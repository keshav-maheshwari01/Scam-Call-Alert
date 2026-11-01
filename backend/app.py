import logging
import os
import tempfile
import threading
import time
from pathlib import Path

from flask import Flask, jsonify, request

import transcriber
from textcheck import check_text

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger(__name__)

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = int(os.environ.get("MAX_UPLOAD_MB", "25")) * 1024 * 1024

# Transcript of each call in progress, keyed by the app's `call_id` form field.
# Kept in memory only, and forgotten CALL_TTL_SECONDS after the last chunk.
CALL_TTL_SECONDS = 15 * 60
MAX_CHUNKS_PER_CALL = 200
_calls: dict = {}
_calls_lock = threading.Lock()


def _add_to_call(call_id: str, text: str) -> str:
    """Remember this chunk's text and return everything said so far in the call."""
    now = time.time()
    with _calls_lock:
        for old_id in [c for c, v in _calls.items() if now - v["last"] > CALL_TTL_SECONDS]:
            del _calls[old_id]
        call = _calls.setdefault(call_id[:64], {"parts": [], "last": now})
        if len(call["parts"]) < MAX_CHUNKS_PER_CALL:
            call["parts"].append(text)
        call["last"] = now
        return " ".join(call["parts"])


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


@app.route("/", methods=["POST"])
def check():
    """Accepts multipart form-data: `test` = audio file, `number` = caller number (optional)."""
    audio = request.files.get("test")
    if audio is None:
        return jsonify({"error": "missing audio file in form field 'test'"}), 400

    suffix = os.path.splitext(audio.filename or "")[1] or ".audio"
    fd, path = tempfile.mkstemp(suffix=suffix)
    os.close(fd)
    tmp = Path(path)
    try:
        audio.save(path)
        log.info("Transcribing %s (%.1f KB)", audio.filename, tmp.stat().st_size / 1024)
        text = transcriber.transcribe_audio_local(path)
        log.info("Transcribed %d words", len(text.split()))
    except Exception as exc:
        log.exception("Audio processing failed: %s", exc)
        return jsonify({"error": "could not process the audio file"}), 500
    finally:
        # Use missing_ok=True so this is safe even if audio.save() never ran
        tmp.unlink(missing_ok=True)

    caller = request.form.get("number", "") or request.form.get("caller_number", "")
    # Score everything heard so far in this call, not just this 8-second slice: scammers
    # spread their script across the call, so one slice alone rarely reaches SCAM.
    call_id = request.form.get("call_id", "")
    full_text = _add_to_call(call_id, text) if call_id else text
    result = check_text(full_text, caller)
    log.info("Result: %s (score=%s)", result["classification"], result["score"])
    return jsonify(result)


@app.route("/analyze/text", methods=["POST"])
def analyze_text():
    """Quick endpoint for testing: POST JSON {\"text\": \"...\", \"number\": \"...\"}"""
    body = request.get_json(silent=True) or {}
    text = body.get("text", "").strip()
    if not text:
        return jsonify({"error": "missing 'text' field in JSON body"}), 400

    result = check_text(text, body.get("number", ""))
    log.info("Text analysis: %s (score=%s)", result["classification"], result["score"])
    return jsonify(result)


if __name__ == "__main__":
    # Debug mode exposes an interactive debugger (remote code execution) to the whole
    # network when host=0.0.0.0, so it is OFF unless you explicitly set FLASK_DEBUG=1.
    transcriber._get_model()  # load Whisper now, so the first real call isn't slow
    app.run(
        host=os.environ.get("HOST", "0.0.0.0"),
        port=int(os.environ.get("PORT", 5000)),
        debug=os.environ.get("FLASK_DEBUG") == "1",
    )

