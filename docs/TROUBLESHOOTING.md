# Troubleshooting

Find your symptom in the table. For anything involving the phone, Logcat (filter by the tag `ScamCallAI`) is the
best source of answers.

## Setting up the server

| Symptom | Cause | Fix |
|---|---|---|
| `ModuleNotFoundError: No module named 'whisper'` | The virtual environment is not active, or the requirements were not installed | Activate the venv, then run `pip install -r requirements.txt` |
| `500` errors, and the server console mentions `ffmpeg` | ffmpeg is not installed or not on your PATH | Install it (see [Getting started](GETTING_STARTED.md)), open a **new** terminal and check `ffmpeg -version` |
| The first start takes minutes | Whisper is downloading its model | Wait. Later starts are fast. Use `WHISPER_MODEL=base` for a smaller model |
| `python` is not recognised (Windows) | Python is not on the PATH | Use `py -m venv venv` and `py app.py`, or reinstall Python with "Add to PATH" ticked |
| Chunk replies arrive late | Whisper cannot keep up on your CPU | Use a smaller model: `WHISPER_MODEL=base` or `tiny` |

## Building and running the app

| Symptom | Cause | Fix |
|---|---|---|
| `Unresolved reference 'statusDot'` (or `statusText`, `statusSubtext`) | The layout views were renamed but the Kotlin code still uses the old IDs | In `MainActivity.kt` use `shieldRing`, `heroStatusText` and `heroSubText`, as in the current code |
| `Error running 'app': Pixel 9 is already running as process …` | The emulator is already open, or a crashed copy holds a lock | Stop the emulator in **Device Manager** and run again. If it persists, end the `qemu-system-x86_64.exe` process in Task Manager, or delete the `.lock` files in the AVD's folder (`.android/avd/<name>.avd`) while everything is closed |
| Gradle sync fails after editing `local.properties` | A typo in the file | Check that the line reads exactly `backend.url=http://<ip>:5000/` |

## The Quick Test or the upload fails

| Symptom | Cause | Fix |
|---|---|---|
| **Test Error** with a timeout or "failed to connect" | The phone cannot reach the server | On a real phone `backend.url` must be your computer's LAN IP, not `10.0.2.2` (that only works in the emulator). Same Wi-Fi, server running, firewall allowing Python. Open `http://<ip>:5000/health` in the phone's browser. After changing `local.properties`, press **Sync Now** and rebuild |
| **Test Error**: "Server returned HTTP 500" | The server could not process the audio | Read the server console. The usual cause is a missing ffmpeg |
| **Test Error**: "Server returned HTTP 413" | The upload is bigger than `MAX_UPLOAD_MB` | Raise `MAX_UPLOAD_MB` or use a shorter clip |
| "Cleartext HTTP traffic not permitted" | The app is blocked from using plain `http://` | The manifest sets `usesCleartextTraffic="true"` for local development. Restore it, or move the server to HTTPS |
| **Test FAILED — scam sample was not detected** | The server answered, but did not rate the sample as a scam | Run `python textcheck.py samples/test_scam.mpeg` in `backend/`. It prints the transcript and the verdict, so you can see what Whisper heard |

## During a real call

| Symptom | Cause | Fix |
|---|---|---|
| Nothing happens | The caller is saved in your contacts, or a permission is missing | Test with a number that is not saved. Make sure the home screen shows **Active — You're Protected** |
| A notification says **ScamCallAI could not monitor this call** | Android refused to start the microphone service. Since Android 11 a foreground service started from the background cannot use the microphone, and on Android 14 it throws an error | Check Logcat for `Could not start microphone foreground service`. Android's documentation lists the exemptions. This is a platform limit, see [Limitations](../README.md#limitations) |
| The notification says the microphone is unavailable | The microphone could not be opened during the call | Check Logcat for `AudioRecord did not initialise` |
| Recording starts but nothing is ever detected | Android may be giving the app **silence** during the call | Look in Logcat for lines such as `chunk recorded: 250 KB, peak amplitude 4100`. Speech normally peaks in the thousands. A value near 0 means silence. Try speakerphone |
| A scam is detected but the red screen does not open | Android blocks apps from opening screens from the background | Allow **full-screen notifications** for the app (Android 14+). The alert still appears as a heads-up notification, and tapping it opens the red screen |
| The verdict is `ALERT` but never `SCAM` | `ALERT` only updates the ongoing notification text | This is by design. Only `SCAM` opens the red screen |
| Alerts arrive very late or the service stops | Aggressive battery saving on some phone brands | Exclude the app from battery optimisation in the phone's settings |

## Still stuck?

Open an issue and include: your Android version and phone model, the server's console output, and the Logcat
lines for the tag `ScamCallAI`. **Do not** attach call recordings.
