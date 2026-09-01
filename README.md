# Sleep Talker Recorder

Android app that records and transcribes sleep talking. Fully offline — no cloud, no internet.

**How it works:** on session start, the app listens silently for a configurable settling period (default 15 min) to calibrate the ambient noise threshold. After that it monitors continuously: when volume crosses the threshold it starts recording (with a pre-buffer so word onsets aren't clipped), stops after a configurable silence window, runs Vosk STT on the clip, saves it as `"first words_HH:MM.wav"` if words are found, discards it otherwise.

**Stack:** Kotlin · Vosk (offline STT) · Android Foreground Service · Room DB · min SDK 26

---

## Setup

### 1. Prerequisites (host)

**Docker** (Fedora):
```bash
sudo dnf install docker
sudo systemctl enable --now docker
sudo usermod -aG docker $USER   # log out and back in
```

**VS Code + Dev Containers extension:**
```bash
sudo rpm --import https://packages.microsoft.com/keys/microsoft.asc
sudo sh -c 'echo -e "[code]\nname=Visual Studio Code\nbaseurl=https://packages.microsoft.com/yumrepos/vscode\nenabled=1\ngpgcheck=1\ngpgkey=https://packages.microsoft.com/keys/microsoft.asc" > /etc/yum.repos.d/vscode.repo'
sudo dnf install code
```
Then in VS Code: Extensions → search **Dev Containers** → Install.

### 2. Open in container

```bash
git clone <repo-url> && cd sleep_talker_recorder
code .
```

VS Code will prompt **Reopen in Container** — click it. First open takes ~5 min (downloads Android SDK + uv deps). The container has Java 17, Android SDK 36, ffmpeg, and uv ready.

### 3. Bootstrap Gradle wrapper (first time only)

The wrapper JAR is a binary and not committed. Run once inside the container terminal:

```bash
wget -q https://services.gradle.org/distributions/gradle-8.9-bin.zip -O /tmp/gradle.zip
unzip -q /tmp/gradle.zip -d /opt/gradle
/opt/gradle/gradle-8.9/bin/gradle wrapper --gradle-version 8.9
chmod +x gradlew
```

Then commit so other contributors skip this step:
```bash
git add gradlew third_party/gradle/wrapper/gradle-wrapper.jar third_party/gradle/wrapper/gradle-wrapper.properties
git commit -m "Add Gradle wrapper"
```

### 4. Download Vosk model

The model is gitignored (too large). Download once into `app/src/main/assets/`:

```bash
wget https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip -O /tmp/vosk-model.zip
unzip /tmp/vosk-model.zip -d app/src/main/assets/
mv app/src/main/assets/vosk-model-small-it-0.22 app/src/main/assets/vosk-model-it
```

To switch language, change `vosk.model=vosk-model-it` in `gradle.properties` and drop the new model folder under `assets/`. The value is baked into `BuildConfig.VOSK_MODEL_NAME` at compile time.

---

## Build & deploy

**To a physical phone** (wireless ADB):

Settings → About Phone → tap **Build Number** 7× → Developer Options → **Wireless Debugging** → **Pair device with pairing code**.

```bash
adb pair <ip>:<pairing-port>     # once — enter the 6-digit code
adb connect <ip>:<port>          # each session
adb devices                      # confirm device is listed
./gradlew installDebug           # build + install
```

**To the emulator** — the emulator runs on the host (native GPU + display), the container deploys via ADB.

One-time host setup:
```bash
sudo dnf install android-tools
mkdir -p ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdtools.zip
unzip /tmp/cmdtools.zip -d /tmp/cmdtools-raw
mv /tmp/cmdtools-raw/cmdline-tools ~/android-sdk/cmdline-tools/latest
```

Add to `~/.zshrc`:
```bash
export ANDROID_HOME=$HOME/android-sdk
export PATH=$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator
```

```bash
source ~/.zshrc
yes | sdkmanager --licenses
sdkmanager "platform-tools" "emulator" "system-images;android-35;google_apis;x86_64"
avdmanager create avd -n SleepTalkerAVD -k "system-images;android-35;google_apis;x86_64"
```

Each session:
```bash
# On host
emulator -avd SleepTalkerAVD &
adb kill-server && adb -a nodaemon server start &

# In container (ANDROID_ADB_SERVER_ADDRESS is pre-configured)
adb devices          # should list emulator-5554
make install         # build + deploy to emulator
```

---

## Testing

### Kotlin unit tests (JVM, no device needed)
```bash
make test-unit       # or: ./gradlew test
```
Tests in `app/src/test/`. Cover pure logic: `RingBuffer`, `AmplitudeAnalyzer`.

### Python tests (Vosk model + audio pipeline)
```bash
make test            # downloads Italian Vosk model if missing, then runs pytest
uv run pytest tests/ -v
uv run python tests/dry_run.py   # print RMS + transcript for the sample recording
```
To add a dependency: `uv add --group test <package>`

---

## Quick reference

| Task | Command (in container) |
|---|---|
| Build APK | `./gradlew assembleDebug` |
| Install on phone | `./gradlew installDebug` |
| Install on emulator | `make install` |
| Kotlin unit tests | `make test-unit` |
| All tests | `make test` |
| Dry-run transcript | `uv run python tests/dry_run.py` |
| Live logs | `adb logcat -s SleepTalker` |
| Clean | `./gradlew clean` |
