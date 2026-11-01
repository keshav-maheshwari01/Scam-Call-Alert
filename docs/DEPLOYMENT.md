# Deployment: sharing and publishing

## Short answers

| Goal | Possible? |
|---|---|
| Use it yourself on your own phone | **Yes.** Run the app from Android Studio, or install an APK |
| Share the APK with friends or classmates | **Yes**, as a "sideloaded" APK, but each person needs a server their phone can reach |
| Use it away from your home Wi-Fi | **Yes**, but only if the server is reachable from the internet, and then it must have HTTPS and authentication first |
| Publish it on Google Play | **Not as built.** See [below](#publishing-on-google-play) |

## 1. Build an APK

**Quick test build (debug).** In Android Studio choose **Build → Build Bundle(s) / APK(s) → Build APK(s)**, or
run this in the `android/` folder:

```bash
gradlew.bat assembleDebug        # Windows.  macOS / Linux: ./gradlew assembleDebug
```

The file appears under `android/app/build/outputs/apk/debug/`.

**A build to share (signed release).** Android only installs signed APKs, and a release APK needs your own key:

1. Choose **Build → Generate Signed App Bundle / APK…**, pick **APK**, and create a new keystore (a `.jks` file).
2. Choose the **release** build type and finish. Android Studio shows where the APK was written.
3. **Keep the keystore and its passwords safe and never commit them.** If you lose the key you cannot publish
   updates that install over the old version.

Before sharing a new version, raise `versionCode` (and `versionName`) in `android/app/build.gradle.kts`.

> **The server address is baked into the APK.** `backend.url` from `local.properties` is compiled in when you
> build. An APK built with `http://192.168.1.8:5000/` only works on that Wi-Fi while your computer is running the
> server. Change `local.properties` and rebuild to point the app somewhere else. The address is stored as plain
> text in the APK, so do not publish a build that contains an address you want to keep private.

## 2. Installing the APK on another phone

1. Copy the APK to the phone (cable, cloud drive, messaging app).
2. Open it. Android asks you to allow **Install unknown apps** for the app you opened it from.
3. Android's Play Protect may show a warning for an app from outside the Play Store that asks for sensitive
   permissions such as the call log and microphone. That is expected for a sideloaded app.
4. Open the app and grant every permission, then see [Getting started](GETTING_STARTED.md#3-build-and-run-the-app).

A convenient way to distribute the file is a **GitHub Release**: on your repository choose *Releases → Draft a new
release* and attach the signed APK. Do not commit APK files to the repository itself.

## 3. Running the server for other people

The phone sends audio to your server, so someone has to run it.

| Option | Works when | Notes |
|---|---|---|
| Your own computer | The phone is on the same Wi-Fi | The easiest option. Allow Python through the firewall |
| A rented cloud server | Anywhere | Needs enough memory and CPU for Whisper (it is slow without a GPU), plus HTTPS and authentication |
| A tunnel to your computer | Anywhere, while your computer is on | Gives your computer a public HTTPS address. Still add authentication first |

**Do not put the current server on the internet.** It has no authentication, no rate limiting, and the app talks
to it over plain HTTP. Complete the [hardening checklist](PRIVACY_AND_SECURITY.md#hardening-checklist) first.

**If other people's calls go through your server, you are handling their audio.** Tell them clearly what is
recorded and where it goes, and check the recording and data-protection laws that apply to you. See
[Privacy & security](PRIVACY_AND_SECURITY.md).

## Publishing on Google Play

As built, ScamCallAI is unlikely to be accepted on Google Play:

- Google restricts the **call-log permission** (`READ_CALL_LOG`, which the app uses to read the caller's number)
  to the user's default Phone or Assistant app, plus a short list of exceptions.
- Google has also restricted **third-party call-recording apps**. Calls recorded by the phone's own built-in
  dialer are treated differently.
- Android itself limits what apps can capture during a call, so even a published app may not hear the caller.

Policies change, so read the current rules before deciding: the
[SMS and Call Log permissions policy](https://support.google.com/googleplay/android-developer/answer/9047303) is
the place to start.

The realistic paths are:

1. **Share the APK directly** (GitHub Releases or a link). This is the best fit for a prototype.
2. **Change what the app is.** For example, build it as a default dialer or a call-screening app, which are
   allowed to use the call-related permissions. That is a major redesign.

## Checklist before you share a build

- [ ] The app is built as a **signed release** and `versionCode` was increased.
- [ ] `backend.url` points to a server your users can reach.
- [ ] If the server is outside your home network: HTTPS and authentication are in place, and
      `usesCleartextTraffic` is `false`.
- [ ] No keystore, `local.properties`, recording or APK is committed to the repository.
- [ ] You told users about the recording laws, what is uploaded, and the known limits (speakerphone, some phones
      block microphone access during calls).
- [ ] You tested on at least one real phone, and noted the Android version in the README.
