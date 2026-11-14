# Setting up Opaw

## 1. Run the app

1. Install Xcode (latest) and XcodeGen: `brew install xcodegen`.
2. From the repository root: `xcodegen generate`, then `open Opaw.xcodeproj`.
3. Choose an iPhone simulator and press Run.

Run `xcodegen generate` again whenever you pull changes that add or remove files.

### Running on your own iPhone

1. Copy `Config/Secrets.example.xcconfig` to `Config/Secrets.xcconfig`.
2. Set `DEVELOPMENT_TEAM` to your Apple Developer Team ID (Xcode → Settings → Accounts).
3. If the bundle id `app.opaw.Opaw` or the App Group `group.app.opaw.shared` is taken, change them in
   `project.yml` (and `OpawDefaults.appGroupID` in `Shared/OpawStore.swift`), then regenerate.

The photo cutout uses the Neural Engine, so it works best on a real device. In the simulator the app
falls back to the full photo.

## 2. Turn on the Pack (social) with Supabase

1. Create a free project at [supabase.com](https://supabase.com).
2. In **Authentication → Sign In / Providers**, enable **Anonymous sign-ins**.
3. Apply the schema. Either install the Supabase CLI and run `supabase link` then `supabase db push`,
   or paste `supabase/migrations/20261102000000_init.sql` into the SQL editor and run it.
4. In **Project Settings → API**, copy the project URL and the `anon` public key into
   `Config/Secrets.xcconfig`:

   ```
   SUPABASE_HOST = abcdefgh.supabase.co
   SUPABASE_ANON_KEY = eyJ...
   ```

   Use the host only, without `https://`, because `//` starts a comment in `.xcconfig` files.
5. Regenerate the project and run. The Pack tab now asks you to join.

### Moderation

Reports land in the `reports` table. Review them in the Supabase table editor, remove offending posts
or dogs, and set `status` to `actioned` or `dismissed`. The App Store requires that you act on reports
within 24 hours, which the in-app rules promise.

## 3. Generate realistic motion clips (optional)

The app always shows the on-device "living photo". For realistic clips, run the avatar worker on any
machine. The app uploads the cutout and queues a job, and the worker picks it up.

```bash
cd avatar-worker
cp .env.example .env    # fill in SUPABASE_URL and the SERVICE ROLE key
```

**Try it without a GPU** (simple animated clips, good for testing the whole flow):

```bash
pip install -r requirements.txt
python -m opaw_worker.worker --backend procedural run
```

**Realistic clips with Wan 2.2** (needs an NVIDIA GPU with 8 GB or more; 24 GB recommended):

```bash
pip install -r requirements-gpu.txt
python -m opaw_worker.worker --backend wan run
```

The first run downloads the Wan 2.2 TI2V-5B model (Apache 2.0) from Hugging Face. Each dog takes
roughly 8 clips; expect a few minutes per clip on a 24 GB card.

To render one photo locally without Supabase:

```bash
python -m opaw_worker.worker --backend procedural render my-dog.png --out clips/
```

> The Wan backend has not been run end to end yet; the first GPU run may need small adjustments to
> resolution or memory settings. The procedural backend and the clip format are covered by tests.

Keep the service role key on the worker machine only. Never put it in the app.

## 4. Bring pets to life in 3D with Meshy (optional, Android for now)

"Bring to life" turns a pet's cutout into a textured 3D model with a four-legged skeleton, using
[Meshy](https://www.meshy.ai). The Android app shows it in 3D on Home and in the Avatar studio.
It walks when the pet is happy, runs when restless or proud, and rests (breathing) otherwise.
It's a Premium feature.

It runs through the `pet-model` Supabase Edge Function. The function holds your Meshy API key,
so the key never ships in the app.

1. Apply `supabase/migrations/20261114000000_pet_models.sql` (`supabase db push`, or paste it into
   the SQL editor). It adds the `pet_models` table and the private `pet-models` bucket.
2. Create an API key at [meshy.ai](https://www.meshy.ai/api). It starts with `msy_`.
3. Deploy the function and give it the key:

   ```bash
   supabase secrets set MESHY_API_KEY=msy_...
   supabase functions deploy pet-model
   ```

   Optional: `supabase secrets set PET_MODEL_DAILY_LIMIT=3` caps how many models one account can
   start per day (3 by default), since each one spends Meshy credits.

Each pet takes two Meshy jobs: image-to-3D (Smart Topology, `meshy-t2.5`), then rigging with
`animation_type: quadruped`. Meshy's animation library only has two-legged animations, so a pet
gets the walk and run clips that come with the rig. The app checks the photo on the phone first:
one whole pet, in focus, close enough, and well lit. If a photo isn't clear enough, it asks for a
better one instead of spending credits.

> Premium is checked in the app (Google Play). The server only enforces the daily limit. To
> enforce Premium on the server too, verify the purchase with the Play Developer API in the function.
