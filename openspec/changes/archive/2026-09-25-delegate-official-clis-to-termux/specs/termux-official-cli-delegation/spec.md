## Purpose

Permitir que Termdroid use los CLI oficiales de Claude Code y Codex en un entorno Android compatible, conservando sus flujos de autenticación de suscripción.

## ADDED Requirements

### Requirement: Delegación autorizada a Termux
El sistema SHALL detectar una instalación compatible de Termux antes de delegar comandos. MUST solicitar la autorización de ejecución requerida y MUST mostrar una instrucción recuperable cuando Termux, el permiso o la configuración externa no estén disponibles.

#### Scenario: Termux no está instalado
- **WHEN** el usuario inicia la configuración de CLI sin Termux compatible
- **THEN** Termdroid informa que debe instalar Termux antes de continuar y no informa una instalación correcta

#### Scenario: Autorización pendiente
- **WHEN** Termux está instalado pero no permite que Termdroid ejecute comandos
- **THEN** Termdroid muestra los pasos para otorgar el permiso y habilitar la ejecución externa

### Requirement: Ejecución de CLI oficiales
El sistema SHALL delegar la inicialización, la validación y los comandos de Claude Code y Codex a Termux. MUST preservar la salida, el código de salida y los errores relevantes para mostrarlos al usuario.

#### Scenario: Instalación y validación correctas
- **WHEN** Termux ejecuta la preparación y las herramientas oficiales devuelven versiones o ayuda válida
- **THEN** Termdroid informa éxito únicamente después de recibir resultados correctos para Node.js, npm, Claude Code y Codex

#### Scenario: Fallo de un comando delegado
- **WHEN** un comando iniciado en Termux devuelve un código de salida distinto de cero
- **THEN** Termdroid muestra el fallo recibido y no declara que el entorno esté listo

### Requirement: Autenticación oficial de suscripción
El sistema SHALL iniciar `claude login` y `codex login` en la sesión de Termux. MUST NOT sustituir esos flujos por una consola propia ni capturar credenciales de suscripción en Termdroid.

#### Scenario: Inicio de sesión de Claude Code
- **WHEN** el usuario solicita autenticar Claude Code
- **THEN** Termdroid abre la sesión oficial de Termux con `claude login`

#### Scenario: Inicio de sesión de Codex
- **WHEN** el usuario solicita autenticar Codex
- **THEN** Termdroid abre la sesión oficial de Termux con `codex login`
