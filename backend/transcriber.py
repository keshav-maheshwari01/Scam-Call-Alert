"""Speech-to-text using a local OpenAI Whisper model (free, no API key needed).

Model sizes: tiny, base, small, medium, large - bigger = more accurate but slower.
The model is downloaded automatically on first run (~= 244 MB for "small").

Set the WHISPER_MODEL environment variable to choose a different size, e.g.:
    WHISPER_MODEL=base python app.py
"""
import os
import threading
from typing import Optional

import whisper

MODEL_NAME: str = os.environ.get("WHISPER_MODEL", "small")
_model: Optional[whisper.Whisper] = None  # loaded once, see _get_model()
_model_lock = threading.Lock()            # stops two uploads loading the model twice
_infer_lock = threading.Lock()            # one transcription at a time


def _get_model() -> whisper.Whisper:
    """Load (and cache) the Whisper model on first use."""
    global _model
    with _model_lock:
        if _model is None:
            _model = whisper.load_model(MODEL_NAME)
        return _model


def transcribe_audio_local(audio_path: str) -> str:
    """Transcribe (and translate to English) the audio file at `audio_path`."""
    model = _get_model()
    with _infer_lock:  # parallel runs on one CPU only slow each other down
        result = model.transcribe(audio_path, task="translate")
    return result["text"]
