# 🛡 ScamCallAI

Real-time scam-call detection for Android. When someone **who is not in your contacts** calls, the app listens
to the call in short 8-second chunks and sends each chunk to a small Python server running on your own computer.
The server turns the speech into text with **OpenAI Whisper** and scores it with a rule-based detector that
understands **English, Hindi and Hinglish**. If the call starts to look like a scam, the phone shows a full-screen
red warning and vibrates.

> ⚠️ **Prototype.** This is a learning project. It works as a proof of concept, but it is not a production
> security product. Read [Limitations](#limitations) before relying on it.

## Features

- Only analyses calls from numbers that are **not saved in your contacts**.
- Analyses the call **while it is happening** (8-second chunks, up to about 5 minutes) and keeps a running
  score for the whole call, so signals spread across the conversation add up.
- Detects common scam scripts in English, Hindi and Hinglish: OTP / PIN requests, fake KYC and "account will be
  blocked" threats, arrest threats, lottery prizes, remote-access apps, gift cards and more.
- Three verdicts: `SAFE`, `ALERT` (suspicious) and `SCAM` (full-screen red alert with vibration).
- Everything runs on your own machine: local Whisper, no cloud API, no API keys.
- A **Run Quick Test** button in the app checks the whole pipeline with a bundled sample clip.

## How it works

```
 Call from a number not in your contacts
            │   CallReceiver (listens for phone-state changes)
            ▼
 AudioRecorderService ──every 8 s──► BackendClient ──HTTP POST──► Flask server (backend/app.py)
 (foreground service, microphone)    audio + number + call_id            │
            ▲                                                            ▼
            │                                               Whisper speech-to-text (transcriber.py)
            │                                                            │ text
            │                                        add to this call's transcript so far
            │                                                            ▼
 ScamAlertActivity ◄── full-screen notification ◄── JSON verdict ◄── rule-based scoring (textcheck.py)
```

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the details, including what each file does.

| Folder | What it is |
|---|---|
| `backend/` | Flask API, Whisper transcription and the scam-scoring rules |
| `android/` | Kotlin app: call listener, recorder service, alert screen |
| [`docs/`](docs/) | Setup guide, architecture, API, scoring, privacy and security, troubleshooting, deployment |

## Quick start

Full step-by-step instructions (including Windows firewall and finding your IP) are in
**[docs/GETTING_STARTED.md](docs/GETTING_STARTED.md)**. The short version:

**1. Start the server** (needs Python 3.10+ and [ffmpeg](https://ffmpeg.org/download.html)):

```bash
cd backend
python -m venv venv
venv\Scripts\activate             # macOS / Linux: source venv/bin/activate
pip install -r requirements.txt   # downloads PyTorch, so it is large
python app.py                     # first start also downloads the Whisper model
```

**2. Run the app.** Open the `android/` folder in Android Studio. On a real phone, add your computer's address
to `android/local.properties` (this file is git-ignored) and press **Sync Now**:

```
backend.url=http://<your-computer-LAN-IP>:5000/
```

The emulator works without this line (it reaches your computer at `10.0.2.2`). Run the app, grant the
permissions and tap **Run Quick Test**. If the pipeline works, the red scam screen opens.

## Documentation

| Document | Contents |
|---|---|
| [Getting started](docs/GETTING_STARTED.md) | Step-by-step setup, configuration and tests |
| [Architecture](docs/ARCHITECTURE.md) | Components, call lifecycle, chunking, threading, notifications |
| [API reference](docs/API.md) | Endpoints, fields, responses and errors |
| [Scoring](docs/SCORING.md) | How the 0–100 risk score works, with real examples |
| [Privacy & security](docs/PRIVACY_AND_SECURITY.md) | What data goes where, app permissions, security gaps and legal notes |
| [Troubleshooting](docs/TROUBLESHOOTING.md) | Fixes for the problems you are most likely to hit |
| [Deployment](docs/DEPLOYMENT.md) | Building an APK, sharing it, hosting the server, and why it is not on Google Play |

## Limitations

- **It may only hear your side of the call.** Modern Android restricts capturing call audio, so the app
  records from the phone's microphone. This works best on speakerphone, and on some phones the microphone is
  silent during a call.
- **Android restricts background recording.** Starting a microphone service while the app is in the background
  is blocked on newer Android versions unless an exemption applies. If that happens the app shows a
  "could not monitor this call" notification instead of failing silently. See
  [Troubleshooting](docs/TROUBLESHOOTING.md).
- **Rule-based detection misses things.** Scams that avoid the listed words get through, and unusual honest
  calls can raise an alert. For example, the sentence *"your parcel is held at customs, you are under digital
  arrest, stay on the video call"* currently scores 0.
- **No authentication or HTTPS.** The server accepts audio from anyone on the network and the app uses plain
  HTTP. Run it only on a network you trust.
- **The alert buttons cannot hang up the call.** They only close the warning.
- **It is not suitable for Google Play as built.** Play restricts the call-log permission and call-recording
  apps. You can share a signed APK directly instead, see [Deployment](docs/DEPLOYMENT.md).

## Future work

Ideas, roughly in order of value:

1. **Detect more scams.** Add rules for scripts the detector misses today, such as "digital arrest" calls and
   paraphrases like "tell me the six digit code", and measure accuracy on a list of real example sentences.
2. **Add a second detection layer** (a small classifier or a language model) for borderline `ALERT` calls.
3. **Make it faster and safer to run.** Faster or smaller Whisper models, streaming audio, authentication, HTTPS,
   and a server-address setting inside the app.
4. **Help the user act.** Show the reason and score on the alert screen, add a real hang-up button, keep a call
   history and a personal block-list, and add a report shortcut. In India, the DoT's Chakshu facility on the
   Sanchar Saathi portal takes reports of suspected fraud calls, and the national cyber-crime helpline is 1930.
   Check the current process before building.
5. **Engineering.** Android unit tests, a CI job that builds the app, and screenshots in this README.

## Privacy & legal

This app records phone calls and uploads the audio to a server **you** run. Laws on recording calls differ by
country, and some require **all parties' consent**. Use it only where it is legal and only on your own device,
and never commit real call recordings to this repository. Details are in [docs/PRIVACY_AND_SECURITY.md](docs/PRIVACY_AND_SECURITY.md).

## License

MIT, see [LICENSE](LICENSE).
