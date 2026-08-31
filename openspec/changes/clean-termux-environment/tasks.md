## 1. Entorno administrado aislado

- [x] 1.1 Cambiar la instalación y validación de herramientas para usar una distribución y directorio de lanzadores exclusivos de Termdroid, con marcador de propiedad; verificar con pruebas unitarias de scripts y validación de versiones.
- [x] 1.2 Detectar instalaciones heredadas sin marcador y no modificarlas; verificar que la configuración informa la migración requerida sin ejecutar borrados.

## 2. Limpieza segura

- [x] 2.1 Implementar la rutina de inspección y eliminación que valide el marcador antes de retirar sólo la distribución, lanzadores y estado administrados; verificar resultados exitosos, ausencia de entorno y error de permisos.
- [x] 2.2 Añadir la acción de limpieza con confirmación, detalle de alcance y resultado verificable en la interfaz; verificar la interacción mediante pruebas de UI o estado y compilación Android.
- [x] 2.3 Explicar en la interfaz que la desinstalación de Termdroid borra sus datos privados pero requiere limpiar Termux previamente; verificar el texto en el flujo de limpieza.

## 3. Validación en dispositivo

- [x] 3.1 Ejecutar la instalación aislada, comprobar que una distribución Debian ajena no cambia, y ejecutar la limpieza en un dispositivo real; verificar que Termux permanece instalado y que sólo desaparecen los artefactos administrados.
