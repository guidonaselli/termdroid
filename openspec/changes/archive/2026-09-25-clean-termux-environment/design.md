## Context

Termdroid ejecuta la instalación de las herramientas oficiales mediante el servicio de comandos de Termux. La implementación actual usa la distribución genérica `debian` y reemplaza los comandos `claude` y `codex` en el prefijo de Termux; ambas decisiones impiden saber con certeza qué puede eliminarse de forma segura.

## Goals / Non-Goals

**Goals:**

- Separar por nombre y marcador persistente el entorno administrado por Termdroid.
- Limpiar únicamente esa frontera, con confirmación y verificación posterior.
- No sobrescribir lanzadores ni reutilizar la distribución genérica de Termux.

**Non-Goals:**

- Desinstalar Termux, borrar almacenamiento compartido o limpiar instalaciones creadas fuera de Termdroid.
- Ejecutar limpieza al desinstalar Termdroid: Android no permite un gancho confiable de desinstalación para borrar datos de otra aplicación.

## Decisions

### Distribución dedicada y marcador de propiedad

La instalación creará una distribución PRoot con identificador exclusivo de Termdroid y un marcador dentro de su raíz. Los lanzadores administrados usarán nombres propios de Termdroid o se guardarán bajo un directorio de propiedad de Termdroid, sin reemplazar ejecutables existentes del usuario.

Esto hace que la eliminación pueda delegarse al gestor de distribuciones de Termux contra un único identificador. Se descarta detectar la distribución genérica `debian` por sus paquetes o archivos: esas señales no prueban propiedad y pueden borrar datos del usuario.

### Limpieza con frontera estricta

La acción solicitará a Termux una rutina de limpieza que primero valide el marcador y, sólo entonces, elimine la distribución dedicada, el directorio de lanzadores administrado y los archivos de registro o estado de Termdroid. La rutina fallará sin modificar nada ante marcador ausente o comandos no disponibles.

La app mostrará una confirmación descriptiva, ejecutará la rutina en segundo plano y comprobará el estado final antes de informar éxito. Se descarta un borrado directo por rutas arbitrarias desde la interfaz porque aumenta el riesgo de alcance incorrecto.

### Compatibilidad del entorno actual

Las instalaciones existentes que usan `debian` no se marcarán ni eliminarán automáticamente. La interfaz las tratará como no administradas y ofrecerá reinstalar en el entorno dedicado; la persona podrá limpiar manualmente la instalación anterior si decide que le pertenece.

Esto prioriza no perder datos. El costo es que una instalación previa no obtiene limpieza automática retroactiva.

## Risks / Trade-offs

- [La instalación dedicada consume espacio adicional durante la migración] → La interfaz advierte el alcance y no toca la distribución anterior.
- [Una versión de Termux sin soporte para la distribución dedicada impide configurar el entorno] → La validación falla antes de modificar una distribución existente y muestra la causa.
- [La persona desinstala Termdroid sin limpiar Termux] → La documentación y la pantalla de limpieza lo explicitan; Android elimina los datos privados de Termdroid, pero no puede limpiar datos de otra app tras la desinstalación.

## Migration Plan

1. Publicar la instalación aislada para nuevas configuraciones.
2. Detectar instalaciones heredadas sin marcador y no modificarlas.
3. Ofrecer reinstalación explícita al entorno dedicado.
4. Verificar instalación, limpieza y preservación de una distribución genérica durante la validación en dispositivo.
