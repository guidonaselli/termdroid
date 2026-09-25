## Why

El sandbox de Termdroid en Android no puede ejecutar de forma fiable el runtime Linux que requieren los CLI oficiales. Los reemplazos nativos internos no implementan el flujo OAuth de suscripción de Claude Code ni Codex, por lo que no son equivalentes ni aceptables para el usuario.

Termux ofrece un entorno Android compatible con Node.js y una API de ejecución explícitamente autorizada para aplicaciones de terceros. Delegar allí la instalación y ejecución conserva los CLI oficiales y sus flujos de autenticación.

## What Changes

- Reemplazar el runtime Linux embebido experimental por un adaptador de Termux.
- Detectar la disponibilidad, versión mínima y autorización de Termux antes de ejecutar comandos.
- Inicializar Node.js, npm, Claude Code y Codex oficiales mediante Termux y mostrar el resultado verificable en Termdroid.
- Abrir las sesiones oficiales de `claude login` y `codex login` dentro de Termux, sin sustituirlas por autenticación propia.
- Informar pasos de configuración recuperables cuando falte Termux o sus permisos.

## Capabilities

### New Capabilities

- `termux-official-cli-delegation`: Delegación autorizada de instalación, ejecución y autenticación de los CLI oficiales a Termux.

### Modified Capabilities

- `runtime-environment-installation`: La validación de Node.js, npm y los CLI se realiza en Termux, no dentro del sandbox de Termdroid.

## Impact

Afecta el instalador del runtime, los wrappers de terminal, el manifiesto Android, la UX de configuración y las pruebas ADB. Requiere Termux compatible instalado y una autorización explícita del usuario para ejecutar comandos en su entorno.
