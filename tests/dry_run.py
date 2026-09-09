#!/usr/bin/env python3
"""
Dry run: convert the sample MP4, print RMS amplitude and Vosk transcript.
Run from the repo root:  uv run python tests/dry_run.py
"""
import json
import math
import struct
import sys
import tempfile
import wave
from pathlib import Path

from vosk import KaldiRecognizer, Model

from conftest import MODEL_DIR, SAMPLES_DIR, convert_to_wav

SAMPLE = SAMPLES_DIR / "test_sample.mp4"


def compute_rms(wav_path: Path) -> float:
    with wave.open(str(wav_path), "rb") as wf:
        frames = wf.readframes(wf.getnframes())
    sample_count = len(frames) // 2
    samples = struct.unpack(f"<{sample_count}h", frames)
    if not samples:
        return 0.0
    return math.sqrt(sum(s * s for s in samples) / len(samples)) / 32768.0


def transcribe(wav_path: Path, model: Model) -> str:
    with wave.open(str(wav_path), "rb") as wf:
        rec = KaldiRecognizer(model, wf.getframerate())
        rec.SetWords(True)
        words = []
        while True:
            data = wf.readframes(4000)
            if not data:
                break
            if rec.AcceptWaveform(data):
                words.extend(json.loads(rec.Result()).get("result", []))
        words.extend(json.loads(rec.FinalResult()).get("result", []))
    return " ".join(w["word"] for w in words)


def main() -> None:
    if not SAMPLE.exists():
        print(f"ERROR: sample not found at {SAMPLE}", file=sys.stderr)
        sys.exit(1)
    if not MODEL_DIR.exists():
        print(f"ERROR: model not found at {MODEL_DIR}. Run: make download-model", file=sys.stderr)
        sys.exit(1)

    print(f"Sample : {SAMPLE}")
    print(f"Model  : {MODEL_DIR}")
    print()

    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        wav_path = Path(tmp.name)

    print("Converting MP4 → WAV (16 kHz mono 16-bit)...")
    convert_to_wav(SAMPLE, wav_path)

    rms = compute_rms(wav_path)
    print(f"RMS amplitude : {rms:.4f}  ({rms * 100:.1f}% of full scale)")
    print()

    print("Loading Vosk model and transcribing...")
    model = Model(str(MODEL_DIR))
    transcript = transcribe(wav_path, model)
    wav_path.unlink()

    print(f"Transcript    : {transcript!r}")


if __name__ == "__main__":
    main()
