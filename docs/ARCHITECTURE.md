# Architecture

ScamCallAI has two parts that talk over HTTP: an **Android app** that watches calls and records audio, and a
**Python server** that understands the audio.

```
┌──────────────────────────── Android phone ────────────────────────────┐        ┌───────────── your computer ─────────────┐
│ CallReceiver ─► AudioRecorderService ─► BackendClient ─────────────────┼─ HTTP ─┼► app.py ─► transcriber.py ─► textcheck.py │
│                         │                                              │        │            (Whisper)         (rules)       │
│                         └─► notifications ─► ScamAlertActivity ◄───────┼─ JSON ─┼────────────────────────────────────────────┘
└────────────────────────────────────────────────────────────────────────┘
```

## Android app (`android/`)

| Component | Responsibility |
|---|---|
| `CallReceiver` | Receives phone-state changes. On **ringing** it remembers the caller number. On **answered** it starts the recorder, but only if the number is not in your contacts. On **idle** (call over) it stops the recorder |
| `AudioRecorderService` | A foreground service of type `microphone`. Records the call in 8-second chunks, uploads each one, and raises the alert |
| `BackendClient` | Sends one chunk to the server as a `multipart/form-data` upload and reads the JSON verdict |
| `ScamAlertActivity` | The full-screen red warning (shows the caller number and safety tips) and the vibration |
| `MainActivity` | Home screen: permission requests, status ring, **Run Quick Test** and **Fix Permissions** buttons |

### Call lifecycle

1. **Ringing.** `CallReceiver` stores the incoming number. If the number is hidden, it is stored as `unknown`
   (and treated as an unknown caller).
2. **Answered.** If the number is not a saved contact, `CallReceiver` starts `AudioRecorderService`. The service
   shows an ongoing notification and generates a random `call_id` for this call.
3. **Recording.** The service records 16 kHz, mono, 16-bit audio and cuts it into **8-second WAV chunks**. Each
   finished chunk is uploaded on its own thread, so the next chunk starts recording immediately with no gap.
4. **Verdicts.** Each reply updates the ongoing notification. `ALERT` changes its text to a warning. `SCAM` raises
   the full-screen alert once, and the service stops recording.
5. **Call ends.** `CallReceiver` stops the service. Chunk files are deleted after every upload attempt.

The service stops by itself after 40 chunks (about 5 minutes), when a scam has been reported, or when the
microphone cannot be opened.

### Notifications

| ID | Channel | Purpose |
|---|---|---|
| 1 | `scam_detection` (low importance) | Ongoing "monitoring" notification, updated with `ALERT` / `SCAM` text |
| 2 | `scam_alert` (high importance) | The scam alert. Carries a **full-screen intent**, so it can open the red screen over the call |
| 3 | `scam_alert` (high importance) | "ScamCallAI could not monitor this call" error, so failures are never silent |

Android 10 and newer block apps from opening an activity directly from a background service, which is why the
alert goes through a notification with a full-screen intent. The service also tries a direct launch, which works
when the app happens to be on screen.

## Backend (`backend/`)

| File | Responsibility |
|---|---|
| `app.py` | HTTP layer. Saves each upload to a unique temp file, calls Whisper, adds the text to the call's transcript, scores it, deletes the file |
| `transcriber.py` | Local Whisper. Loads the model once (thread-safe) and runs one transcription at a time |
| `textcheck.py` | Pure-Python scoring rules. No heavy imports, so it is fast and easy to test |

### Whole-call scoring

A scammer rarely says everything in 8 seconds. When a request includes a `call_id`, the server appends the new
chunk's text to the transcript it already holds for that call and scores **everything said so far**. A phrase
that appears in two chunks is counted once.

The transcripts live in memory only. They are discarded the next time a request arrives after 15 minutes of
inactivity, at most 200 chunks are kept per call, and everything is lost when the server restarts.

### Language handling

Whisper runs in `translate` mode: whatever language is spoken (including Hindi), the text comes back in English.
The scoring rules contain English words and romanised Hindi (Hinglish), so they are tested against that output.

## Design choices

| Choice | Why | Trade-off |
|---|---|---|
| Server on your own computer | Free, private, no API keys | The phone must reach your computer, and Whisper is slow without a GPU |
| Local Whisper instead of a cloud API | Audio never leaves your network | Slower; accuracy depends on the model size |
| Rule-based scoring | Fast, explainable (the reply says why), easy to extend | Misses paraphrases and new scripts, see [Scoring](SCORING.md) |
| 8-second chunks | The first verdict arrives early, and uploads overlap with recording | More requests; a sentence can be split across two chunks |
| Microphone recording | The only audio source available to normal apps | May miss the caller's voice unless speakerphone is on |

## Latency

The earliest possible alert arrives after the first chunk is recorded (8 seconds) plus the time Whisper needs
to transcribe it. On a CPU the `small` model can take several seconds per chunk, so chunk replies may arrive
slightly behind the live call. Use `WHISPER_MODEL=base` or `tiny` if the server cannot keep up.
