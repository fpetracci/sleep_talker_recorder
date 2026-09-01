FROM mcr.microsoft.com/devcontainers/base:ubuntu-24.04

# System tools
RUN apt-get update && apt-get install -y \
    wget unzip curl git usbutils ffmpeg \
    && rm -rf /var/lib/apt/lists/*

# uv — Python package manager
COPY --from=ghcr.io/astral-sh/uv:latest /uv /uvx /usr/local/bin/

# Avoid hardlink issues on bind-mounted workspaces
ENV UV_LINK_MODE=copy

# Android SDK directory
RUN mkdir -p /opt/android-sdk/cmdline-tools

# Download Android command-line tools (latest stable)
RUN wget -q https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    -O /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-raw \
    && mv /tmp/cmdline-tools-raw/cmdline-tools /opt/android-sdk/cmdline-tools/latest \
    && rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-raw

ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV PATH="${PATH}:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:/opt/android-sdk/emulator"
