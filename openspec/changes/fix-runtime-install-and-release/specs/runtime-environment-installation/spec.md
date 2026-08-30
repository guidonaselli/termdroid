## Purpose

Define el comportamiento observable para instalar y reintentar un entorno Linux con Node.js, npm y los CLI oficiales dentro del sandbox de Termdroid.

## ADDED Requirements

### Requirement: Instalación por etapas con resultado real
El sistema SHALL preparar el rootfs, instalar paquetes y validar los ejecutables como etapas ordenadas. SHALL detenerse ante el primer fallo y comunicar un error no vacío al cliente.

#### Scenario: Falla la preparación del rootfs
- **WHEN** la descarga, extracción o configuración del rootfs falla
- **THEN** el cliente recibe el fallo y no intenta ejecutar el gestor de paquetes

#### Scenario: Instalación completa
- **WHEN** todas las etapas terminan correctamente
- **THEN** `node`, `npm`, `claude` y `codex` pueden ejecutarse y reportar una versión o ayuda válida

### Requirement: Reintento seguro
El sistema SHALL poder reintentar una instalación incompleta sin informar éxito por la sola presencia de wrappers o archivos parciales.

#### Scenario: Entorno parcial previo
- **WHEN** el usuario repite la instalación después de un fallo
- **THEN** el sistema repara o reemplaza el estado parcial y vuelve a validar todas las herramientas

### Requirement: Compatibilidad por ABI detectada
El sistema MUST seleccionar únicamente recursos compatibles con una ABI soportada y SHALL fallar explícitamente ante una ABI no soportada.

#### Scenario: ABI no soportada
- **WHEN** el dispositivo no ofrece arm64-v8a ni x86_64
- **THEN** la instalación termina con un mensaje de incompatibilidad antes de descargar recursos
