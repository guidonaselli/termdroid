## Why

La instalación de Node.js y los CLI puede continuar después de que falle la preparación de Alpine, ocultando la causa real y dejando un entorno parcial. Además, la publicación de una etiqueta existente reemplaza assets en paralelo y puede dejar una release incompleta cuando GitHub corta alguna carga.

## What Changes

- Hacer que la preparación del entorno comunique éxito o error al cliente antes de instalar paquetes.
- Ejecutar e inspeccionar la instalación de Node.js, npm y los CLI dentro de un entorno Alpine funcional.
- Mantener estados parciales como fallo recuperable y permitir reintentos determinísticos.
- Publicar APK arm64-v8a, x86_64, universal, AAB y checksums sin dejar una release parcialmente reemplazada.
- Añadir comprobaciones automatizadas para los contratos del instalador y de los artefactos de release.

## Capabilities

### New Capabilities

- `runtime-environment-installation`: Instalación observable y verificable del entorno Alpine, Node.js, npm y los CLI soportados.
- `release-artifact-publication`: Publicación confiable y completa de los binarios versionados y sus checksums.

### Modified Capabilities


## Impact

Afecta `rootfs`, el servidor local de instalación, el ejecutable CLI, las pruebas y el workflow de Release. No cambia el `targetSdk`, el modelo de permisos ni las interfaces públicas del agente.
