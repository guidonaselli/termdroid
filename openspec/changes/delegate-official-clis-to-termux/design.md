## Context

El sandbox de la aplicación bloquea los procesos Linux requeridos por los paquetes oficiales. Termux ejecuta un userland Android/Bionic separado y expone `RUN_COMMAND` para que aplicaciones autorizadas puedan iniciar comandos y recibir resultados.

## Goals / Non-Goals

**Goals:**

- Ejecutar Node.js, npm, Claude Code y Codex oficiales en Termux.
- Preservar `claude login` y `codex login` oficiales.
- Mostrar en Termdroid resultados, errores y requisitos de configuración de Termux.

**Non-Goals:**

- Embeber o forkear el bootstrap completo de Termux en Termdroid.
- Capturar o transformar credenciales de suscripción.
- Mantener wrappers que simulen los CLI oficiales.

## Decisions

### Delegar comandos mediante RUN_COMMAND

Termdroid enviará comandos a `com.termux.app.RunCommandService` y recibirá resultado mediante `PendingIntent`. El adaptador centralizará la detección de Termux, el permiso, la ejecución y el resultado para que las pantallas y la terminal no repliquen contratos.

### Instalar una vez y validar cada herramienta

La preparación ejecutará un script idempotente en Termux que instala `proot-distro`, crea Debian sólo cuando falta e instala allí Node.js, npm, Claude Code y Codex. Después ejecutará las versiones de las cuatro herramientas dentro de Debian y devolverá cada resultado. Un código de salida distinto de cero interrumpe el flujo.

### Mantener OAuth en la sesión de Termux

Los comandos de login se lanzarán en una sesión visible de Termux, donde los CLI oficiales controlan su navegador, callbacks y archivos de autenticación. Termdroid no leerá ni escribirá tokens de suscripción de Termux.

### Requisitos explícitos y recuperables

Antes de iniciar comandos, Termdroid comprobará que Termux está instalado. Ante ausencia de permiso o de `allow-external-apps`, mostrará pasos concretos y no ofrecerá un falso éxito.

## Risks / Trade-offs

- [Termux requiere instalación y autorización única] → Termdroid explica y verifica cada requisito antes de instalar.
- [La sesión de login cambia a Termux] → se abre explícitamente una sesión visible y Termdroid conserva el estado de la solicitud.
- [La salida de comandos puede ser grande] → usar resultados de background para validaciones breves y sesión foreground para login interactivo.

## Migration Plan

1. Retirar el runtime embebido experimental y sus assets.
2. Añadir el adaptador Termux, permisos y detección.
3. Conectar instalación, validación y wrappers al adaptador.
4. Probar los flujos reales por ADB en arm64, incluida autenticación manual.
5. Publicar sólo después de confirmar las versiones y los dos flujos oficiales.
