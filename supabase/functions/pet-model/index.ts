// "Bring to life": turns a pet's photos into a rigged 3D model.
//
// Tripo builds new pets in steps, each paid only once the one before it is good:
//   cleaning   a 2D reference sheet of the pet drawn from all the photos: standing, head straight,
//              even light, front and left side by side (about 5 credits). Owners' photos never
//              agree (sitting, looking at the camera, different light), and a 3D model built from
//              them copies that: two faces, washed-out fur, a seated pet that can't be rigged.
//   reviewing  waits for the owner to say the sheet looks like their pet. The app splits the
//              sheet into front and side and uploads them ("approve"), or asks for another
//              ("redraw"). A bad result costs 5 credits here instead of 60 later.
//   modeling   the 3D model from the two clean views (30), then a free riggability check
//   rigging    a four-legged skeleton (25). The app animates walk, run and idle itself, so there's
//              no per-pet charge for motion clips.
// Meshy is kept as a fallback (PET_MODEL_PROVIDER=meshy, or no TRIPO_API_KEY). Each row remembers
// which service it started on and finishes there.
//
// The app calls this function with the signed-in user's token:
//   { "action": "start", "dog_id": "...", "input_paths": ["<uid>/<file>.png", ...],
//     "angles": ["front", "left", "right", "back"], "height_m": 0.5, "review": true }
// Builds without "review" skip the sheet and model the photos directly.
//   { "action": "status", "id": "<pet_models row id>" }
//   { "action": "approve", "id": "...", "views": { "front": "<uid>/...", "left": "<uid>/..." } }
//   { "action": "redraw", "id": "..." } draws the sheet again (up to MAX_DRAWS in all).
//   { "action": "retry", "id": "..." } picks a job whose skeleton step failed back up.
// Each status call advances the job one step. Files are copied into the private pet-models
// bucket as soon as they're made, because Tripo's links expire after five minutes.
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
// No daily cap unless PET_MODEL_DAILY_LIMIT is set to a number above 0.
const DAILY_LIMIT = Number(Deno.env.get("PET_MODEL_DAILY_LIMIT") ?? "0");

// Tripo models, pinned so a new release can't change results (or prices) underneath us.
const TRIPO_MODEL = "v3.1-20260211";
const TRIPO_RIG_BIPED = "v3.0-20260909";
const TRIPO_RIG_CREATURE = "v2.5-20260210";
// The reference sheet: Tripo's image model, at its smallest allowed two-to-one size.
const TRIPO_SHEET_MODEL = "seedream_v5";
const TRIPO_SHEET_SIZE = "3072x1536";

// The pet as one picture in the app's flat cartoon style (simple colour blocks, one shadow tone,
// a clean outline, but unmistakably this pet), which the owner approves and which then becomes the
// 3D model. A clean, evenly lit, standing, three-quarter view in simple shapes models far better
// than photos, and keeps the face's markings. The onboarding sample pet (Android welcome_pet) is
// drawn with this same prompt, so keep the two in step. Drawn by Tripo's GPT-image model (10 credits).
const TRIPO_PICTURE_MODEL = "chat_image_2";
const PICTURE_PROMPT =
  "Redraw this exact pet (the same individual animal shown in all reference photos) as a cute, simple game character. " +
  "Style: smooth simplified shapes, flat colour blocks, one soft shadow tone, no fur texture or individual hair strands, " +
  "a clean even dark-brown outline, small simple dot eyes with one highlight, gently rounded cute proportions with a " +
  "slightly bigger head. Keep its identity: the same coat colours and where the markings sit, the same face pattern, " +
  "eye colour, nose colour, ear shape and tail shape. Full body, standing on all four legs, three-quarter front view, " +
  "all four legs clearly visible and separated, happy open-mouth smile, plain pure white background, no shadow, " +
  "no ground, no collar, no text.";
const MAX_DRAWS = 3;
const SHEET_PROMPT =
  "Character reference sheet of the exact pet in the reference images (the same animal photographed from different sides). " +
  "Two views side by side on one plain pure white background, the same animal at the same scale. " +
  "LEFT HALF: the animal seen straight from the front, standing on all four legs, head level and facing the camera. " +
  "RIGHT HALF: the animal seen exactly from its left side in profile, standing on all four legs, nose pointing to the left edge, " +
  "head facing straight ahead in line with the body (not looking at the camera), tail relaxed and fully visible. " +
  "Keep the pet's true body length, leg length, build, coat colours and markings exactly as in the photos. " +
  "Even soft studio light, no shadows, no text, no ground, the whole animal in frame in both views.";

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

