
# 🛡 ScamCallAI

Real-time scam-call detection for Android. When an **unknown number** calls, the app records a few seconds of
audio, sends it to a small Python server that transcribes it with **OpenAI Whisper**, scores the transcript
with a rule-based detector (English / Hindi / Hinglish), and — if it looks like a scam — takes over the screen with a
red warning and vibrates the phone.

> ⚠️ **Prototype.** Built as a learning project. It works as a proof of concept, but it is not a production
> security product. See [Limitations](#limitations).

## How it works

```
 Incoming call (number not in contacts)
        │
        ▼
 Android app  ──(10 s WAV over HTTP)──►  Flask server ──► Whisper (speech → English text)
        ▲                                                        │
        │                                                        ▼
  Red alert screen + vibration  ◄──── JSON verdict ◄──── Rule-based scorer (textcheck.py)
```

| Folder | What it is |
|---|---|
| `backend/` | Flask API + Whisper transcription + scam scoring (`textcheck.py`) |
| `android/` | Kotlin app: call listener, audio recorder service, alert screen |

### Scoring (`backend/textcheck.py`)
Each transcript gets a 0–100 risk score: critical regex patterns (+40 each), red-flag phrases (+25), soft
"yellow" words (+10), safe-call phrases such as "thank you for calling" (−20), and a suspicious caller prefix (+30).
`≥70` → **SCAM**, `≥40` → **ALERT**, otherwise **SAFE**.

## Quick start

### 1. Backend
Requires Python 3.9+ and [ffmpeg](https://ffmpeg.org/download.html) installed on your system.

```bash
cd backend
python -m venv venv
source venv/bin/activate          # Windows: venv\Scripts\activate
pip install -r requirements.txt   # installs flask + openai-whisper (pulls in PyTorch, large download)
python app.py                     # listens on http://0.0.0.0:5000
```

Try it without the phone:

```bash
curl -F "test=@samples/test_scam.mpeg" http://localhost:5000/
```

Config via environment variables: `WHISPER_MODEL` (default `small`), `HOST`, `PORT`, `FLASK_DEBUG=1`. Uploads over 25 MB are rejected.

### 2. Android app
1. Open `android/` in Android Studio.
2. Tell the app where your server is by adding a line to `android/local.properties` (this file is git-ignored):
   ```
   backend.url=http://<your-computer-LAN-IP>:5000/
   ```
   Phone and computer must be on the same Wi-Fi. (Default is `http://10.0.2.2:5000/`, which reaches your
   computer from the Android emulator.)
3. Run the app, grant all permissions, and tap **Test Scam Detection** to check the full pipeline.

## Tests

```bash
cd backend
python -m unittest discover -s tests -v
```

## Limitations

- **Not truly "live" yet:** it records one 10-second clip per call, then analyses it.
- **Call audio:** modern Android versions block apps from recording the *other* side of a call; the app only
  hears the phone's microphone (works best on speakerphone).
- **Rule-based detection:** keyword/regex scoring can miss scams (see the expected-failure test) and flag
  innocent calls. Whisper runs in *translate* mode, so Hinglish patterns only match if the translation keeps them.
- **No authentication / HTTPS:** the server accepts audio from anyone on the network and the app uses plain HTTP.
  Run it only on a trusted network.
- **"END CALL NOW" on the alert screen currently only closes the warning**; it does not hang up the call.

## Privacy & legal

This app records phone calls and uploads the audio to your server. Laws on recording calls differ by country —
some require **all parties' consent**. Use it only where it is legal, only on your own device, and never
commit real call recordings to this repo. The server deletes each upload right after transcription.

## License

MIT — see [LICENSE](LICENSE).
=======

