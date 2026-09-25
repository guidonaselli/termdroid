## Context

Termdroid ya puede aprovisionar y ejecutar los CLI oficiales en el entorno Debian de Termux, pero la pantalla principal sólo muestra un texto de estado. La API autorizada de Termux devuelve resultados finitos y no proporciona una PTY interactiva embebible.

## Goals / Non-Goals

**Goals:**

- Hacer visible en Termdroid el estado verificable de la configuración.
- Llevar al usuario desde una acción principal a instalar, validar y abrir cada CLI.
- Explicar una sola vez por qué una sesión oficial se abre en Termux.

**Non-Goals:**

- Embeber una terminal PTY de Termux.
- Interceptar OAuth, credenciales o tokens de proveedores.
- Duplicar los CLI oficiales en el sandbox de la aplicación.

## Decisions

### Pantalla de herramientas como punto de entrada

La pantalla principal mostrará una sección dedicada a las herramientas oficiales con una jerarquía por estado: preparar, error recuperable o listas. Esto concentra las decisiones operativas en Termdroid en lugar de requerir comandos manuales. La alternativa de abrir Termux de entrada mantiene el problema actual.

### Validación breve al abrir la aplicación

Termdroid consultará las cuatro versiones mediante el adaptador existente cuando el entorno ya esté preparado. Una única operación de validación ofrece evidencia actual sin inferir el estado desde preferencias. La alternativa de confiar sólo en la marca local puede mostrar una instalación rota como lista.

### Termux como sesión externa explícita

Los botones de Claude Code y Codex abrirán la sesión oficial interactiva de Termux y la interfaz indicará ese traspaso. Esto preserva la compatibilidad con OAuth y las capacidades reales de terminal. La alternativa de ocultar esa transición crearía una promesa que la API no puede cumplir.

## Risks / Trade-offs

- [La comprobación breve puede tardar] → mostrar un estado de verificación no bloqueante y conservar la última información válida hasta completarla.
- [Termux puede ser desinstalado o revocar permisos] → volver a mostrar el requisito pendiente y no las acciones de CLI.
- [El usuario espera una terminal embebida] → comunicar que Termdroid gestiona el entorno y Termux aloja únicamente la sesión oficial avanzada.

## Migration Plan

1. Añadir el modelo de estado y la validación de versiones.
2. Sustituir el aviso textual por la sección de herramientas guiada.
3. Probar requisitos, éxito, error y los accesos oficiales en un dispositivo real.
4. Publicar un APK y verificar la actualización sobre una instalación existente.
