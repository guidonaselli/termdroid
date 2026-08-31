## Why

Termdroid puede instalar herramientas oficiales dentro de Termux, una aplicación separada cuyos datos Android no elimina al desinstalar Termdroid. La persona necesita poder retirar explícitamente ese entorno administrado sin borrar proyectos, credenciales u otros datos propios de Termux.

## What Changes

- Incorporar una acción de limpieza desde Termdroid que identifique y elimine únicamente los artefactos que Termdroid administra en Termux.
- Mostrar el alcance exacto de la limpieza y requerir confirmación antes de ejecutarla.
- Mantener Termux, sus proyectos y datos no administrados intactos.
- Informar que desinstalar Termdroid borra sus datos privados, pero no puede ejecutar esta limpieza de Termux automáticamente.

## Capabilities

### New Capabilities

- `managed-termux-cleanup`: eliminación confirmada, acotada y verificable del entorno que Termdroid creó en Termux.

### Modified Capabilities

- Ninguna.

## Impact

- Interfaz de ajustes/herramientas de Termdroid.
- Puente de comandos hacia Termux y el instalador del entorno oficial.
- Documentación de desinstalación y almacenamiento de la aplicación.
