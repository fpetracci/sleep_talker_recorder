# Development Environment Setup

This project builds an Android APK. The recommended workflow uses a **VS Code devcontainer** for the build environment and **wireless ADB** to deploy to your phone — no Android Studio required.

---

## Option A — Devcontainer (Recommended)

### Prerequisites on the host machine

**1. Docker**

On Fedora:
```bash
sudo dnf install docker
sudo systemctl enable --now docker
sudo usermod -aG docker $USER   # log out and back in after this
```

Or use **Podman** with the Docker compatibility shim (Fedora ships Podman by default):
```bash
sudo dnf install podman podman-docker
# VS Code Dev Containers works with Podman Desktop or the podman socket:
systemctl --user enable --now podman.socket
```

**2. VS Code + Dev Containers extension**

```bash
# Install VS Code if not already installed
sudo rpm --import https://packages.microsoft.com/keys/microsoft.asc
sudo sh -c 'echo -e "[code]\nname=Visual Studio Code\nbaseurl=https://packages.microsoft.com/yumrepos/vscode\nenabled=1\ngpgcheck=1\ngpgkey=https://packages.microsoft.com/keys/microsoft.asc" > /etc/yum.repos.d/vscode.repo'
sudo dnf install code
```

Then install the extension inside VS Code:
- Open VS Code → Extensions (`Ctrl+Shift+X`) → search **Dev Containers** → Install

### Open the project in a container

```bash
git clone <repo-url>
cd sleep_talker_recorder
code .
```

VS Code will detect `.devcontainer/devcontainer.json` and show a prompt:
> *Reopen in Container*

Click it. The first build takes ~5 minutes (downloads the Android SDK). Subsequent opens are fast (cached layers).

The container automatically:
- Installs Java 17 (Microsoft OpenJDK)
- Downloads Android SDK command-line tools
- Installs Android platform 36 (Android 16) and build tools
- Persists the Gradle dependency cache in a named Docker volume (`sleep-talker-gradle-cache`) across rebuilds

### Bootstrap the Gradle wrapper (first time only)

The `gradlew` script and its companion JAR are not committed to the repo (the JAR is a binary). Generate them once from inside the container terminal:

```bash
wget -q https://services.gradle.org/distributions/gradle-8.9-bin.zip -O /tmp/gradle.zip
unzip -q /tmp/gradle.zip -d /opt/gradle
cd /workspaces/sleep_talker_recorder
/opt/gradle/gradle-8.9/bin/gradle wrapper --gradle-version 8.9 --gradle-distribution-path third_party/gradle/wrapper
chmod +x gradlew
```

After this you can commit `gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` so other contributors don't need to repeat the step.

### Build the APK inside the container

```bash
./gradlew assembleDebug
# APK output: app/build/outputs/apk/debug/app-debug.apk
```

---

## Option B — Native Fedora Setup (without container)

If you prefer a direct install:

**1. Java 17**
```bash
sudo dnf install java-17-openjdk java-17-openjdk-devel
```

**2. Android SDK command-line tools**
```bash
mkdir -p ~/android-sdk/cmdline-tools
wget https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip -O /tmp/cmdtools.zip
unzip /tmp/cmdtools.zip -d /tmp/cmdtools-raw
mv /tmp/cmdtools-raw/cmdline-tools ~/android-sdk/cmdline-tools/latest
```

Add to `~/.zshrc` (or `~/.bashrc`):
```bash
export ANDROID_HOME="$HOME/android-sdk"
export ANDROID_SDK_ROOT="$HOME/android-sdk"
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools"
```

```bash
source ~/.zshrc
yes | sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

**3. VS Code with Kotlin extension**
```bash
code --install-extension fwcd.kotlin
code --install-extension mathiasfrohlich.Kotlin
```

---

## Deploying to your Android phone — Wireless ADB

USB passthrough in containers is inconvenient. Use **wireless debugging** instead — works from both the container and the host.

### First-time pairing (do this once)

On your **Android 16 phone**:
1. Settings → About Phone → tap **Build Number** 7 times to unlock Developer Options
2. Settings → System → Developer Options → enable **Wireless Debugging**
3. Tap **Wireless Debugging** → **Pair device with pairing code**
4. Note the IP address, pairing port, and 6-digit code shown on screen

On your **computer** (inside the container or on the host):
```bash
adb pair <phone-ip>:<pairing-port>
# Enter the 6-digit code when prompted
```

You only pair once. After pairing, to connect in future sessions:
```bash
# Check the "IP address & port" shown in Wireless Debugging settings
adb connect <phone-ip>:<port>
adb devices   # should show your device
```

### Deploy the debug APK

```bash
./gradlew installDebug
# or manually:
adb install app/build/outputs/apk/debug/app-debug.apk
```

---

## Running the tests

The Python test suite lives in `tests/` and covers two things:

- **Transcription** (`test_transcription.py`) — runs the Vosk Italian model over `tests/samples/test_sample.mp4` and asserts words are returned.
- **Amplitude** (`test_amplitude.py`) — converts the sample to WAV and checks the RMS is above the silence floor, independently of Vosk.

Python dependencies are managed with **uv** (no pip, no virtualenv commands). The container installs uv and runs `uv sync` automatically on first open, so the environment is ready immediately.

```bash
# Download the Italian Vosk model (~50 MB) and run all tests
make test

# Or run pytest directly (model must already be downloaded)
uv run pytest tests/ -v
```

`make test` downloads the model automatically if it isn't present. The model lands in `tests/models/vosk-model-it/` (gitignored). The MP4 sample is converted to 16 kHz mono WAV by ffmpeg at test time into a temporary directory.

To add a dependency:
```bash
uv add <package>   # updates pyproject.toml and uv.lock
```

If you add more sleep-talking recordings, drop them in `tests/samples/` and add a parametrised test case.

---

## Vosk Speech-to-Text Model

The app uses Vosk for offline speech recognition. Models are not committed to the repo (too large for git).

The active model is set in `gradle.properties`:
```properties
vosk.model=vosk-model-it   # folder name under app/src/main/assets/
```

This value is baked into `BuildConfig.VOSK_MODEL_NAME` at compile time. To switch language, change the property and drop the corresponding folder under `assets/`.

**Download the Italian model (default, ~50 MB):**
```bash
wget https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip -O /tmp/vosk-model.zip
unzip /tmp/vosk-model.zip -d app/src/main/assets/
mv app/src/main/assets/vosk-model-small-it-0.22 app/src/main/assets/vosk-model-it
```

**Switch to English:**
```bash
wget https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip -O /tmp/vosk-model.zip
unzip /tmp/vosk-model.zip -d app/src/main/assets/
mv app/src/main/assets/vosk-model-small-en-us-0.15 app/src/main/assets/vosk-model-en
# then set vosk.model=vosk-model-en in gradle.properties
```

All model folders under `assets/vosk-model-*/` are gitignored — each developer downloads once.

---

## Quick Reference

| Task | Command |
|---|---|
| Build debug APK | `./gradlew assembleDebug` |
| Install on phone | `./gradlew installDebug` |
| View phone logs | `adb logcat -s SleepTalker` |
| Connect to phone | `adb connect <ip>:<port>` |
| Clean build | `./gradlew clean` |
