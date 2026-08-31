## Purpose

Provee un entorno de terminal y CLI que Termdroid instala, ejecuta, actualiza y elimina íntegramente, sin exigir que otra aplicación sea parte del flujo normal.

## ADDED Requirements

### Requirement: Runtime propiedad de Termdroid
El sistema SHALL instalar y ejecutar el runtime seleccionado, sus datos, configuraciones y terminal desde el espacio administrado por Termdroid. El flujo normal SHALL funcionar sin que Termux esté instalado.

#### Scenario: Instalación nueva sin Termux
- **WHEN** una persona instala Termdroid en un dispositivo sin Termux
- **THEN** puede preparar y abrir el runtime seleccionado desde Termdroid sin instalar otra aplicación

### Requirement: Terminal integrada con paridad seleccionada
El sistema SHALL abrir Claude Code y Codex en la terminal integrada de Termdroid usando el runtime que aprobó el gate, y SHALL mostrar un estado recuperable si el runtime no está listo.

#### Scenario: Apertura de Codex
- **WHEN** la persona elige abrir Codex desde Termdroid
- **THEN** la sesión se inicia en la terminal de Termdroid y no cambia a otra aplicación

### Requirement: Ciclo de vida administrado y reversible
El sistema SHALL permitir reparar, actualizar y eliminar el runtime propio con confirmación. La eliminación SHALL borrar el runtime, credenciales locales asociadas, cachés y logs administrados por Termdroid, y SHALL verificar el resultado.

#### Scenario: Eliminación del runtime propio
- **WHEN** la persona confirma la eliminación del entorno de Termdroid
- **THEN** el sistema elimina sólo sus datos administrados y confirma que el espacio dejó de existir

### Requirement: Migración segura desde Termux
El sistema SHALL detectar la instalación heredada de Termux sin leer, copiar, modificar ni eliminar sus proyectos o credenciales automáticamente. SHALL ofrecer mantenerla o configurar el runtime propio mediante una decisión explícita.

#### Scenario: Termux ya contiene una sesión autenticada
- **WHEN** Termdroid detecta una instalación de Termux existente
- **THEN** informa que esos datos permanecen fuera de Termdroid y no los toca durante la configuración del runtime propio
