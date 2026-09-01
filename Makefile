.PHONY: test test-python test-unit download-model emulator-setup emulator

# ── Python / Vosk tests ───────────────────────────────────────────────────────

MODEL_DIR = tests/models/vosk-model-it
MODEL_ZIP = /tmp/vosk-model-it.zip
MODEL_URL = https://alphacephei.com/vosk/models/vosk-model-small-it-0.22.zip

download-model:
	@if [ -d "$(MODEL_DIR)" ]; then \
		echo "Model already present at $(MODEL_DIR)"; \
	else \
		echo "Downloading Italian Vosk model..."; \
		wget -q $(MODEL_URL) -O $(MODEL_ZIP); \
		mkdir -p tests/models; \
		unzip -q $(MODEL_ZIP) -d tests/models; \
		mv tests/models/vosk-model-small-it-0.22 $(MODEL_DIR); \
		echo "Model ready at $(MODEL_DIR)"; \
	fi

test-python: download-model
	uv run pytest tests/ -v

# ── Kotlin / JVM unit tests ───────────────────────────────────────────────────

test-unit:
	./gradlew test

# ── Run all tests ─────────────────────────────────────────────────────────────

test: test-python test-unit

# ── Android Emulator ──────────────────────────────────────────────────────────

AVD_NAME    = SleepTalkerAVD
SYSTEM_IMG  = system-images;android-35;google_apis;x86_64

emulator-setup:
	sdkmanager "$(SYSTEM_IMG)"
	@if ! avdmanager list avd | grep -q "$(AVD_NAME)"; then \
		echo "Creating AVD $(AVD_NAME)..."; \
		echo no | avdmanager create avd -n $(AVD_NAME) -k "$(SYSTEM_IMG)" --force; \
	else \
		echo "AVD $(AVD_NAME) already exists"; \
	fi

emulator: emulator-setup
	emulator -avd $(AVD_NAME) -no-audio &