type Row = {
  id: string;
  owner_id: string;
  dog_id: string;
  input_path: string;
  provider: "tripo" | "meshy";
  stage: "cleaning" | "reviewing" | "modeling" | "rigging" | "saving" | "done" | "failed";
  progress: number;
  sheet_task: string | null;
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
  return {
    id: row.id, stage: row.stage, progress: row.progress, outputs: row.stage === "done" ? row.outputs : {}, error: row.error,
    // The reference sheet to show the owner, a path in the pet-models bucket.
    sheet: row.stage === "reviewing" ? row.outputs.sheet ?? null : null,
    draws_left: Math.max(0, MAX_DRAWS - Number(row.outputs.draws ?? "0")),
  };
}

async function update(row: Row, changes: Partial<Row>): Promise<Row> {
  const { data, error } = await admin.from("pet_models")
    .update({ ...changes, updated_at: new Date().toISOString() })
    .eq("id", row.id).select().single();
  if (error) throw error;
  return data as Row;
}

async function copyToStorage(url: string, path: string, contentType = "model/gltf-binary") {
  const file = await fetch(url);
  if (!file.ok) throw new Error(`Couldn't download the model (${file.status}).`);
  const { error } = await admin.storage.from("pet-models")
    .upload(path, await file.arrayBuffer(), { contentType, upsert: true });
  if (error) throw error;
}

function clipPath(row: Row, name: string, extension = "glb") {
  return `${row.owner_id}/${row.dog_id}/${row.id}/${name}.${extension}`;
}

