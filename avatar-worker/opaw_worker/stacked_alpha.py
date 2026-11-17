"""Encode transparent frames as a "stacked alpha" MP4.

Each output frame is twice as tall as the input: the top half holds the colour image and the
bottom half holds the alpha channel as greyscale. The iOS app splits and recombines them
(`StackedAlphaVideoView`). This keeps transparency while using plain H.264, which every
iPhone decodes in hardware.
"""

from __future__ import annotations

import shutil
import subprocess
import tempfile
from pathlib import Path
from typing import Iterable, Sequence

import numpy as np
from PIL import Image


def _even(value: int) -> int:
    return value if value % 2 == 0 else value + 1


def stack_frame(frame: Image.Image, size: tuple[int, int] | None = None) -> Image.Image:
    """Turns one RGBA frame into a stacked RGB frame (colour above, alpha below)."""
    rgba = frame.convert("RGBA")
    if size is not None and rgba.size != size:
        rgba = rgba.resize(size, Image.LANCZOS)
    width, height = rgba.size
    width, height = _even(width), _even(height)
    if (width, height) != rgba.size:
        padded = Image.new("RGBA", (width, height), (0, 0, 0, 0))
        padded.paste(rgba, (0, 0))
        rgba = padded

    pixels = np.asarray(rgba)
    alpha = pixels[..., 3:4]
    # Straight (not premultiplied) colour: the app's mask blend applies alpha once.
    # Fully transparent pixels are zeroed so they compress well.
    colour = np.where(alpha > 0, pixels[..., :3], 0).astype(np.uint8)
    mask = np.repeat(alpha, 3, axis=2).astype(np.uint8)
    stacked = np.concatenate([colour, mask], axis=0)
    return Image.fromarray(stacked, mode="RGB")


def unstack_frame(stacked: Image.Image) -> Image.Image:
    """Inverse of `stack_frame`, used by tests and previews."""
    array = np.asarray(stacked.convert("RGB"))
    half = array.shape[0] // 2
    colour = array[:half]
    alpha = array[half:, :, 0:1]
    colour = np.where(alpha > 0, colour, 0)
    rgba = np.concatenate([colour, alpha], axis=2).astype(np.uint8)
    return Image.fromarray(rgba, mode="RGBA")


def ffmpeg_path() -> str:
    path = shutil.which("ffmpeg")
    if path is None:
        raise RuntimeError("ffmpeg is required. Install it (for example `brew install ffmpeg` or `apt install ffmpeg`).")
    return path


def encode_stacked_mp4(frames: Sequence[Image.Image] | Iterable[Image.Image], output: Path, fps: int = 16, max_side: int = 512, crf: int = 20) -> Path:
    """Writes frames (RGBA) to `output` as a stacked-alpha H.264 MP4."""
    frames = list(frames)
    if not frames:
        raise ValueError("No frames to encode")
    width, height = frames[0].size
    scale = min(1.0, max_side / max(width, height))
    size = (_even(int(width * scale)), _even(int(height * scale)))

    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for index, frame in enumerate(frames):
            stack_frame(frame, size).save(Path(tmp) / f"{index:05d}.png")
        command = [
            ffmpeg_path(),
            "-y",
            "-loglevel", "error",
            "-framerate", str(fps),
            "-i", str(Path(tmp) / "%05d.png"),
            "-c:v", "libx264",
            "-pix_fmt", "yuv420p",
            "-profile:v", "high",
            "-crf", str(crf),
            "-movflags", "+faststart",
            "-an",
            str(output),
        ]
        subprocess.run(command, check=True)
    return output
