"""Motion clips the worker generates for each dog.

Keep CLIP_NAMES in sync with `AvatarClipStore.clipNames` in the iOS app and with the
`clips` default in supabase/migrations.
"""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class ClipSpec:
    name: str
    action: str
    seconds: float = 4.0


CLIPS: tuple[ClipSpec, ...] = (
    ClipSpec("idle", "stands calmly, breathing gently, blinking and looking around"),
    ClipSpec("wag", "wags its tail happily and tilts its head"),
    ClipSpec("excited", "bounces on its front paws with excitement, tail wagging fast"),
    ClipSpec("hungry", "looks up hopefully and licks its lips"),
    ClipSpec("sleepy", "yawns widely and slowly lies down to rest"),
    ClipSpec("shake", "shakes its fur from head to tail as if drying off"),
    ClipSpec("celebrate", "jumps up joyfully and spins around once"),
    ClipSpec("walk", "trots forward in place with a happy, steady gait"),
)

CLIP_NAMES: tuple[str, ...] = tuple(clip.name for clip in CLIPS)

NEGATIVE_PROMPT = (
    "extra legs, extra tail, missing limbs, deformed paws, distorted face, changing fur colour, "
    "different dog, blurry, low quality, text, watermark, camera motion, cut, background clutter"
)


def clip_spec(name: str) -> ClipSpec:
    for clip in CLIPS:
        if clip.name == name:
            return clip
    raise KeyError(f"Unknown clip: {name}")


def build_prompt(clip: str, breed: str = "") -> str:
    """A prompt that keeps the exact dog from the photo and a static camera."""
    spec = clip_spec(clip)
    subject = f"the {breed.strip()} dog" if breed.strip() else "the dog"
    return (
        f"Photorealistic video of {subject} from the photo, exactly the same dog with identical fur, "
        f"markings and proportions. The dog {spec.action}. Full body visible, centred, "
        "plain light grey studio background, static camera, soft even lighting, smooth natural motion, "
        "seamless loop."
    )
