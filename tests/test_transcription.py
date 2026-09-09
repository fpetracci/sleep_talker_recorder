import json
import wave

import pytest
from vosk import KaldiRecognizer


def transcribe(model, wav_path: str) -> str:
    """Run Vosk over a WAV file and return the full transcript."""
    with wave.open(wav_path, "rb") as wf:
        assert wf.getnchannels() == 1, "WAV must be mono"
        assert wf.getsampwidth() == 2, "WAV must be 16-bit"
        assert wf.getframerate() == 16000, "WAV must be 16 kHz"

        rec = KaldiRecognizer(model, wf.getframerate())
        rec.SetWords(True)

        words = []
        while True:
            data = wf.readframes(4000)
            if not data:
                break
            if rec.AcceptWaveform(data):
                result = json.loads(rec.Result())
                words.extend(result.get("result", []))

        final = json.loads(rec.FinalResult())
        words.extend(final.get("result", []))

    return " ".join(w["word"] for w in words)


class TestTranscription:
    def test_sample_produces_words(self, vosk_model, talking_wav):
        transcript = transcribe(vosk_model, str(talking_wav))
        assert transcript.strip(), (
            "Transcription returned empty string — "
            "check model language or audio quality"
        )

    def test_sample_transcript_is_italian(self, vosk_model, talking_wav):
        """Smoke test: result should contain at least one recognisable Italian word."""
        common_italian = {
            "il", "la", "lo", "un", "una", "e", "è", "di", "che", "non",
            "si", "mi", "ti", "ci", "ha", "ho", "per", "con", "ma", "se",
            "io", "tu", "lui", "lei", "noi", "voi", "loro",
        }
        transcript = transcribe(vosk_model, str(talking_wav))
        found = common_italian & set(transcript.lower().split())
        assert found, (
            f"No common Italian words found in transcript: '{transcript}'. "
            "Consider checking the model or the recording."
        )

    def test_sample_word_count(self, vosk_model, talking_wav):
        """A 30-second sleep talking clip should have at least a few words."""
        transcript = transcribe(vosk_model, str(talking_wav))
        word_count = len(transcript.split())
        assert word_count >= 1, f"Expected at least 1 word, got {word_count}"


class TestNonSpeechDiscard:
    """Clips with no words: the app's save-if-words-found rule should discard these.

    Design tradeoff: the app is tuned to favor false positives (saving a
    non-speech clip) over false negatives (discarding real sleep talk). So
    while humming reliably produces an empty transcript, short noise bursts
    like a cough can occasionally trigger a spurious one-word hallucination
    from Vosk (e.g. "il") — that's an accepted, non-fatal cost of erring
    toward not losing real speech, not a bug to chase here.
    """

    def test_humming_produces_no_words(self, vosk_model, humming_wav):
        transcript = transcribe(vosk_model, str(humming_wav))
        assert not transcript.strip(), (
            f"Expected empty transcript for humming, got: '{transcript}'"
        )

    def test_coughing_may_false_positive(self, vosk_model, coughing_wav):
        """Coughing may spuriously produce a stray word (e.g. "il") — acceptable,
        since we'd rather over-save than risk discarding real sleep talk."""
        transcript = transcribe(vosk_model, str(coughing_wav))
        word_count = len(transcript.split())
        assert word_count <= 1, (
            f"Expected at most a stray single word for coughing, got: '{transcript}'"
        )
