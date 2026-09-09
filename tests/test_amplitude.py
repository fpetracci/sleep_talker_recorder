import math
import struct
import wave

import pytest


def compute_rms(wav_path: str) -> float:
    """Return the RMS amplitude of a WAV file (0.0-1.0 scale)."""
    with wave.open(wav_path, "rb") as wf:
        frames = wf.readframes(wf.getnframes())
        sample_count = len(frames) // 2
        samples = struct.unpack(f"<{sample_count}h", frames)

    if not samples:
        return 0.0

    mean_sq = sum(s * s for s in samples) / len(samples)
    return math.sqrt(mean_sq) / 32768.0


class TestAmplitude:
    def test_sample_has_nonzero_amplitude(self, talking_wav):
        rms = compute_rms(str(talking_wav))
        assert rms > 0.0, "RMS should be positive for a non-silent recording"

    def test_sample_amplitude_above_silence_floor(self, talking_wav):
        """A recording with speech should be well above the noise floor."""
        rms = compute_rms(str(talking_wav))
        assert rms > 0.01, (
            f"RMS {rms:.4f} is suspiciously low — "
            "audio might be silent or conversion failed"
        )