async function signed(bucket: string, path: string): Promise<string> {
  const result = await admin.storage.from(bucket).createSignedUrl(path, 3600);
  if (result.error) throw new Error("Couldn't read the photo.");
  return result.data.signedUrl;
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
 * Starts the photos-to-3D task. With a front and a labelled left or right side, Tripo's multiview
 * gets the pet's real length and leg height from the sides. Tripo's "left" view is the pet's own
 * left side (nose on the left of the photo), which is how the app asks for it.
 *
 * Older builds send one "side" photo without saying which side. Sent as the wrong one, it put a
 * second head where the tail should be, so those jobs use the front photo alone.
 */
async function tripoModel(row: Row): Promise<string> {
  // Detailed texture and more faces (+10 credits) keep the eyes, nose and fur markings sharp.
  const common0 = { model: TRIPO_MODEL, texture: true, pbr: false, face_limit: 40000, texture_quality: "detailed" };
  // The owner approved the picture: it becomes the model.
  if (row.outputs.picture) {
    const task = await tripo("POST", "/generation/image-to-model", { input: await signed("pet-models", row.outputs.picture), ...common0 });
    return task.task_id;
  }
  // The owner approved a reference sheet: model its clean front and left views.
  if (row.outputs.views) {
    const views: Record<string, string> = JSON.parse(row.outputs.views);
    const inputs = [];
    for (const view of ["front", "left"]) if (views[view]) inputs.push({ [view]: await signed("avatar-inputs", views[view]) });
    const task = await tripo("POST", "/generation/multiview-to-model", { inputs, ...common0 });
    return task.task_id;
  }
  const urls = await signedInputs(row);
  const angles: string[] = JSON.parse(row.outputs.angles ?? "[]");
  const common = { model: TRIPO_MODEL, texture: true, pbr: false, face_limit: 15000 };
  const views = ["front", "left", "right", "back"]
    .filter((view) => angles.includes(view))
    .map((view) => ({ [view]: urls[angles.indexOf(view)] }));
  const hasSide = angles.includes("left") || angles.includes("right");
  const task = angles.includes("front") && hasSide
    ? await tripo("POST", "/generation/multiview-to-model", { inputs: views, ...common })
    // No angles (older builds) always put the front first.
    : await tripo("POST", "/generation/image-to-model", { input: urls[Math.max(angles.indexOf("front"), 0)], ...common });
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
  if (row.stage === "cleaning" && !row.sheet_task) {
    const [first, ...rest] = await signedInputs(row);
    const sheet = await tripo("POST", "/generation/image-to-image", {
      model: TRIPO_PICTURE_MODEL, input: first, inputs: rest, prompt: PICTURE_PROMPT, size: "1024x1024", output_format: "jpeg",
    });
    return update(row, {
      sheet_task: sheet.task_id, progress: 2,
      outputs: { ...row.outputs, style: "picture", draws: String(Number(row.outputs.draws ?? "0") + 1) },
    });
  }

  if (row.stage === "cleaning") {
    const task = await tripoTask(row.sheet_task!);
    if (tripoFailed(task)) return update(row, { stage: "failed", error: task.error_message || "Tripo couldn't draw this pet." });
    if (task.status !== "success") return update(row, { progress: 2 + Math.round((task.progress ?? 0) * 0.08) });
    // Its link lasts five minutes: keep a copy for the owner to look at.
    const path = clipPath(row, `sheet-${row.outputs.draws ?? "1"}`, "jpg");
    await copyToStorage(task.output.generated_image_url, path, "image/jpeg");
    return update(row, { stage: "reviewing", progress: 10, outputs: { ...row.outputs, sheet: path } });
  }

  // Waiting for the owner.
  if (row.stage === "reviewing") return row;

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
    // The rigged model is the rest pose, and the app animates it. Save it now: its link only
    // lasts five minutes.
    await copyToStorage(rig.output.model_url, clipPath(row, "rest"));
    return finish(row, { rest: clipPath(row, "rest") });
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

  // Each generation spends credits; a daily cap per account can be turned on with PET_MODEL_DAILY_LIMIT.
  if (DAILY_LIMIT > 0) {
    const since = new Date(Date.now() - 86_400_000).toISOString();
    const { count } = await admin.from("pet_models").select("id", { count: "exact", head: true })
      .eq("owner_id", user).gte("created_at", since);
    if ((count ?? 0) >= DAILY_LIMIT) return reply({ error: "You've brought enough pets to life for today. Try again tomorrow." }, 429);
  }

  // The row comes first, so a job that can't start right away (the service's queue is full)
  // waits its turn: each status call tries to start it again.
  const provider = PROVIDER === "tripo" && TRIPO_KEY ? "tripo" : "meshy";
  // Builds that can show the reference sheet start by drawing one; older ones model the photos.
  const stage = provider === "tripo" && body.review === true ? "cleaning" : "modeling";
  const { data, error } = await admin.from("pet_models").insert({
    owner_id: user, dog_id: dogId, input_path: paths[0], provider, stage,
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
    if (body.action === "approve" || body.action === "redraw") {
      const { data } = await admin.from("pet_models").select().eq("id", String(body.id ?? "")).eq("owner_id", user).maybeSingle();
      let row = data as Row | null;
      if (!row || row.stage !== "reviewing") return reply({ error: "There's nothing to review." }, 409);
      if (body.action === "redraw") {
        if (Number(row.outputs.draws ?? "0") >= MAX_DRAWS) return reply({ error: "That's as many tries as we can do. Try new photos." }, 409);
        row = await update(row, { stage: "cleaning", sheet_task: null, progress: 1 });
      } else {
        // One picture: it becomes the model as it is.
        if (row.outputs.style === "picture") {
          row = await update(row, { stage: "modeling", model_task: null, progress: 12, outputs: { ...row.outputs, picture: row.outputs.sheet } });
        }
        const given = (body.views ?? {}) as Record<string, unknown>;
        const views: Record<string, string> = {};
        for (const view of ["front", "left"]) {
          const path = String(given[view] ?? "");
          if (path.startsWith(`${user}/`)) views[view] = path;
        }
        if (row.stage === "reviewing") {
          if (!views.front || !views.left) return reply({ error: "Missing the front or side view." }, 400);
          row = await update(row, { stage: "modeling", model_task: null, progress: 12, outputs: { ...row.outputs, views: JSON.stringify(views) } });
        }
      }
      try {
        row = await advance(row);
      } catch (error) {
        const message = String((error as Error).message ?? error);
        if (isFinal(message)) row = await update(row, { stage: "failed", error: message });
      }
      return reply(view(row));
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
