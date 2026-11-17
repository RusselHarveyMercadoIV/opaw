# Opaw avatar worker

Turns a dog's cutout into short looping motion clips for the app's avatar.

- **Backends:** `wan` uses the open-source Wan 2.2 TI2V-5B model (Apache 2.0) for realistic motion and
  `rembg` (MIT) to remove the background from each frame. `procedural` animates the cutout with simple
  transforms and needs no GPU.
- **Output:** "stacked alpha" H.264 MP4s: colour on top, transparency mask below. The app recombines
  them in `StackedAlphaVideoView`.
- **Queue:** jobs come from the `avatar_jobs` table in Supabase. The worker claims one atomically with
  the `claim_avatar_job()` function, uploads clips to the `avatar-clips` bucket and marks the job done.

See [../docs/SETUP.md](../docs/SETUP.md#3-generate-realistic-motion-clips-optional) for how to run it.

```bash
pip install -r requirements-dev.txt
python -m pytest tests
```
