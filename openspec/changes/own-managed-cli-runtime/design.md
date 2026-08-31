## Context

La integración actual de Termux comprobó que los CLI oficiales funcionan en un Linux glibc bajo PRoot, pero deja el producto dividido entre dos aplicaciones. La alternativa Bionic propia ya estaba prevista en F-002, aunque el ecosistema actual de los CLI presenta riesgos específicos de Android: Codex resuelve paquetes Linux desde Android y Claude Code puede requerir módulos nativos no publicados para Android.

Además, un prefijo distinto al de Termux no puede consumir sus repositorios binarios: las dependencias deben construirse y distribuirse para el prefijo y paquete de Termdroid. El costo de mantenimiento es real y debe entrar en la decisión, no quedar oculto en la implementación.

## Goals / Non-Goals

**Goals:**

- Decidir el runtime final con evidencia de ejecución, no por afinidad con una tecnología.
- Mantener una sola propiedad de producto sobre terminal, datos, reparación y eliminación.
- Evitar migrar o reimplementar UX antes de confirmar los CLI oficiales.

**Non-Goals:**

- Forkear la aplicación Termux completa como primera opción.
- Adaptar, emular o declarar compatibles versiones no oficiales de Claude Code o Codex.
- Importar automáticamente credenciales, proyectos o configuraciones desde Termux.

## Decisions

### Gate antes de la implementación de producto

El primer entregable es un spike descartable, no un nuevo runtime. Evalúa dos candidatos en arm64 real y un emulador x86_64:

1. Bionic propio construido desde un fork mínimo de `termux-packages` con prefijo de Termdroid.
2. Linux glibc/PRoot empaquetado y lanzado por Termdroid, sin la app Termux.

Los dos candidatos usan el mismo protocolo: bootstrap, Node, npm, instalación de los CLI oficiales, versión, inicio de autenticación, operación no interactiva, operación interactiva, reinicio, reparación, borrado, tiempo, tamaño y estado tras volver de background.

Se descarta elegir Bionic por defecto: tener Node no prueba que los binarios y módulos de los CLI soporten Android. También se descarta elegir PRoot por defecto: que funcione a través de Termux no prueba que pueda ejecutarse con el targetSdk y los procesos propios de Termdroid.

### Criterio de selección estricto

Una alternativa avanza sólo si completa todas las pruebas obligatorias en el A56 y no requiere otra aplicación. Si ambas pasan, se elige la que minimice mantenimiento, tamaño instalado, tiempo de preparación y fricción. Si ninguna pasa, se abre una decisión separada para portar o solicitar soporte upstream de los componentes que fallen; no se construye una "Termdroid 2.0" sobre supuestos.

### Propiedad y fronteras de datos

El runtime elegido vive únicamente en almacenamiento privado de Termdroid. El terminal integrado es su único host de sesiones. Los tokens oficiales quedan donde el CLI oficial los espera dentro de ese runtime, protegidos por permisos de aplicación; la app no los interpreta ni los migra desde Termux.

La compatibilidad de Termux se mantiene temporalmente como ruta explícita de legado. La eliminación del runtime propio no instala, desinstala ni toca Termux.

### Entregas por capas después del gate

Después de la decisión hay cuatro entregas independientes: bootstrap reproducible; terminal y ciclo de vida; CLI y autenticación; migración y retiro de Termux. Cada entrega tiene una prueba real previa al siguiente corte. El adaptador de Termux sólo se retira tras la última prueba de paridad.

## Risks / Trade-offs

- [Codex o Claude Code no soportan Android Bionic] → El gate lo detecta antes de construir el runtime; evaluar Linux embebido o trabajo upstream.
- [PRoot falla con targetSdk moderno o degrada la experiencia] → Validarlo en el APK de Termdroid, con background y reinstalación; no extrapolar desde Termux.
- [Prefijo propio obliga a mantener repositorio de paquetes] → Bootstrap pequeño, versiones bloqueadas, SBOM/licencias y actualizaciones controladas.
- [Un runtime Linux embebido aumenta tamaño y tiempo de primera preparación] → Descarga bajo demanda, progreso reanudable y presupuesto explícito por ABI.
- [Migrar sesiones puede exponer secretos] → No se copian automáticamente; cada CLI ejecuta su autenticación oficial dentro del nuevo runtime.

## Migration Plan

1. Mantener la versión actual como compatibilidad y etiquetarla temporal.
2. Ejecutar el gate y registrar la decisión en la vault antes de modificar el flujo principal.
3. Construir el runtime elegido detrás de una bandera interna, sin cambiar las instalaciones existentes.
4. Probar instalación nueva, reparación, actualizaciones, background, borrado y CLI en dispositivo.
5. Ofrecer migración explícita; conservar Termux sin modificaciones.
6. Retirar la configuración automática de Termux sólo cuando la paridad sea verificable en releases soportadas.
