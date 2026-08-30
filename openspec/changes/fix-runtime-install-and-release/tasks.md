## 1. Reproducción automatizada

- [x] 1.1 Añadir una prueba que demuestre que un fallo al preparar Alpine se devuelve al cliente y evita la instalación de paquetes; verificar que falla con la implementación actual
- [x] 1.2 Añadir pruebas para detectar wrappers recursivos y estados parciales considerados instalados; verificar que fallan con la implementación actual
- [x] 1.3 Añadir una comprobación de artefactos esperados y checksums de release; verificarla contra fixtures completos e incompletos

## 2. Instalación del entorno

- [x] 2.1 Unificar en el servicio Android la preparación, instalación y validación, y verificar las pruebas de propagación de errores
- [x] 2.2 Ejecutar comandos Alpine con una raíz coherente en arm64-v8a y x86_64, y verificar `apk --version` en el harness disponible
- [x] 2.3 Corregir wrappers y detección de instalación para evitar autorreferencia y falsos positivos; verificar Node, npm, Claude y Codex mediante comandos reales
- [x] 2.4 Hacer que reintentar repare el estado parcial y verificar dos ejecuciones consecutivas del escenario de instalación

## 3. Pipeline de release

- [x] 3.1 Serializar ejecuciones por tag y reemplazar la carga paralela por una publicación controlada; verificar sintaxis y comandos del workflow
- [x] 3.2 Validar los cinco archivos y checksums antes y después de publicar; verificar que un archivo ausente falla antes de modificar la release

## 4. Verificación integral

- [x] 4.1 Ejecutar tests unitarios y builds debug/release locales, verificando que se generan APKs arm64-v8a, x86_64, universal y AAB
- [ ] 4.2 Ejecutar el flujo completo por ADB en un dispositivo arm64-v8a y verificar versiones de Node, npm, Claude y Codex
- [x] 4.3 Publicar un tag nuevo y verificar que la pipeline queda verde y la release contiene los cinco artefactos con checksums válidos
