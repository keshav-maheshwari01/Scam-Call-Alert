# Privacy & security

ScamCallAI records phone calls and uploads the audio to a server **you** run. Nothing is sent to any other
service. This page explains what data exists, what the app is allowed to do, what is not secured yet, and what
you are responsible for.

## What data exists, and for how long

| Data | Where | How long |
|---|---|---|
| Audio chunks (8 seconds each) | The app's private cache folder on the phone | Deleted right after each upload attempt |
| The bundled sample used by **Run Quick Test** | Copied into the app's cache folder | Overwritten each time you run the test |
| Audio upload | A temporary file on the server | Deleted right after transcription, even if transcription fails |
| Caller number | Sent to the server with each chunk and shown on the alert screen | Not stored by the server. Used only to check for suspicious prefixes |
| **Transcript of a call in progress** | **The server's memory** (only when the app sends a `call_id`) | Never written to disk. Dropped the next time a request arrives after 15 minutes of inactivity, and lost when the server restarts |
| Logs | The server's console | Only the word count and the verdict. Transcripts and audio are never logged |

The Whisper speech-to-text model runs on your own computer, so audio is not sent to a third-party API.

## Permissions the app asks for

| Permission | Why |
|---|---|
| `RECORD_AUDIO` | Record the call audio |
| `READ_PHONE_STATE` | Detect that a call is ringing, answered or finished |
| `READ_CALL_LOG` | Read the caller's number |
| `READ_CONTACTS` | Skip recording for numbers saved in your contacts |
| `INTERNET` | Upload audio to your server |
| `VIBRATE` | Vibrate on a scam alert |
| `FOREGROUND_SERVICE` | Keep recording while a call is in progress |
| `FOREGROUND_SERVICE_MICROPHONE` | Required on Android 14+ for a foreground service that uses the microphone |
| `POST_NOTIFICATIONS` | Show the monitoring notification (Android 13+) |
| `USE_FULL_SCREEN_INTENT` | Let the scam alert open over the call screen. On Android 14+ you may also have to allow full-screen notifications for the app in its notification settings |

## Security: known gaps

These are acceptable for local testing on a trusted network and **must be fixed before real use**:

- The API has **no authentication**. Anyone who can reach it can submit audio or text (`POST /` and
  `POST /analyze/text`).
- The app uses **plain HTTP** (`usesCleartextTraffic="true"` in the manifest), so audio travels unencrypted.
- `FLASK_DEBUG=1` exposes Flask's interactive debugger, which allows remote code execution. It is off by
  default. Never enable it on a shared network.
- While a call is in progress its transcript sits in the server's memory (see the table above).

### Hardening checklist

- [ ] Put the server behind HTTPS (for example with Caddy or nginx).
- [ ] Add authentication (an API key or token) and rate limiting.
- [ ] Set `usesCleartextTraffic="false"` and use an `https://` `backend.url`.
- [ ] Keep `MAX_UPLOAD_MB` as low as your clips allow.
- [ ] Never commit `local.properties`, keystores (`.jks`) or recordings. The `.gitignore` blocks them.

### Reporting a vulnerability

Please **do not open a public issue** for security problems. Use GitHub's private vulnerability reporting for
this repository (Security tab → *Report a vulnerability*), or contact the maintainer directly. Include steps to
reproduce and the affected version or commit.

## Your responsibilities

- **Recording laws differ by country and state.** Many places require the consent of **all** parties. Use the app
  only where recording is legal and only on your own device. This is not legal advice.
- **If other people use your server, you are handling their call audio.** Privacy and data-protection rules may
  apply to you. See [Deployment](DEPLOYMENT.md).
- **Never commit real recordings** to this repository. The `.gitignore` blocks audio files except the two
  bundled demo clips. Before publishing a demo clip, listen to it and make sure it contains no real person's
  voice, no private conversation and no copyrighted material.
- **Platform rules:** Android restricts how apps capture call audio, and Google Play restricts both the call-log
  permission and call-recording apps. See [Deployment](DEPLOYMENT.md#publishing-on-google-play).
