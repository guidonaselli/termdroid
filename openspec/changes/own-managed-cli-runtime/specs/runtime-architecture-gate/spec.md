## Purpose

Evita comprometer una arquitectura de runtime sin evidencia reproducible de que puede sostener los CLI oficiales y las promesas de producto de Termdroid.

## ADDED Requirements

### Requirement: Evaluación comparativa reproducible
El sistema SHALL evaluar, en el mismo dispositivo objetivo, las alternativas de runtime propio Bionic y Linux embebido antes de adoptar una como arquitectura del producto. Cada evaluación SHALL registrar versión de Android, ABI, artefactos, comandos, salida, duración y consumo de almacenamiento.

#### Scenario: Ejecución del gate en un dispositivo objetivo
- **WHEN** se inicia la evaluación de arquitectura
- **THEN** se ejecutan ambas alternativas con el mismo conjunto de comprobaciones y se conserva evidencia comparable

### Requirement: Paridad mínima de CLI oficiales
Una alternativa SHALL superar el gate sólo si instala y ejecuta Node.js, npm, Claude Code y Codex oficiales dentro de Termdroid, permite iniciar sus flujos oficiales de autenticación y ejecutar una operación no interactiva de cada CLI sin depender de Termux.

#### Scenario: Un CLI no alcanza paridad
- **WHEN** cualquiera de los CLI falla por plataforma, binario nativo, autenticación o ejecución real
- **THEN** la alternativa se declara no apta y no avanza a migración de producto

### Requirement: Decisión explícita de salida
El gate SHALL producir una decisión documentada con una alternativa elegida, evidencia de cumplimiento y límites conocidos; si ninguna alternativa cumple, SHALL detener la migración y definir el siguiente experimento antes de construir UX o migraciones permanentes.

#### Scenario: Ninguna alternativa es apta
- **WHEN** las dos alternativas fallan una comprobación obligatoria
- **THEN** no se retira la compatibilidad existente ni se declara un runtime propio listo
