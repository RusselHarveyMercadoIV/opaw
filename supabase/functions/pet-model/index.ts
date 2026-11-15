// "Bring to life": turns a pet's cutouts into a rigged, animated 3D model.
//
// Tripo builds new pets: photos to 3D, a riggability check, a four-legged skeleton and a walk
// clip. Meshy is kept as a fallback (PET_MODEL_PROVIDER=meshy, or no TRIPO_API_KEY); its rigging
// only takes two-legged characters, so most dogs stay still there. Each row remembers which
// service it started on and finishes there.
//
// The app calls this function with the signed-in user's token:
//   { "action": "start", "dog_id": "...", "input_paths": ["<uid>/<file>.png", ...],
//     "angles": ["front", "side", "back"], "height_m": 0.5 }
// One to four photos of the same pet. "angles" names each photo; older builds leave it out and
// send front, side, back in that order (or a single "input_path").
//   { "action": "status", "id": "<pet_models row id>" }
//   { "action": "retry", "id": "<pet_models row id>" } picks a job whose motion step failed back up.
// Each status call advances the job one step. The GLBs (rest pose, walk, run) are copied into the
// private pet-models bucket as soon as they're made, because the services' links expire (Tripo's
// after five minutes).
//
// Secrets: TRIPO_API_KEY and/or MESHY_API_KEY. Optional: PET_MODEL_PROVIDER, PET_MODEL_DAILY_LIMIT.
// SUPABASE_URL, SUPABASE_ANON_KEY and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase.

import { createClient } from "jsr:@supabase/supabase-js@2.49.4";

const MESHY = "https://api.meshy.ai/openapi";
const MESHY_KEY = Deno.env.get("MESHY_API_KEY") ?? "";
// The international platform (platform.tripo3d.ai). China accounts use openapi.tripo3d.com.
const TRIPO = Deno.env.get("TRIPO_API_BASE") ?? "https://openapi.tripo3d.ai/v3";
const TRIPO_KEY = Deno.env.get("TRIPO_API_KEY") ?? "";
const PROVIDER = (Deno.env.get("PET_MODEL_PROVIDER") ?? (TRIPO_KEY ? "tripo" : "meshy")).toLowerCase();
const DAILY_LIMIT = Number(Deno.env.get("PET_MODEL_DAILY_LIMIT") ?? "3");

// Tripo models, pinned so a new release can't change results (or prices) underneath us.
const TRIPO_MODEL = "v3.1-20260211";
const TRIPO_RIG_BIPED = "v3.0-20260909";
const TRIPO_RIG_CREATURE = "v2.5-20260210";
// The walk clip for each skeleton Tripo can give back. Four-legged rigs only have a walk so far.
const TRIPO_WALK: Record<string, string> = {
  quadruped: "preset:quadruped:walk",
  biped: "preset:walk",
  hexapod: "preset:hexapod:walk",
  octopod: "preset:octopod:walk",
};

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

type Row = {
  id: string;
  owner_id: string;
  dog_id: string;
  input_path: string;
  provider: "tripo" | "meshy";
  stage: "modeling" | "rigging" | "saving" | "done" | "failed";
  progress: number;
  model_task: string | null;
  check_task: string | null;
  rig_task: string | null;
  anim_task: string | null;
  // While working: the job's notes (height_m, source, inputs, angles, files saved so far).
  // When done: only the clip names mapped to storage paths, which the app downloads.
  outputs: Record<string, string>;
  error: string | null;
};

