FROM mcr.microsoft.com/devcontainers/base:debian

ARG JAVA_VERSION=25.0.2
ARG CMDLINE_TOOLS_VERSION=14742923

ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV ANDROID_HOME=${ANDROID_SDK_ROOT}
ENV SDKMAN_DIR=/opt/.sdkman

SHELL ["/bin/bash", "-c"]

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl \
        unzip \
        zip \
        ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN curl -fsSL "https://get.sdkman.io" | bash \
    && source "${SDKMAN_DIR}/bin/sdkman-init.sh" \
    && sdk install java ${JAVA_VERSION}-tem \
    && sdk flush tmp

ENV PATH="${SDKMAN_DIR}/candidates/java/current/bin:${ANDROID_SDK_ROOT}/cmdline-tools/latest/bin:${PATH}"

RUN mkdir -p ${ANDROID_SDK_ROOT}/cmdline-tools \
    && curl -fsSL \
        "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
        -o /tmp/cmdline-tools.zip \
    && unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-extracted \
    && mv /tmp/cmdline-tools-extracted/cmdline-tools ${ANDROID_SDK_ROOT}/cmdline-tools/latest \
    && rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-extracted

WORKDIR /workspace
