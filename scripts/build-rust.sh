#!/usr/bin/env bash
# Builds rust-core into Android .so libraries under app/src/main/jniLibs.
# Requires: rustup targets (aarch64-linux-android, armv7-linux-androideabi, x86_64-linux-android),
#           cargo-ndk, and the Android NDK (ANDROID_NDK_HOME).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"

: "${ANDROID_NDK_HOME:=${ANDROID_NDK_LATEST_HOME:-}}"
export ANDROID_NDK_HOME
if [ -z "$ANDROID_NDK_HOME" ]; then
  echo "ANDROID_NDK_HOME is not set" >&2
  exit 1
fi

cd "$ROOT/rust-core"
cargo test --lib
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86_64 -o "$ROOT/app/src/main/jniLibs" build --release
ls -R "$ROOT/app/src/main/jniLibs"
