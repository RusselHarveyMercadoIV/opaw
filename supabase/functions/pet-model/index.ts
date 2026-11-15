// "Bring to life": turns a pet's cutouts into a rigged, animated 3D model with Meshy.
//
// The app calls this function with the signed-in user's token:
//   { "action": "start", "dog_id": "...", "input_paths": ["<uid>/<file>.png", ...], "height_m": 0.5 }
// One to four photos of the same pet from different angles (front, side, back). Older builds send
// a single "input_path" instead, which still works.
//   { "action": "status", "id": "<pet_models row id>" }
// Each status call advances the job one step: photos to 3D, then quadruped rigging, then copying
// the GLBs (rest pose, walk and run) into the private pet-models bucket, because Meshy's links expire.
//
// Secrets: MESHY_API_KEY (supabase secrets set MESHY_API_KEY=msy_...). Optional: PET_MODEL_DAILY_LIMIT.
// SUPABASE_URL, SUPABASE_ANON_KEY and SUPABASE_SERVICE_ROLE_KEY are provided by Supabase.

import { createClient } from "jsr:@supabase/supabase-js@2.49.4";

const MESHY = "https://api.meshy.ai/openapi";
const MESHY_KEY = Deno.env.get("MESHY_API_KEY") ?? "";
const DAILY_LIMIT = Number(Deno.env.get("PET_MODEL_DAILY_LIMIT") ?? "3");

const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

type Row = {
  id: string;
  owner_id: string;
  dog_id: string;
  input_path: string;
  stage: "modeling" | "rigging" | "saving" | "done" | "failed";
  progress: number;
  meshy_model_task: string | null;
  meshy_rig_task: string | null;
  outputs: Record<string, string>;
  error: string | null;
};

function reply(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

function view(row: Row) {
  return { id: row.id, stage: row.stage, progress: row.progress, outputs: row.outputs, error: row.error };
}

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

async function start(user: string, body: Record<string, unknown>) {
  const dogId = String(body.dog_id ?? "");
  const given: unknown[] = Array.isArray(body.input_paths) ? body.input_paths : [body.input_path];
  const paths = given.map((path) => String(path ?? "")).filter(Boolean).slice(0, 4);
  const inputPath = paths[0] ?? "";
  const height = Math.min(Math.max(Number(body.height_m ?? 0.5) || 0.5, 0.15), 1.2);
  if (!dogId || !paths.length || paths.some((path) => !path.startsWith(`${user}/`))) {
    return reply({ error: "Missing dog or photo." }, 400);
  }

  // Each generation spends Meshy credits, so cap how many one account can start per day.
  const since = new Date(Date.now() - 86_400_000).toISOString();
  const { count } = await admin.from("pet_models").select("id", { count: "exact", head: true })
    .eq("owner_id", user).gte("created_at", since);
  if ((count ?? 0) >= DAILY_LIMIT) return reply({ error: "You've brought enough pets to life for today. Try again tomorrow." }, 429);

  const urls: string[] = [];
  for (const path of paths) {
    const signed = await admin.storage.from("avatar-inputs").createSignedUrl(path, 3600);
    if (signed.error) return reply({ error: "Couldn't read the photo." }, 400);
    urls.push(signed.data.signedUrl);
  }

  // Several angles give Meshy the pet's real shape, so the walk and run clips move a body it has
  // seen from every side. A single photo uses smart topology, Meshy's pipeline for rigged characters.
  const source = urls.length > 1 ? "multi" : "single";
  const task = source === "multi"
    ? await meshy("POST", "/v1/multi-image-to-3d", {
      image_urls: urls,
      should_remesh: true,
      topology: "triangle",
      target_polycount: 15000,
      should_texture: true,
      enable_pbr: false,
    })
    : await meshy("POST", "/v1/image-to-3d", {
      image_url: urls[0],
      model_type: "smart-topology",
      ai_model: "meshy-t2.5",
      target_polycount: 15000,
      should_texture: true,
      enable_pbr: false,
      name: `opaw-${dogId}`,
    });
  const { data, error } = await admin.from("pet_models").insert({
    owner_id: user, dog_id: dogId, input_path: inputPath, stage: "modeling", meshy_model_task: task.result,
    // Remembered for the next steps: the pet's height for rigging, and which Meshy task to ask about.
    outputs: { height_m: String(height), source },
  }).select().single();
  if (error) throw error;
  return reply(view(data as Row));
}

async function advance(row: Row): Promise<Row> {
  if (row.stage === "modeling" && row.meshy_model_task) {
    const kind = row.outputs.source === "multi" ? "multi-image-to-3d" : "image-to-3d";
    const task = await meshy("GET", `/v1/${kind}/${row.meshy_model_task}`);
    if (task.status === "FAILED" || task.status === "CANCELED") {
      return update(row, { stage: "failed", error: task.task_error?.message || "Meshy couldn't build a 3D model from these photos." });
    }
    if (task.status !== "SUCCEEDED") return update(row, { progress: Math.round((task.progress ?? 0) * 0.6) });
    const rig = await meshy("POST", "/v1/rigging", {
      input_task_id: row.meshy_model_task,
      animation_type: "quadruped",
      height_meters: Number(row.outputs.height_m ?? 0.5),
    });
    return update(row, { stage: "rigging", progress: 60, meshy_rig_task: rig.result });
  }
  // "saving" is retried too, in case an earlier call was cut off while copying files.
  if ((row.stage === "rigging" || row.stage === "saving") && row.meshy_rig_task) {
    const task = await meshy("GET", `/v1/rigging/${row.meshy_rig_task}`);
    if (task.status === "FAILED" || task.status === "CANCELED") {
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
    const outputs: Record<string, string> = {};
    for (const [name, url] of Object.entries(files)) {
      if (!url) continue;
      const path = `${row.owner_id}/${row.dog_id}/${row.id}/${name}.glb`;
      await copyToStorage(url, path);
      outputs[name] = path;
    }
    if (!outputs.rest) return update(row, { stage: "failed", error: "Meshy didn't return a model." });
    return update(row, { stage: "done", progress: 100, outputs });
  }
  return row;
}

Deno.serve(async (request) => {
  if (request.method !== "POST") return reply({ error: "Use POST." }, 405);
  if (!MESHY_KEY) return reply({ error: "Bring to life isn't set up on the server yet (MESHY_API_KEY)." }, 503);

  const token = (request.headers.get("Authorization") ?? "").replace(/^Bearer /, "");
  const { data: auth } = await admin.auth.getUser(token);
  const user = auth.user?.id;
  if (!user) return reply({ error: "Sign in first." }, 401);

  try {
    const body = await request.json();
    if (body.action === "start") return await start(user, body);
    if (body.action === "status") {
      const { data } = await admin.from("pet_models").select().eq("id", String(body.id ?? "")).eq("owner_id", user).maybeSingle();
      if (!data) return reply({ error: "That job doesn't exist." }, 404);
      let row = data as Row;
      try {
        row = await advance(row);
      } catch (error) {
        const message = String((error as Error).message ?? error);
        // A request Meshy refuses (no credits, bad input) won't succeed on retry; anything else
        // (a timeout, a rate limit, a network blip) is tried again on the next status call.
        if (/^Meshy 4(?!29)/.test(message)) {
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