function reply(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function view(row: Row) {
  return { id: row.id, stage: row.stage, progress: row.progress, outputs: row.stage === "done" ? row.outputs : {}, error: row.error };
}

async function update(row: Row, changes: Partial<Row>): Promise<Row> {
  const { data, error } = await admin.from("pet_models")
    .update({ ...changes, updated_at: new Date().toISOString() })
    .eq("id", row.id).select().single();
  if (error) throw error;
  return data as Row;
}

async function copyToStorage(url: string, path: string) {
  const file = await fetch(url);
  if (!file.ok) throw new Error(`Couldn't download the model (${file.status}).`);
  const { error } = await admin.storage.from("pet-models")
    .upload(path, await file.arrayBuffer(), { contentType: "model/gltf-binary", upsert: true });
  if (error) throw error;
}

function clipPath(row: Row, name: string) {
  return `${row.owner_id}/${row.dog_id}/${row.id}/${name}.glb`;
}

/** Ends the job with the clips saved so far. */
function finish(row: Row, clips: Record<string, string>): Promise<Row> {
  if (!clips.rest) return update(row, { stage: "failed", error: "The 3D model didn't come back." });
  return update(row, { stage: "done", progress: 100, error: null, outputs: clips });
}

async function signedInputs(row: Row): Promise<string[]> {
  const paths: string[] = JSON.parse(row.outputs.inputs ?? "[]");
  const urls: string[] = [];
  for (const path of paths.length ? paths : [row.input_path]) {
    const signed = await admin.storage.from("avatar-inputs").createSignedUrl(path, 3600);
    if (signed.error) throw new Error("Couldn't read the photo.");
    urls.push(signed.data.signedUrl);
  }
  return urls;
}

// ---------------------------------------------------------------------------------------------
// Tripo

async function tripo(method: string, path: string, body?: unknown) {
  const response = await fetch(TRIPO + path, {
    method,
    headers: { Authorization: `Bearer ${TRIPO_KEY}`, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  let json: Record<string, unknown> = {};
  try { json = JSON.parse(text); } catch { /* plain text */ }
  if (!response.ok || (json.code !== undefined && json.code !== 0)) {
    // 429 is a busy queue or rate limit: the status call tries again next time.
    throw new Error(`Tripo ${response.status}: ${json.message ?? text}`);
  }
  return json.data as Record<string, any>;
}

const tripoTask = (id: string) => tripo("GET", `/tasks/${id}`);
const tripoFailed = (task: Record<string, any>) => ["failed", "cancelled", "banned", "expired"].includes(task.status);

/**
 * Starts the photos-to-3D task from the front photo alone. Owners' side and back shots rarely
 * agree with it (the tail up in one and down in another, the side lit differently, the pet
 * facing either way), and Tripo's multiview blends those disagreements into a lumpy body with
 * a second head. One clear front photo gives a cleaner, truer pet, at the same price.
 */
async function tripoModel(row: Row): Promise<string> {
  const urls = await signedInputs(row);
  const angles: string[] = JSON.parse(row.outputs.angles ?? "[]");
  // Older builds send no angles, but always put the front first.
  const front = urls[Math.max(angles.indexOf("front"), 0)];
  const task = await tripo("POST", "/generation/image-to-model", {
    input: front, model: TRIPO_MODEL, texture: true, pbr: false, face_limit: 15000,
  });
  return task.task_id;
}

/** Saves the unrigged model as the rest pose and finishes, for pets Tripo can't rig. */
async function tripoKeepStill(row: Row): Promise<Row> {
  const model = await tripoTask(row.model_task!);
  if (!model.output?.model_url) return update(row, { stage: "failed", error: "Tripo didn't return a model." });
  await copyToStorage(model.output.model_url, clipPath(row, "rest"));
  return finish(row, { rest: clipPath(row, "rest") });
}

async function advanceTripo(row: Row): Promise<Row> {
  // Queued because Tripo was busy when the job started.
  if (row.stage === "modeling" && !row.model_task) {
    return update(row, { model_task: await tripoModel(row), progress: 1 });
  }

  if (row.stage === "modeling") {
    const task = await tripoTask(row.model_task!);
    if (tripoFailed(task)) {
      return update(row, { stage: "failed", error: task.error_message || "Tripo couldn't build a 3D model from these photos." });
    }
    if (task.status !== "success") return update(row, { progress: Math.round((task.progress ?? 0) * 0.6) });
    // The free check says whether the model can take a skeleton, and which kind.
    const check = await tripo("POST", "/animations/rig-check", { input: row.model_task });
    return update(row, { stage: "rigging", progress: 60, check_task: check.task_id, outputs: { ...row.outputs, modeled: "1" } });
  }

  if (row.stage === "rigging" && !row.rig_task) {
    const check = await tripoTask(row.check_task!);
    if (tripoFailed(check) || (check.status === "success" && !check.output?.riggable)) return tripoKeepStill(row);
    if (check.status !== "success") return update(row, { progress: 61 });
    const type = String(check.output.rig_type ?? "quadruped");
    const rig = await tripo("POST", "/animations/rig", {
      input: row.model_task,
      model: type === "biped" ? TRIPO_RIG_BIPED : TRIPO_RIG_CREATURE,
      rig_type: type,
      out_format: "glb",
    });
    return update(row, { progress: 62, rig_task: rig.task_id, outputs: { ...row.outputs, rig_type: type } });
  }

  if (row.stage === "rigging" && !row.anim_task) {
    const rig = await tripoTask(row.rig_task!);
    // No skeleton, but the model itself is fine: keep it still rather than lose it.
    if (tripoFailed(rig)) return tripoKeepStill(row);
    if (rig.status !== "success") return update(row, { progress: 62 + Math.round((rig.progress ?? 0) * 0.18) });
    // The rigged model is the rest pose. Save it now: its link only lasts five minutes.
    await copyToStorage(rig.output.model_url, clipPath(row, "rest"));
    const preset = TRIPO_WALK[row.outputs.rig_type ?? ""];
    if (!preset) return finish(row, { rest: clipPath(row, "rest") });
    const anim = await tripo("POST", "/animations/retarget", {
      input: row.rig_task,
      animation: preset,
      out_format: "glb",
      animate_in_place: true,
    });
    return update(row, { progress: 80, anim_task: anim.task_id, outputs: { ...row.outputs, rest_saved: "1" } });
  }

  if (row.stage === "rigging" && row.anim_task) {
    const anim = await tripoTask(row.anim_task);
    const clips: Record<string, string> = { rest: clipPath(row, "rest") };
    // The walk is a bonus: without it the pet still breathes in its rest pose.
    if (tripoFailed(anim)) return finish(row, clips);
    if (anim.status !== "success") return update(row, { progress: 80 + Math.round((anim.progress ?? 0) * 0.12) });
    await copyToStorage(anim.output.model_url, clipPath(row, "walk"));
    return finish(row, { ...clips, walk: clipPath(row, "walk") });
  }

  return row;
}

// ---------------------------------------------------------------------------------------------
// Meshy (fallback)

async function meshy(method: string, path: string, body?: unknown) {
  const response = await fetch(MESHY + path, {
    method,
    headers: { Authorization: `Bearer ${MESHY_KEY}`, "Content-Type": "application/json" },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  if (!response.ok) {
    let message = text;
    try { message = JSON.parse(text).message ?? text; } catch { /* plain text */ }
    throw new Error(`Meshy ${response.status}: ${message}`);
  }
  return JSON.parse(text);
}

const meshyKind = (row: Row) => row.outputs.source === "multi" ? "multi-image-to-3d" : "image-to-3d";

async function meshyModel(row: Row): Promise<string> {
  const urls = await signedInputs(row);
  // Several angles give Meshy the pet's real shape. A single photo uses smart topology,
  // Meshy's pipeline for rigged characters.
  const task = urls.length > 1
    ? await meshy("POST", "/v1/multi-image-to-3d", {
      image_urls: urls, should_remesh: true, topology: "triangle", target_polycount: 15000,
      should_texture: true, enable_pbr: false,
    })
    : await meshy("POST", "/v1/image-to-3d", {
      image_url: urls[0], model_type: "smart-topology", ai_model: "meshy-t2.5", target_polycount: 15000,
      should_texture: true, enable_pbr: false, name: `opaw-${row.dog_id}`,
    });
  return task.result;
}

/** Saves the unrigged model as the rest pose and finishes, for pets Meshy can't rig. */
async function meshyKeepStill(row: Row, task: { model_urls?: { glb?: string } }): Promise<Row> {
  const url = task.model_urls?.glb;
  if (!url) return update(row, { stage: "failed", error: "Meshy didn't return a model." });
  row = await update(row, { stage: "saving", progress: 92 });
  await copyToStorage(url, clipPath(row, "rest"));
  return finish(row, { rest: clipPath(row, "rest") });
}

async function advanceMeshy(row: Row): Promise<Row> {
  if (row.stage === "modeling" && !row.model_task) {
    return update(row, { model_task: await meshyModel(row), progress: 1 });
  }
  if (row.stage === "modeling") {
    const task = await meshy("GET", `/v1/${meshyKind(row)}/${row.model_task}`);
    if (task.status === "FAILED" || task.status === "CANCELED") {
      return update(row, { stage: "failed", error: task.task_error?.message || "Meshy couldn't build a 3D model from these photos." });
    }
    if (task.status !== "SUCCEEDED") return update(row, { progress: Math.round((task.progress ?? 0) * 0.6) });
    let rig;
    try {
      rig = await meshy("POST", "/v1/rigging", { input_task_id: row.model_task, height_meters: Number(row.outputs.height_m ?? 0.5) });
    } catch (error) {
      // Meshy's rigging only takes two-legged, humanoid characters, so most pets fail its pose
      // estimation (422). Keep the model it already built: the app breathes the still pose.
      if (/^Meshy 422/.test(String((error as Error).message))) return meshyKeepStill(row, task);
      throw error;
    }
    return update(row, { stage: "rigging", progress: 60, rig_task: rig.result, outputs: { ...row.outputs, modeled: "1" } });
  }
  // A still model whose copy was cut off: fetch the model again and finish saving it.
  if (row.stage === "saving" && !row.rig_task) {
    return meshyKeepStill(row, await meshy("GET", `/v1/${meshyKind(row)}/${row.model_task}`));
  }
  // "saving" is retried too, in case an earlier call was cut off while copying files.
  if ((row.stage === "rigging" || row.stage === "saving") && row.rig_task) {
    const task = await meshy("GET", `/v1/rigging/${row.rig_task}`);
    if (task.status === "FAILED" || task.status === "CANCELED") {
      const model = await meshy("GET", `/v1/${meshyKind(row)}/${row.model_task}`).catch(() => null);
      if (model?.model_urls?.glb) return meshyKeepStill(row, model);
      return update(row, { stage: "failed", error: task.task_error?.message || "Meshy couldn't add a skeleton to this model." });
    }
    if (task.status !== "SUCCEEDED") return update(row, { progress: 60 + Math.round((task.progress ?? 0) * 0.3) });
    row = await update(row, { stage: "saving", progress: 92 });
    const result = task.result ?? {};
    const files: Record<string, string | undefined> = {
      rest: result.rigged_character_glb_url,
      walk: result.basic_animations?.walking_glb_url,
      run: result.basic_animations?.running_glb_url,
    };
    const clips: Record<string, string> = {};
    for (const [name, url] of Object.entries(files)) {
      if (!url) continue;
      await copyToStorage(url, clipPath(row, name));
      clips[name] = clipPath(row, name);
    }
    return finish(row, clips);
  }
  return row;
}

// ---------------------------------------------------------------------------------------------

const advance = (row: Row) => row.provider === "tripo" ? advanceTripo(row) : advanceMeshy(row);

/** A request the service refuses (no credits, bad input) won't succeed on retry. Busy (429) will. */
const isFinal = (message: string) => /^(Meshy|Tripo) 4(?!29)/.test(message);

async function start(user: string, body: Record<string, unknown>) {
  const dogId = String(body.dog_id ?? "");
  const given: unknown[] = Array.isArray(body.input_paths) ? body.input_paths : [body.input_path];
  const paths = given.map((path) => String(path ?? "")).filter(Boolean).slice(0, 4);
  const angles = (Array.isArray(body.angles) ? body.angles : []).map((angle) => String(angle).toLowerCase()).slice(0, paths.length);
  const height = Math.min(Math.max(Number(body.height_m ?? 0.5) || 0.5, 0.15), 1.2);
  if (!dogId || !paths.length || paths.some((path) => !path.startsWith(`${user}/`))) {
    return reply({ error: "Missing dog or photo." }, 400);
  }

  // Each generation spends credits, so cap how many one account can start per day.
  const since = new Date(Date.now() - 86_400_000).toISOString();
  const { count } = await admin.from("pet_models").select("id", { count: "exact", head: true })
    .eq("owner_id", user).gte("created_at", since);
  if ((count ?? 0) >= DAILY_LIMIT) return reply({ error: "You've brought enough pets to life for today. Try again tomorrow." }, 429);

  // The row comes first, so a job that can't start right away (the service's queue is full)
  // waits its turn: each status call tries to start it again.
  const provider = PROVIDER === "tripo" && TRIPO_KEY ? "tripo" : "meshy";
  const { data, error } = await admin.from("pet_models").insert({
    owner_id: user, dog_id: dogId, input_path: paths[0], provider, stage: "modeling",
    outputs: {
      height_m: String(height),
      source: paths.length > 1 ? "multi" : "single",
      inputs: JSON.stringify(paths),
      angles: JSON.stringify(angles),
    },
  }).select().single();
  if (error) throw error;
  let row = data as Row;
  try {
    row = await advance(row);
  } catch (error) {
    const message = String((error as Error).message ?? error);
    if (isFinal(message)) {
      // Not the user's fault (out of credits, a bad request): don't count it against their day.
      await admin.from("pet_models").delete().eq("id", row.id);
      throw error;
    }
  }
  return reply(view(row));
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return reply({ error: "Use POST." }, 405);
  if (!TRIPO_KEY && !MESHY_KEY) return reply({ error: "Bring to life isn't set up on the server yet (TRIPO_API_KEY)." }, 503);

  const token = (request.headers.get("Authorization") ?? "").replace(/^Bearer /, "");
  const { data: auth } = await admin.auth.getUser(token);
  const user = auth.user?.id;
  if (!user) return reply({ error: "Sign in first." }, 401);

  try {
    const body = await request.json();
    if (body.action === "start") return await start(user, body);
    if (body.action === "retry") {
      // Trying again after only the motion step failed: the 3D model is already built, so pick
      // that job back up instead of spending another generation. The app starts over if this says no.
      const { data } = await admin.from("pet_models").select().eq("id", String(body.id ?? "")).eq("owner_id", user).maybeSingle();
      const row = data as Row | null;
      if (!row || row.stage !== "failed" || !row.model_task || row.outputs.modeled !== "1") {
        return reply({ error: "Start over." }, 409);
      }
      // Back to the end of modeling: the next status call sees the finished model and rigs it again.
      return reply(view(await update(row, { stage: "modeling", error: null, check_task: null, rig_task: null, anim_task: null })));
    }
    if (body.action === "status") {
      const { data } = await admin.from("pet_models").select().eq("id", String(body.id ?? "")).eq("owner_id", user).maybeSingle();
      if (!data) return reply({ error: "That job doesn't exist." }, 404);
      let row = data as Row;
      try {
        row = await advance(row);
      } catch (error) {
        const message = String((error as Error).message ?? error);
        // Anything that isn't a final refusal (a timeout, a busy queue, a network blip) is tried
        // again on the next status call.
        if (isFinal(message)) {
          row = await update(row, { stage: "failed", error: message });
        } else {
          return reply({ ...view(row), error: null, retry: message });
        }
      }
      return reply(view(row));
    }
    return reply({ error: "Unknown action." }, 400);
  } catch (error) {
    return reply({ error: String((error as Error).message ?? error) }, 500);
  }
});
