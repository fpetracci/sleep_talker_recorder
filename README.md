# Sleep Talker Recorder

An Android app that automatically records and transcribes sleep talking — fully offline, no cloud required.

## How it works

1. **Settling phase** — when you start the session, the app listens silently for a configurable period (default 15 min) to measure ambient noise and auto-calibrate the detection threshold.
2. **Monitoring phase** — the app continuously watches audio amplitude. When it crosses the threshold, it starts saving audio (including a pre-buffer so the start of a word is never clipped).
3. **Silence detection** — once audio drops back below the threshold for a configurable duration, the recording stops.
4. **Transcription** — the recording is passed to an on-device Vosk speech recogniser. If words are detected, the file is saved as `"first words_HH:MM.wav"`. If nothing was recognised, the file is discarded.

All processing happens locally on the device. No internet connection is used.

## Tech stack

- **Language:** Kotlin
- **Min SDK:** 26 (Android 8.0) — tested on Android 16
- **Speech recognition:** [Vosk](https://alphacephei.com/vosk/) (offline, on-device)
- **Background recording:** Android Foreground Service + WakeLock
- **Database:** Room (recordings metadata)

## Project structure

```
app/src/main/
  kotlin/com/sleeptalker/app/
    MainActivity.kt          # Entry point, start/stop UI
    RecorderService.kt       # Foreground service, audio loop, state machine
    RingBuffer.kt            # Rolling pre-buffer for audio chunks
    AmplitudeAnalyzer.kt     # RMS computation
    WavWriter.kt             # PCM → WAV file
    VoskTranscriber.kt       # Vosk STT wrapper
    RecordingRepository.kt   # Room DB access
  res/
    layout/activity_main.xml
    values/
```

## Getting started

See [setup_repo.md](setup_repo.md) for full environment setup instructions (devcontainer + ADB wireless).

**Short version** — if your environment is already set up:

```bash
# First time only: bootstrap the Gradle wrapper
wget -q https://services.gradle.org/distributions/gradle-8.8-bin.zip -O /tmp/gradle.zip
unzip -q /tmp/gradle.zip -d /opt/gradle
/opt/gradle/gradle-8.8/bin/gradle wrapper --gradle-version 8.8
chmod +x gradlew

# Build
./gradlew assembleDebug

# Deploy
./gradlew installDebug
```

## ADB wireless (quick reference)

```bash
# Pair once
adb pair <phone-ip>:<pairing-port>   # code shown in Developer Options → Wireless Debugging

# Connect each session
adb connect <phone-ip>:<port>
adb devices

# Logs
adb logcat -s SleepTalker
```
