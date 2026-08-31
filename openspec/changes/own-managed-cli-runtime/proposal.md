## Why

La delegación actual a Termux permite ejecutar los CLI, pero rompe la promesa de una sola aplicación: la terminal, los datos, la autenticación y la desinstalación quedan repartidos entre dos productos. Termdroid debe poseer el runtime que presenta al usuario, sin asumir que un userland Bionic o Linux embebido soporta los CLI oficiales hasta haberlo probado.

## What Changes

- Reemplazar Termux como dependencia de ejecución final por un runtime administrado dentro de Termdroid.
- Ejecutar un gate técnico comparativo entre un runtime Bionic propio y un runtime Linux/PRoot embebido, contra Claude Code y Codex oficiales.
- Construir sólo la alternativa que supere los criterios de compatibilidad, seguridad, ciclo de vida y UX definidos.
- **BREAKING** Retirar la configuración automática de Termux una vez que el runtime propio alcance paridad validada.
- Mantener la instalación de Termux existente como compatibilidad transitoria, sin modificar ni borrar sus datos.

## Capabilities

### New Capabilities

- `managed-cli-runtime`: runtime, terminal y ciclo de vida íntegramente administrados por Termdroid para ejecutar los CLI oficiales.
- `runtime-architecture-gate`: evaluación reproducible que decide el backend propio antes de implementar una migración.

### Modified Capabilities

- Ninguna.

## Impact

- Módulos `:rootfs`, `:exec`, `:terminal`, `:app` y pipeline de build/release.
- Infraestructura de paquetes y distribución por ABI.
- Onboarding, autenticación oficial, almacenamiento, actualización, reparación y desinstalación.
- La integración actual con Termux queda explícitamente fuera de la arquitectura objetivo.
