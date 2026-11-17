"""Frame generators.

* `ProceduralGenerator` animates the cutout with simple transforms. It needs no GPU, runs in
  seconds, and is used for tests and as a free fallback.
* `WanGenerator` uses the open-source Wan 2.2 TI2V-5B model (Apache 2.0) to generate realistic
  motion of the actual dog, then removes the background from every frame.
"""

from __future__ import annotations

import math
from typing import Protocol

from PIL import Image

from .prompts import NEGATIVE_PROMPT, build_prompt, clip_spec


class Generator(Protocol):
    fps: int

    def generate(self, cutout: Image.Image, clip: str, breed: str = "") -> list[Image.Image]:
        """Returns RGBA frames for one clip."""


def _fit_on_canvas(cutout: Image.Image, canvas: int, margin: float = 0.12) -> tuple[Image.Image, tuple[int, int]]:
    """Scales the cutout to fit a square canvas with a margin, returning it and its paste offset."""
    image = cutout.convert("RGBA")
    bbox = image.getbbox()
    if bbox:
        image = image.crop(bbox)
    available = int(canvas * (1 - 2 * margin))
    scale = available / max(image.size)
    image = image.resize((max(1, int(image.width * scale)), max(1, int(image.height * scale))), Image.LANCZOS)
    offset = ((canvas - image.width) // 2, canvas - image.height - int(canvas * margin / 2))
    return image, offset


class ProceduralGenerator:
    """Breathing, tilting and hopping per clip, without any ML."""

    def __init__(self, canvas: int = 512, fps: int = 16):
        self.canvas = canvas
        self.fps = fps

    def generate(self, cutout: Image.Image, clip: str, breed: str = "") -> list[Image.Image]:
        spec = clip_spec(clip)
        dog, (ox, oy) = _fit_on_canvas(cutout, self.canvas)
        frame_count = max(2, int(spec.seconds * self.fps))
        frames: list[Image.Image] = []
        for index in range(frame_count):
            phase = index / frame_count  # 0..1, loops seamlessly
            angle, dy, sx, sy = self._motion(clip, phase)
            w = max(1, int(dog.width * sx))
            h = max(1, int(dog.height * sy))
            moved = dog.resize((w, h), Image.BILINEAR).rotate(angle, resample=Image.BICUBIC, expand=True)
            frame = Image.new("RGBA", (self.canvas, self.canvas), (0, 0, 0, 0))
            x = ox + (dog.width - moved.width) // 2
            y = oy + (dog.height - moved.height) + int(dy)
            frame.alpha_composite(moved, (max(-moved.width, x), max(-moved.height, y)))
            frames.append(frame)
        return frames

    @staticmethod
    def _motion(clip: str, phase: float) -> tuple[float, float, float, float]:
        two_pi = 2 * math.pi
        breathe = math.sin(two_pi * phase * 2)
        angle, dy = 0.0, 0.0
        sx, sy = 1 - 0.006 * breathe, 1 + 0.015 * breathe
        if clip == "wag":
            angle = 3 * math.sin(two_pi * phase * 4)
        elif clip == "excited":
            angle = 4 * math.sin(two_pi * phase * 6)
            dy = -14 * max(0.0, math.sin(two_pi * phase * 6))
        elif clip == "hungry":
            angle = 6 * math.sin(two_pi * phase)
        elif clip == "sleepy":
            sy = 1 + 0.03 * math.sin(two_pi * phase)
            angle = -3
        elif clip == "shake":
            angle = 5 * math.sin(two_pi * phase * 16) * max(0.0, math.sin(two_pi * phase))
        elif clip == "celebrate":
            dy = -40 * max(0.0, math.sin(two_pi * phase * 2))
            angle = 8 * math.sin(two_pi * phase * 2)
        elif clip == "walk":
            dy = -4 * abs(math.sin(two_pi * phase * 4))
            angle = 1.5 * math.sin(two_pi * phase * 4)
        return angle, dy, sx, sy


class WanGenerator:
    """Realistic clips with Wan 2.2 TI2V-5B. Needs an NVIDIA GPU and `requirements-gpu.txt`."""

    model_id = "Wan-AI/Wan2.2-TI2V-5B-Diffusers"

    def __init__(self, size: int = 704, fps: int = 24, steps: int = 40, guidance: float = 5.0, device: str = "cuda"):
        # Imported lazily so the rest of the worker runs without torch installed.
        import torch
        from diffusers import AutoencoderKLWan, WanImageToVideoPipeline
        from rembg import new_session

        self.size = size
        self.fps = fps
        self.steps = steps
        self.guidance = guidance
        self.torch = torch
        vae = AutoencoderKLWan.from_pretrained(self.model_id, subfolder="vae", torch_dtype=torch.float32)
        self.pipe = WanImageToVideoPipeline.from_pretrained(self.model_id, vae=vae, torch_dtype=torch.bfloat16)
        self.pipe.to(device)
        # Lowers peak memory so 8-16 GB cards can run it.
        self.pipe.enable_model_cpu_offload()
        self.matting = new_session("isnet-general-use")

    def _condition_image(self, cutout: Image.Image) -> Image.Image:
        """The dog centred on a plain light grey background, matching the prompt."""
        dog, offset = _fit_on_canvas(cutout, self.size, margin=0.1)
        background = Image.new("RGBA", (self.size, self.size), (225, 225, 225, 255))
        background.alpha_composite(dog, offset)
        return background.convert("RGB")

    def generate(self, cutout: Image.Image, clip: str, breed: str = "") -> list[Image.Image]:
        from rembg import remove

        spec = clip_spec(clip)
        # Wan expects 4k+1 frames.
        num_frames = int(spec.seconds * self.fps) // 4 * 4 + 1
        result = self.pipe(
            image=self._condition_image(cutout),
            prompt=build_prompt(clip, breed),
            negative_prompt=NEGATIVE_PROMPT,
            height=self.size,
            width=self.size,
            num_frames=num_frames,
            guidance_scale=self.guidance,
            num_inference_steps=self.steps,
            generator=self.torch.Generator(device="cpu").manual_seed(7),
        )
        frames = result.frames[0]
        rgba_frames: list[Image.Image] = []
        for frame in frames:
            if not isinstance(frame, Image.Image):
                frame = Image.fromarray((frame * 255).clip(0, 255).astype("uint8"))
            rgba_frames.append(remove(frame, session=self.matting).convert("RGBA"))
        return rgba_frames


def make_generator(name: str) -> Generator:
    if name == "wan":
        return WanGenerator()
    if name == "procedural":
        return ProceduralGenerator()
    raise ValueError(f"Unknown backend '{name}'. Use 'wan' or 'procedural'.")
