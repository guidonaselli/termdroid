## Purpose

Define la publicación confiable de cada binario de Android y sus checksums para que una release nunca anuncie descargas ausentes o parciales.

## ADDED Requirements

### Requirement: Conjunto completo de artefactos
La pipeline SHALL publicar APK arm64-v8a, APK x86_64, APK universal, AAB y checksums para cada tag de release.

#### Scenario: Publicación exitosa
- **WHEN** finaliza la pipeline de un tag
- **THEN** los cinco artefactos existen en la release y cada checksum corresponde al archivo publicado

### Requirement: Actualización sin estado parcial
La pipeline MUST evitar borrar un conjunto válido antes de disponer del reemplazo completo y SHALL fallar si falta cualquier artefacto esperado.

#### Scenario: Falla una carga
- **WHEN** GitHub rechaza o interrumpe la carga de un artefacto
- **THEN** la pipeline falla con el nombre del artefacto y no declara completa la publicación

### Requirement: Publicación serializable
La pipeline SHALL impedir publicaciones concurrentes para el mismo tag.

#### Scenario: Segundo intento simultáneo
- **WHEN** dos ejecuciones intentan publicar el mismo tag
- **THEN** solo una modifica la release a la vez
