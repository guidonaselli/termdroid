## Purpose

Permitir que Termdroid sea el punto de entrada completo y verificable para preparar y acceder a los CLI oficiales, reservando Termux para sus sesiones interactivas oficiales.

## ADDED Requirements

### Requirement: Incorporación guiada desde Termdroid
El sistema SHALL mostrar en Termdroid el estado de los requisitos, la instalación y el resultado final de Claude Code y Codex. MUST ofrecer una acción de reintento cuando una etapa falle.

#### Scenario: Entorno aún no preparado
- **WHEN** el usuario abre Termdroid y falta un requisito o una herramienta
- **THEN** la aplicación muestra el requisito pendiente y una única acción clara para continuarlo

#### Scenario: Configuración completada
- **WHEN** la instalación y la validación de las herramientas terminan correctamente
- **THEN** la aplicación muestra sus versiones verificadas y acciones para abrir cada CLI

#### Scenario: Configuración fallida
- **WHEN** una etapa de configuración devuelve un error
- **THEN** la aplicación muestra el error recuperable y permite reintentar la configuración

### Requirement: Acceso oficial con contexto claro
El sistema SHALL iniciar las sesiones oficiales interactivas de Claude Code y Codex desde acciones etiquetadas en Termdroid. MUST comunicar antes de abrirlas que la interacción y la autenticación ocurren en Termux.

#### Scenario: Abrir Claude Code
- **WHEN** el usuario selecciona abrir Claude Code desde Termdroid
- **THEN** la aplicación abre la sesión oficial correspondiente y conserva visible en Termdroid que la herramienta se ejecuta mediante Termux

#### Scenario: Abrir Codex
- **WHEN** el usuario selecciona abrir Codex desde Termdroid
- **THEN** la aplicación abre la sesión oficial correspondiente y conserva visible en Termdroid que la herramienta se ejecuta mediante Termux
