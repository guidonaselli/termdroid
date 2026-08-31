## MODIFIED Requirements

### Requirement: Instalación por etapas con resultado real
El sistema SHALL verificar Termux, preparar Node.js y npm, instalar los CLI oficiales y validar los ejecutables como etapas ordenadas. SHALL detenerse ante el primer fallo y comunicar un error no vacío al cliente.

#### Scenario: Falla la preparación del entorno delegado
- **WHEN** la disponibilidad de Termux, su autorización o un comando de preparación falla
- **THEN** el cliente recibe el fallo y no intenta ejecutar las etapas posteriores

#### Scenario: Instalación completa
- **WHEN** todas las etapas terminan correctamente en Termux
- **THEN** `node`, `npm`, `claude` y `codex` pueden ejecutarse en Termux y reportar una versión o ayuda válida

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
