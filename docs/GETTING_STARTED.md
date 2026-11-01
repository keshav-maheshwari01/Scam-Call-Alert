# Getting started

This guide takes you from a fresh download to a working scam alert on your phone. Commands use Windows first,
with macOS/Linux alternatives where they differ.

## What you need

| Requirement | Notes |
|---|---|
| Python 3.10 or newer | Developed with Python 3.12 |
| ffmpeg | Whisper uses it to read audio files. Not a pip package |
| Android Studio | To build and run the app |
| An Android phone **or** the emulator | USB debugging must be on for a real phone |
| ~2 GB of free disk space | PyTorch and the Whisper model are large downloads |

Install ffmpeg:

```bash
winget install Gyan.FFmpeg      # Windows (open a NEW terminal afterwards)
brew install ffmpeg             # macOS
sudo apt install ffmpeg         # Debian / Ubuntu
ffmpeg -version                 # should print a version number
```

## 1. Start the backend

```bash
cd backend
python -m venv venv
venv\Scripts\activate           # macOS / Linux: source venv/bin/activate
pip install -r requirements.txt
python app.py
```

The first start downloads the Whisper `small` model, so it can take several minutes. The server loads the
model before it starts listening, which means the first real call is not slow. When you see
`Running on http://...:5000` the server is ready.

Check it: open <http://localhost:5000/health> in a browser. You should see `{"status":"ok"}`.

To use a different model size, set `WHISPER_MODEL` before starting. Smaller is faster but less accurate (see
[Configuration](#configuration)):

```bash
set WHISPER_MODEL=base          # Windows (cmd).  macOS / Linux: export WHISPER_MODEL=base
python app.py
```

## 2. Let your phone reach the server

Skip this section if you only use the emulator.

1. Connect the phone and the computer to the **same Wi-Fi network**.
2. Find the computer's address: run `ipconfig` (Windows) or `ifconfig` / `ip addr` (macOS / Linux) and note the
   IPv4 address, for example `192.168.1.8`.
3. The first time the server runs, Windows may ask whether to let Python through the firewall. Allow it on
   **private networks**.
4. On the phone's browser open `http://192.168.1.8:5000/health` (use your own address). If you see
   `{"status":"ok"}`, the phone can reach the server. If not, see [Troubleshooting](TROUBLESHOOTING.md).

## 3. Build and run the app

1. In Android Studio choose **Open** and select the `android/` folder.
2. For a real phone, add your address to `android/local.properties` (create the file if it does not exist). This
   file is git-ignored, so your IP is never committed:

   ```
   backend.url=http://192.168.1.8:5000/
   ```

   Press **Sync Now** after editing it. The emulator needs no change: it uses `http://10.0.2.2:5000/`, which is
   how the emulator sees your computer.
3. Pick your phone or the emulator in the device dropdown and press **Run**.
4. Grant every permission the app asks for. The home screen shows **Active — You're Protected** (green ring)
   when everything is granted, or **Permissions Required** (red ring). The **Fix Permissions** button opens the
   app's settings page.

## 4. Test it

**Quick test (no call needed).** Tap **Run Quick Test**. The app uploads a bundled sample scam recording to your
server. If the server rates it as a scam, the red alert screen opens. Otherwise the app shows
**Test FAILED — scam sample was not detected** together with the server's reply.

**Real call test.**

1. Make sure the number you will call **from** is not saved in the phone's contacts.
2. Call the phone from another phone and switch to **speakerphone** on the phone running ScamCallAI.
3. Read a scam-style script aloud, for example: *"Your account will be blocked. Please tell me your OTP now."*
4. A notification, **Listening for scam activity…**, shows while the call is monitored. If the call is judged
   suspicious its text changes, and if it is judged a scam you get a red alert.

On Android 14 and newer you may also need to allow **full-screen notifications** for the app in its notification
settings (the wording differs between phone brands). Without it, the alert shows as a normal heads-up
notification instead of taking over the screen.

To see what the app is doing, open **Logcat** in Android Studio and filter by the tag `ScamCallAI`.

## Configuration

Set these as environment variables **before** `python app.py`:

| Variable | Default | Meaning |
|---|---|---|
| `WHISPER_MODEL` | `small` | `tiny`, `base`, `small`, `medium` or `large`. Bigger is more accurate but slower |
| `HOST` | `0.0.0.0` | Interface to listen on. Use `127.0.0.1` to allow only this computer |
| `PORT` | `5000` | Port |
| `MAX_UPLOAD_MB` | `25` | Larger uploads are rejected with HTTP 413 |
| `FLASK_DEBUG` | off | `1` turns on Flask's debug mode. **Never use it on a network you do not trust** |

Whisper always runs in *translate* mode: any spoken language is converted to English text before scoring.

## Try the server without the phone

```bash
# Is it running?
curl http://localhost:5000/health

# Score some text (no audio needed)
curl -X POST http://localhost:5000/analyze/text -H "Content-Type: application/json" \
     -d '{"text": "Sir aapka account band ho jayega OTP dijiye"}'

# Send a recording (from the backend folder)
curl -F "test=@samples/test_scam.mpeg" -F "number=+923001234567" http://localhost:5000/

# Transcribe a file and print the transcript and verdict (no server needed)
python textcheck.py samples/test_scam.mpeg
```

On Windows PowerShell use `curl.exe` instead of `curl`. The responses are described in the [API reference](API.md).

## Run the tests

```bash
cd backend
python -m unittest discover -s tests -v
```

The tests replace Whisper with a stub, so they need neither the model nor ffmpeg and finish in under a second.

## Next steps

- [Architecture](ARCHITECTURE.md): how the pieces fit together
- [Troubleshooting](TROUBLESHOOTING.md): if something does not work
- [Scoring](SCORING.md): how to change what counts as a scam. Add a test for every rule you add
- [Deployment](DEPLOYMENT.md): sharing the app with other people
