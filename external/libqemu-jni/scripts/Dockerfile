# Build with:
#  docker build -t neotty-buildenv .
FROM ubuntu:20.04

# Fix locale to avoid warnings:
ENV LANG C.UTF-8

# Install necessary packages.
ENV DEBIAN_FRONTEND noninteractive
RUN apt update && apt upgrade -yq && \
    apt install -yq --no-install-recommends \
    automake autopoint bison ca-certificates curl flex g++ \
    git gettext gperf libglib2.0-dev libtool-bin m4 make \
    ninja-build pkg-config patch python3.8 sudo tar texinfo \
    unzip xmlto zip

# Setup user.
RUN adduser --disabled-password --shell /bin/bash --gecos "" builder && \
    echo "builder ALL=(root) NOPASSWD:ALL" > /etc/sudoers.d/builder && \
    chmod 0440 /etc/sudoers.d/builder && \
    mkdir -p /data && chown builder:builder /data

# Install NDK toolchain.
RUN curl --fail --retry 3 -o ndk.zip https://dl.google.com/android/repository/android-ndk-r23c-linux.zip && \
    unzip -q ndk.zip && rm -f ndk.zip
COPY ./toolchain /tmp/toolchain
RUN /tmp/toolchain/create-standalone-toolchain.sh android-ndk-r23c /home/builder/ndk-llvm-x86_64 && \
    chown -Rh builder:builder /home/builder/ndk-llvm-x86_64 && \
    rm -rf android-ndk-r23c

# Cleanup.
RUN apt-get clean && rm -rf /var/lib/apt/lists/*

# Finish.
USER builder
RUN mkdir /home/builder/libqemu-jni
WORKDIR /home/builder/libqemu-jni
