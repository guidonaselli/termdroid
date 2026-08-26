#!/usr/bin/env bash
# Produce los binarios de rootfs/prebuilt/ que viajan en el APK.
#
# Requiere: rustup con los targets de Android, y el NDK del SDK.
#   rustup target add aarch64-linux-android x86_64-linux-android
set -euo pipefail

NDK_VERSION="${NDK_VERSION:-27.2.12479018}"
SDK="${ANDROID_HOME:-D:/Android/Sdk}"
HOST="${NDK_HOST:-windows-x86_64}"
NDK_BIN="$SDK/ndk/$NDK_VERSION/toolchains/llvm/prebuilt/$HOST/bin"

RIPGREP_VERSION="14.1.1"
OUT="$(cd "$(dirname "$0")" && pwd)/prebuilt"
WORK="${RUST_BUILD_DIR:-$(dirname "$OUT")/.rust-build}"

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_BIN/aarch64-linux-android26-clang.cmd"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$NDK_BIN/x86_64-linux-android26-clang.cmd"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_AR="$NDK_BIN/llvm-ar.exe"
export CARGO_TARGET_X86_64_LINUX_ANDROID_AR="$NDK_BIN/llvm-ar.exe"

build() {
  local target="$1" abi="$2"
  mkdir -p "$OUT/$abi"

  cargo install ripgrep \
    --version "$RIPGREP_VERSION" \
    --target "$target" \
    --root "$WORK/$abi" \
    --no-default-features \
    --force

  # El nombre lib*.so es lo que hace que el packager lo extraiga a
  # nativeLibraryDir, la unica ruta ejecutable garantizada.
  cp "$WORK/$abi/bin/rg" "$OUT/$abi/librg.so"
  "$NDK_BIN/llvm-strip.exe" --strip-all "$OUT/$abi/librg.so"
}

build aarch64-linux-android arm64-v8a
build x86_64-linux-android x86_64

ls -la "$OUT"/*/
