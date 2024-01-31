# Native executables build environment

A Docker-based build environment for `libqemu-jni.so` \- the QEMU package. It
is patched for use as JNI library in order to avoid the need of `execve()`
system call which is restricted by security hardening on Android 10+.

# How to build the JNI library

You need `docker` to be installed and running.

1. Create a build environment:
  ```
  cd ./scripts/
  docker build -t neotty-buildenv .
  cd ..
  ```
2. Start the container:
  ```
  ./scripts/run-docker.sh
  ```
3. Build the library for all architectures:
  ```
  ./build-package.sh -f -a all qemu-system
  ```

The output is placed into `./jniLibs`. Note that there are prebuilt
libraries available.
