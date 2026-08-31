## Why

La instalación de los CLI oficiales ya es automática, pero el flujo cotidiano expone Termux demasiado pronto. Termdroid debe ser el punto de entrada claro para preparar, comprobar y abrir cada herramienta sin ocultar ni reemplazar los flujos de autenticación oficiales.

## What Changes

- Añadir un recorrido de incorporación en Termdroid con requisitos, progreso, resultado y reintento.
- Mostrar el estado real de Node.js, npm, Claude Code y Codex después de la instalación.
- Ofrecer acciones directas para abrir Claude Code o Codex, usando Termux sólo para las sesiones oficiales interactivas.
- Diferenciar la configuración necesaria de la terminal avanzada, evitando presentar Termux como un paso obligatorio para las tareas normales.

## Capabilities

### New Capabilities
- `integrated-cli-onboarding`: configuración, estado y accesos a los CLI oficiales desde la interfaz de Termdroid.

### Modified Capabilities
- Ninguna.

## Impact

Afecta la pantalla principal de Android, el adaptador de comandos de Termux, las pruebas de interfaz y la verificación por ADB. No añade dependencias ni cambia el almacenamiento de credenciales oficiales.
