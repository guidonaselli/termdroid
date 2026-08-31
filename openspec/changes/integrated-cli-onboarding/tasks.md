## 1. Estado verificable

- [x] 1.1 Añadir una comprobación de versiones de las cuatro herramientas y verificar sus resultados unitariamente
- [x] 1.2 Modelar los estados de requisito pendiente, preparación, error y listo, y verificar sus transiciones unitariamente

## 2. Experiencia integrada

- [x] 2.1 Sustituir el aviso de configuración por una sección de herramientas con acción principal, progreso, error recuperable y versiones verificadas; verificar compilación de la interfaz
- [x] 2.2 Añadir acciones claras para abrir Claude Code y Codex, explicando la transición a Termux; verificar que invocan las sesiones oficiales

## 3. Verificación y entrega

- [x] 3.1 Ejecutar tests y ensamblar un APK release, verificando que el build termina correctamente
- [x] 3.2 Probar por ADB los estados listo y de apertura de ambos CLI en arm64, verificando salida y pantalla real
- [x] 3.3 Publicar un tag y comprobar CI, assets y checksums de la release
