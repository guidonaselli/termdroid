# Entorno de desarrollo

## Instalado en esta maquina

- Android SDK: `D:\Android\Sdk` (platform-tools r37, platforms android-36 y android-37.0,
  build-tools 36/37, NDK r27c, CMake 3.22.1, emulator)
- Gradle: `D:\Android\gradle\gradle-9.7.1` (el repo tiene wrapper, no hace falta)
- JDK 17 (Temurin), ya estaba

## Emuladores

```
D:\Android\Sdk\emulator\emulator.exe -avd td36   # Android 16, API 36
D:\Android\Sdk\emulator\emulator.exe -avd td28   # Android 9,  API 28
```

Agregar `-no-window -no-audio -no-boot-anim -gpu swiftshader_indirect` para correrlo headless.

## Comandos

```
./gradlew assembleDebug                    # todos los modulos
./gradlew testDebugUnitTest                # tests JVM (nucleo VT)
./gradlew connectedDebugAndroidTest        # tests en device o emulador
./gradlew :spike:assembleDebug             # app de experimentos de F-001
```

APKs en `app/build/outputs/apk/debug/` (uno por ABI mas el universal).

## Para probar en el A56

Depuracion inalambrica -> emparejar -> `adb connect` -> `./gradlew :app:installDebug`.
El capability probe deja su veredicto en logcat con el tag de la app.

## Binarios del rootfs

`rootfs/prebuilt/` no está en git: se genera con

```
rustup target add aarch64-linux-android x86_64-linux-android
bash rootfs/build-prebuilt.sh
```

Sin eso la app compila igual; el tool `grep` cae al recorrido en Kotlin.
