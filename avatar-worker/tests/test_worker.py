import json
import re
import shutil
import subprocess
from pathlib import Path

import numpy as np
import pytest
from PIL import Image, ImageDraw

from opaw_worker.generators import ProceduralGenerator
from opaw_worker.prompts import CLIP_NAMES, build_prompt
from opaw_worker.stacked_alpha import encode_stacked_mp4, stack_frame, unstack_frame
from opaw_worker.worker import render_clips

ROOT = Path(__file__).resolve().parents[2]


def fake_cutout(size=(300, 240)) -> Image.Image:
    """A brown blob on a transparent background, standing in for a dog cutout."""
    image = Image.new("RGBA", size, (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse((40, 60, 260, 220), fill=(150, 100, 60, 255))
    draw.ellipse((200, 20, 290, 110), fill=(130, 85, 50, 255))
    return image


def test_clip_names_match_ios_app():
    swift = (ROOT / "Opaw/Services/AvatarClipStore.swift").read_text()
    match = re.search(r"clipNames = \[(.*?)\]", swift)
    assert match, "clipNames not found in AvatarClipStore.swift"
    ios_names = tuple(json.loads(f"[{match.group(1)}]"))
    assert ios_names == CLIP_NAMES


def test_clip_names_match_migration_default():
    sql = next((ROOT / "supabase/migrations").glob("*_init.sql")).read_text()
    match = re.search(r"clips text\[\] not null default array\[(.*?)\]", sql)
    assert match
    names = tuple(name.strip().strip("'") for name in match.group(1).split(","))
    assert names == CLIP_NAMES


def test_mood_clip_names_exist():
    swift = (ROOT / "Packages/OpawKit/Sources/OpawKit/AvatarMood.swift").read_text()
    for name in re.findall(r'return "([a-z]+)"\n', swift.split("clipName")[1]):
        assert name in CLIP_NAMES


def test_prompt_mentions_breed_and_static_camera():
    prompt = build_prompt("wag", "Shiba Inu")
    assert "Shiba Inu" in prompt
    assert "static camera" in prompt
    with pytest.raises(KeyError):
        build_prompt("moonwalk")


def test_stack_round_trip_preserves_alpha_and_colour():
    frame = fake_cutout((64, 50))
    stacked = stack_frame(frame)
    assert stacked.size == (64, 100)
    restored = np.asarray(unstack_frame(stacked))
    original = np.asarray(frame)
    assert np.array_equal(restored[..., 3], original[..., 3])
    opaque = original[..., 3] == 255
    assert np.array_equal(restored[opaque][:, :3], original[opaque][:, :3])


def test_stack_pads_odd_sizes():
    stacked = stack_frame(Image.new("RGBA", (33, 21), (255, 0, 0, 255)))
    assert stacked.size == (34, 44)


def test_procedural_frames_loop_and_keep_transparency():
    generator = ProceduralGenerator(canvas=128, fps=8)
    frames = generator.generate(fake_cutout(), "excited")
    assert len(frames) == 32
    for frame in frames:
        assert frame.mode == "RGBA"
        alpha = np.asarray(frame)[..., 3]
        assert alpha.max() == 255
        assert alpha[0, 0] == 0


@pytest.mark.skipif(shutil.which("ffmpeg") is None, reason="ffmpeg not installed")
def test_render_writes_stacked_mp4(tmp_path):
    generator = ProceduralGenerator(canvas=128, fps=8)
    files = render_clips(generator, fake_cutout(), tmp_path, ["idle", "wag"])
    assert set(files) == {"idle", "wag"}
    probe = subprocess.run(
        ["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height,codec_name",
         "-of", "json", str(files["wag"])],
        check=True, capture_output=True, text=True,
    )
    stream = json.loads(probe.stdout)["streams"][0]
    assert stream["codec_name"] == "h264"
    assert stream["height"] == 2 * stream["width"]


@pytest.mark.skipif(shutil.which("ffmpeg") is None, reason="ffmpeg not installed")
def test_encode_rejects_empty(tmp_path):
    with pytest.raises(ValueError):
        encode_stacked_mp4([], tmp_path / "x.mp4")
