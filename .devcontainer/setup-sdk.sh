#!/usr/bin/env bash
set -e

echo "Accepting Android SDK licenses..."
yes | sdkmanager --licenses > /dev/null 2>&1 || true

echo "Installing Android SDK components..."
sdkmanager \
    "platform-tools" \
    "platforms;android-36" \
    "build-tools;36.0.0" \
    "emulator"

echo "Android SDK setup complete."
sdkmanager --list_installed

echo "Installing Python dependencies via uv..."
cd /workspaces/sleep_talker_recorder
uv sync
echo "Python environment ready."
