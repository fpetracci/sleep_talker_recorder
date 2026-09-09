import json
import os
import subprocess
import wave
from pathlib import Path

import pytest

SAMPLES_DIR = Path("tests") / "samples"
MODEL_DIR = Path("tests") / "models" / "vosk-model-it"


def convert_to_wav(src: Path, dst: Path) -> None:
    """Convert any audio/video file to 16kHz mono 16-bit PCM WAV."""
    subprocess.run(
        [
            "ffmpeg", "-y",
            "-i", str(src),
            "-ar", "16000",
            "-ac", "1",
            "-sample_fmt", "s16",
            str(dst),
        ],
        check=True,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


@pytest.fixture(scope="session")
def vosk_model():
    if not MODEL_DIR.exists():
        pytest.skip(
            f"Vosk Italian model not found at {MODEL_DIR}. "
            "Run: make download-model  (or see setup_repo.md)"
        )
    from vosk import Model
    return Model(str(MODEL_DIR))


@pytest.fixture(scope="session")
def talking_wav(tmp_path_factory):
    src = SAMPLES_DIR / "test_sample.mp4"
    assert src.exists(), f"Sample file missing: {src}"
    dst = tmp_path_factory.mktemp("audio") / "test_sample.wav"
    convert_to_wav(src, dst)
    return dst


@pytest.fixture(scope="session")
def humming_wav(tmp_path_factory):
    """Humming: no words — the app should NOT save this clip."""
    src = SAMPLES_DIR / "sample_humming.m4a"
    assert src.exists(), f"Sample file missing: {src}"
    dst = tmp_path_factory.mktemp("audio") / "sample_humming.wav"
    convert_to_wav(src, dst)
    return dst


@pytest.fixture(scope="session")
def coughing_wav(tmp_path_factory):
    """Coughing: no words — the app should NOT save this clip."""
    src = SAMPLES_DIR / "sample_coughin.m4a"
    assert src.exists(), f"Sample file missing: {src}"
    dst = tmp_path_factory.mktemp("audio") / "sample_coughin.wav"
    convert_to_wav(src, dst)
    return dst
