"""Opaw avatar worker.

Polls Supabase for queued avatar jobs, generates each motion clip, uploads the stacked-alpha MP4s
and marks the job done. Run it on any machine with the service role key:

    python -m opaw_worker.worker --backend wan          # realistic, needs a GPU
    python -m opaw_worker.worker --backend procedural   # quick test clips, no GPU
    python -m opaw_worker.worker --once                 # process one job and exit

To try a single photo without Supabase:

    python -m opaw_worker.worker render dog.png --out clips/ --backend procedural
"""

from __future__ import annotations

import argparse
import io
import logging
import os
import sys
import tempfile
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from PIL import Image

from .generators import Generator, make_generator
from .prompts import CLIP_NAMES
from .stacked_alpha import encode_stacked_mp4

log = logging.getLogger("opaw_worker")


def render_clips(generator: Generator, cutout: Image.Image, out_dir: Path, clips: list[str], breed: str = "") -> dict[str, Path]:
    """Generates every clip for one dog into `out_dir`. Returns clip name -> file."""
    results: dict[str, Path] = {}
    for clip in clips:
        if clip not in CLIP_NAMES:
            log.warning("Skipping unknown clip %s", clip)
            continue
        started = time.time()
        frames = generator.generate(cutout, clip, breed)
        path = encode_stacked_mp4(frames, out_dir / f"{clip}.mp4", fps=generator.fps)
        log.info("Rendered %s (%d frames) in %.1fs", clip, len(frames), time.time() - started)
        results[clip] = path
    return results


class SupabaseQueue:
    """Thin wrapper over the avatar_jobs table and storage buckets."""

    def __init__(self, url: str, service_key: str):
        from supabase import create_client

        self.client = create_client(url, service_key)

    def claim(self) -> dict[str, Any] | None:
        rows = self.client.rpc("claim_avatar_job").execute().data or []
        return rows[0] if rows else None

    def download_input(self, path: str) -> bytes:
        return self.client.storage.from_("avatar-inputs").download(path)

    def upload_clip(self, path: str, file: Path) -> None:
        self.client.storage.from_("avatar-clips").upload(
            path,
            file.read_bytes(),
            {"content-type": "video/mp4", "upsert": "true"},
        )

    def finish(self, job_id: str, outputs: dict[str, str]) -> None:
        self.client.table("avatar_jobs").update(
            {"status": "done", "outputs": outputs, "updated_at": datetime.now(timezone.utc).isoformat()}
        ).eq("id", job_id).execute()

    def fail(self, job_id: str, error: str) -> None:
        self.client.table("avatar_jobs").update(
            {"status": "failed", "error": error[:500], "updated_at": datetime.now(timezone.utc).isoformat()}
        ).eq("id", job_id).execute()


def process_job(queue: SupabaseQueue, generator: Generator, job: dict[str, Any]) -> None:
    job_id = job["id"]
    owner = job["owner_id"]
    dog = job["dog_id"]
    log.info("Processing job %s for dog %s", job_id, dog)
    try:
        cutout = Image.open(io.BytesIO(queue.download_input(job["input_path"]))).convert("RGBA")
        with tempfile.TemporaryDirectory() as tmp:
            files = render_clips(generator, cutout, Path(tmp), list(job.get("clips") or CLIP_NAMES), job.get("breed") or "")
            outputs: dict[str, str] = {}
            stamp = int(time.time())
            for clip, file in files.items():
                # Owner id first, to match the storage policies.
                path = f"{owner}/{dog}/{stamp}-{clip}.mp4"
                queue.upload_clip(path, file)
                outputs[clip] = path
        queue.finish(job_id, outputs)
        log.info("Job %s done", job_id)
    except Exception as error:  # noqa: BLE001 - report any failure back to the app
        log.exception("Job %s failed", job_id)
        queue.fail(job_id, str(error))


def run_queue(backend: str, once: bool, poll_seconds: float) -> None:
    from dotenv import load_dotenv

    load_dotenv()
    url = os.environ.get("SUPABASE_URL")
    key = os.environ.get("SUPABASE_SERVICE_ROLE_KEY")
    if not url or not key:
        sys.exit("Set SUPABASE_URL and SUPABASE_SERVICE_ROLE_KEY (see .env.example).")
    queue = SupabaseQueue(url, key)
    generator = make_generator(backend)
    log.info("Worker started with the %s backend", backend)
    while True:
        job = queue.claim()
        if job:
            process_job(queue, generator, job)
            if once:
                return
        elif once:
            log.info("No queued jobs")
            return
        else:
            time.sleep(poll_seconds)


def main(argv: list[str] | None = None) -> None:
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    parser = argparse.ArgumentParser(description="Opaw avatar clip worker")
    parser.add_argument("--backend", default=os.environ.get("OPAW_BACKEND", "procedural"), choices=["wan", "procedural"])
    sub = parser.add_subparsers(dest="command")

    run = sub.add_parser("run", help="Process queued jobs from Supabase (default)")
    run.add_argument("--once", action="store_true")
    run.add_argument("--poll", type=float, default=10.0)

    render = sub.add_parser("render", help="Render clips for a local image")
    render.add_argument("image", type=Path, help="A cutout PNG with transparency, or any photo")
    render.add_argument("--out", type=Path, default=Path("clips"))
    render.add_argument("--breed", default="")
    render.add_argument("--clips", nargs="*", default=list(CLIP_NAMES))

    parser.add_argument("--once", action="store_true", help=argparse.SUPPRESS)
    args = parser.parse_args(argv)

    if args.command == "render":
        generator = make_generator(args.backend)
        cutout = Image.open(args.image).convert("RGBA")
        files = render_clips(generator, cutout, args.out, args.clips, args.breed)
        for clip, path in files.items():
            print(f"{clip}: {path}")
        return

    once = getattr(args, "once", False)
    poll = getattr(args, "poll", 10.0)
    run_queue(args.backend, once, poll)


if __name__ == "__main__":
    main()
