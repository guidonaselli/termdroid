## Context

El flujo actual divide la preparación del rootfs en Kotlin y la instalación de paquetes en el CLI nativo. El servidor ignora el `Result`, mientras varios wrappers consideran su propia existencia como prueba de instalación. La pipeline de Release reemplaza assets mediante cargas paralelas y una interrupción deja el conjunto incompleto.

## Goals / Non-Goals

**Goals:**

- Un único resultado de instalación de extremo a extremo, observable por terminal.
- Validación basada en ejecutar las herramientas, no solo comprobar archivos.
- Publicación determinística y verificable de todos los artefactos.

**Non-Goals:**

- Añadir nuevas arquitecturas Android.
- Cambiar autenticación, proveedores de IA o permisos del dispositivo.
- Publicar una release nueva antes de validar el arreglo.

## Decisions

### Centralizar la instalación en el servicio Android

El servicio ejecutará la secuencia completa y devolverá progreso, error o éxito; el CLI será solo cliente. Esto elimina el estado dividido actual. Se descarta mantener dos instaladores coordinados porque duplica decisiones y permite continuar tras un fallo.

### Ejecutar Alpine mediante aislamiento de rootfs compatible con Android

Los comandos de Alpine se lanzarán con una raíz y enlaces de sistema coherentes, usando únicamente capacidades empaquetadas o ya disponibles. Invocar solo el linker musl no alcanza para procesos hijos con intérpretes y rutas absolutas. Antes de añadir una dependencia se comprobará si los binarios existentes permiten el aislamiento requerido.

### Validar comandos reales

El éxito exige ejecutar versiones/ayuda de Node, npm y cada CLI. La presencia de wrappers no será señal de instalación y los wrappers no podrán reenviarse a sí mismos.

### Publicar assets de forma secuencial y verificar el conjunto

El workflow construirá y validará los cinco archivos antes de modificar GitHub, serializará por tag y cargará cada asset con reemplazo explícito. Una comprobación final comparará nombres y checksums. Se descarta la carga paralela de la acción actual por el fallo reproducido al reemplazar assets existentes.

## Risks / Trade-offs

- [El aislamiento Alpine puede requerir un binario adicional por ABI] → revisar primero los recursos existentes y fijar su procedencia/versionado si resulta imprescindible.
- [Una instalación completa tarda más] → emitir progreso por etapa y conservar descargas válidas solo cuando puedan verificarse.
- [Reintentar una release existente puede mezclar assets durante segundos] → serializar la operación, validar previamente y verificar el conjunto final antes de éxito.

## Migration Plan

1. Añadir pruebas de contrato que reproduzcan la continuación después del fallo y la recursión de wrappers.
2. Corregir el instalador y validar localmente build/tests.
3. Corregir y validar el workflow con comandos equivalentes locales.
4. Probar instalación en arm64 mediante ADB cuando haya dispositivo.
5. Publicar un tag nuevo; no reutilizar `v0.1.4` como release definitiva.
