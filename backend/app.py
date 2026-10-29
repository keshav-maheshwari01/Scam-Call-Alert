import os
import tempfile

from flask import Flask, jsonify, request

import transcriber
from textcheck import check_text

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 25 * 1024 * 1024  # reject uploads over 25 MB


@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})


@app.route("/", methods=["POST"])
def check():
    """Accepts multipart form-data: `test` = audio file, `number` = caller number (optional)."""
    audio = request.files.get("test")
    if audio is None:
        return jsonify({"error": "missing audio file in form field 'test'"}), 400

    # unique temp file per request (the old fixed 'temp.mp3' broke on concurrent calls)
    suffix = os.path.splitext(audio.filename or "")[1] or ".audio"
    fd, path = tempfile.mkstemp(suffix=suffix)
    os.close(fd)
    try:
        audio.save(path)
        text = transcriber.transcribe_audio_local(path)
    except Exception:
        # bad / unreadable audio shouldn't crash the request with a traceback
        return jsonify({"error": "could not process the audio file"}), 500
    finally:
        os.remove(path)  # don't keep recordings of calls on disk

    return jsonify(check_text(text, request.form.get("number", "")))


if __name__ == "__main__":
    # Debug mode exposes an interactive debugger (remote code execution) to the whole
    # network when host=0.0.0.0, so it is OFF unless you explicitly set FLASK_DEBUG=1.
    app.run(
        host=os.environ.get("HOST", "0.0.0.0"),
        port=int(os.environ.get("PORT", 5000)),
        debug=os.environ.get("FLASK_DEBUG") == "1",
    )
