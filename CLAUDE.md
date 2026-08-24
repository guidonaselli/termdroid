# Termdroid

App Android que da shell real y un agente Claude con acceso de primera clase al dispositivo.

## Diseño

El diseño canónico y el tracking no viven en el repo. Antes de tocar código, leer:

- Arquitectura, modelo de ejecución, agent loop, UX, seguridad, compatibilidad, onboarding
- Decisiones (ADRs) y planning por feature

## Invariantes que no se negocian

1. **`targetSdk` moderno.** Bajarlo es el problema que este proyecto existe para evitar.
2. **`useLegacyPackaging = true`.** Sin eso los binarios quedan comprimidos en el APK y el nivel 1
   de ejecución no tiene ruta que ejecutar.
3. **Detectar, no asumir.** Ninguna capacidad se decide por `Build.VERSION` ni por lista de devices:
   la decide el capability probe en runtime.
4. **Degradar, no romper.** Si falta una capacidad, la app sigue abriendo y lo explica.
5. **Cero fricción.** Ningún permiso se pide al arrancar. El primer frame útil es el chat.

## Build

```
./gradlew assembleDebug          # todos los módulos
./gradlew :spike:assembleDebug   # app de experimentos de F-001
```

Requiere `local.properties` con `sdk.dir`. NDK r27c, compileSdk 37, minSdk 26.
