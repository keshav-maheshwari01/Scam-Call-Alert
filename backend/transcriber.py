"""Speech-to-text using a local OpenAI Whisper model (free, no API key needed).

Model sizes: tiny, base, small, medium, large - bigger = more accurate but slower.
The model is downloaded automatically on first run.
"""
import os

import whisper

MODEL_NAME = os.environ.get("WHISPER_MODEL", "small")
model = whisper.load_model(MODEL_NAME)


def transcribe_audio_local(audio_path: str) -> str:
    """Transcribe (and translate to English) the audio file at `audio_path`."""
    result = model.transcribe(audio_path, task="translate")
    return result["text"]
