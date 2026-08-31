## 1. Gate de arquitectura

- [ ] 1.1 Definir el manifiesto reproducible de versiones, fuentes, licencias y casos obligatorios de Node, npm, Claude Code y Codex; verificar que el harness ejecuta cada caso y conserva salida, duración y tamaño.
- [ ] 1.2 Construir un bootstrap Bionic mínimo con prefijo de Termdroid desde el fork de paquetes; verificar en arm64 que no contiene el prefijo de Termux y que Node/npm ejecutan desde la terminal integrada.
- [ ] 1.3 Ejecutar Claude Code y Codex oficiales sobre el candidato Bionic, incluyendo autenticación y una operación no interactiva; verificar resultados reales o registrar el fallo reproducible por plataforma.
- [ ] 1.4 Empaquetar un Linux/PRoot mínimo dentro de un APK de Termdroid con targetSdk actual; verificar instalación, inicio de shell, background, reinicio y borrado sin Termux.
- [ ] 1.5 Ejecutar Claude Code y Codex oficiales sobre el candidato Linux embebido, incluyendo autenticación y una operación no interactiva; verificar resultados reales o registrar el fallo reproducible.
- [ ] 1.6 Comparar ambos candidatos en A56 arm64 y emulador x86_64, registrar métricas y crear la ADR de selección o bloqueo; verificar que sólo un candidato apto avanza.

## 2. Runtime seleccionado

- [ ] 2.1 Implementar el bootstrap por ABI y el instalador reanudable del backend elegido; verificar una instalación nueva, una reparación y una actualización sin aplicaciones externas.
- [ ] 2.2 Conectar el backend elegido al PTY y emulador de terminal existentes; verificar comandos, tamaño de terminal, background y recuperación de sesión dentro de Termdroid.
- [ ] 2.3 Integrar Node, npm, Claude Code y Codex oficiales con estado verificable; verificar versiones, inicio interactivo y una operación no interactiva de cada CLI.
- [ ] 2.4 Implementar autenticación oficial y almacenamiento local con permisos restrictivos; verificar login, reinicio, logout y eliminación sin leer ni exponer tokens.

## 3. Ciclo de vida y migración

- [ ] 3.1 Implementar reparar, actualizar y eliminar el runtime administrado con confirmación y verificación posterior; verificar que desaparecen runtime, logs, cachés y credenciales de Termdroid.
- [ ] 3.2 Implementar detección y migración explícita desde Termux; verificar que una instalación con proyectos y sesión autenticada no se modifica sin consentimiento.
- [ ] 3.3 Retirar la configuración automática de Termux sólo detrás de la paridad aprobada; verificar instalación limpia sin Termux, migración y reversión a la ruta de legado.

## 4. Calidad de entrega

- [ ] 4.1 Ejecutar la matriz de compatibilidad, seguridad, rendimiento y almacenamiento en los ABI soportados; verificar evidencia de cada criterio de aceptación y degradación recuperable.
- [ ] 4.2 Actualizar documentación, onboarding, scripts de release y verificadores para reflejar el runtime seleccionado; verificar build, tests, release candidata y desinstalación completa en dispositivo.
