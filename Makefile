.PHONY: test test-python test-unit download-model install

# ── Build & install ───────────────────────────────────────────────────────────

install:
	./gradlew assembleDebug
	adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk

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

