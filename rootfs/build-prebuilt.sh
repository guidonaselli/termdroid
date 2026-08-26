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

OUT="$(cd "$(dirname "$0")" && pwd)/prebuilt"
WORK="${RUST_BUILD_DIR:-$(dirname "$OUT")/.rust-build}"

export CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER="$NDK_BIN/aarch64-linux-android26-clang.cmd"
export CARGO_TARGET_X86_64_LINUX_ANDROID_LINKER="$NDK_BIN/x86_64-linux-android26-clang.cmd"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_AR="$NDK_BIN/llvm-ar.exe"
export CARGO_TARGET_X86_64_LINUX_ANDROID_AR="$NDK_BIN/llvm-ar.exe"

# cc-rs no lee las variables de cargo: busca el compilador por su cuenta.
export CC_aarch64_linux_android="$NDK_BIN/aarch64-linux-android26-clang.cmd"
export CC_x86_64_linux_android="$NDK_BIN/x86_64-linux-android26-clang.cmd"
export AR_aarch64_linux_android="$NDK_BIN/llvm-ar.exe"
export AR_x86_64_linux_android="$NDK_BIN/llvm-ar.exe"
export CFLAGS_aarch64_linux_android="--target=aarch64-linux-android26"
export CFLAGS_x86_64_linux_android="--target=x86_64-linux-android26"

# gitoxide queda afuera a proposito: compila y corre, pero su TLS en Android va
# por rustls-platform-verifier, que se inicializa desde la JVM. Un binario suelto
# no tiene JVM y `gix clone` sobre https entra en panic. Son 26 MB por un git que
# no puede clonar.
#
# crate:version:binario:features
HERRAMIENTAS=(
  "ripgrep:14.1.1:rg:"
  "jaq:2.1.0:jaq:"
)

instalar() {
  local crate="$1" version="$2" binario="$3" features="$4" target="$5" abi="$6"
  local extra=()
  [ -n "$features" ] && extra=(--no-default-features --features "$features")

  cargo install "$crate" \
    --version "$version" \
    --target "$target" \
    --root "$WORK/$abi" \
    "${extra[@]}" \
    --force

  mkdir -p "$OUT/$abi"
  # El nombre lib*.so es lo que hace que el packager lo extraiga a
  # nativeLibraryDir, la unica ruta ejecutable garantizada.
  cp "$WORK/$abi/bin/$binario" "$OUT/$abi/lib$binario.so"
  "$NDK_BIN/llvm-strip.exe" --strip-all "$OUT/$abi/lib$binario.so"
}

for entrada in "${HERRAMIENTAS[@]}"; do
  IFS=':' read -r crate version binario features <<< "$entrada"
  instalar "$crate" "$version" "$binario" "$features" aarch64-linux-android arm64-v8a
  instalar "$crate" "$version" "$binario" "$features" x86_64-linux-android x86_64
done

ls -la "$OUT"/*/
