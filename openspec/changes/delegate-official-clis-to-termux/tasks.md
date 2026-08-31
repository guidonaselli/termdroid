## 1. Retiro del runtime experimental

- [x] 1.1 Eliminar el runtime Node embebido, binarios y wrappers que no ejecutan los CLI oficiales
- [x] 1.2 Mantener el instalador sin declarar éxito mientras Termux no haya validado las herramientas reales

## 2. Adaptador Termux

- [x] 2.1 Declarar visibilidad y permiso `RUN_COMMAND`, e implementar detección de Termux y requisitos de autorización
- [x] 2.2 Implementar la ejecución de comandos con resultado mediante `RunCommandService` y `PendingIntent`
- [x] 2.3 Implementar el script idempotente para instalar Node.js, npm, Claude Code y Codex en Termux, y validar sus versiones
- [x] 2.4 Conectar los comandos de terminal y login a sesiones oficiales visibles de Termux

## 3. Verificación

- [x] 3.1 Añadir pruebas unitarias para estados de Termux ausente, permiso pendiente, éxito y error delegado
- [x] 3.2 Ejecutar build y tests locales, verificando que no quedan assets del runtime experimental
- [ ] 3.3 Probar por ADB en arm64: instalación real, cuatro versiones y `claude login`/`codex login` en Termux
- [ ] 3.4 Publicar un tag nuevo y validar pipeline, assets y checksums
