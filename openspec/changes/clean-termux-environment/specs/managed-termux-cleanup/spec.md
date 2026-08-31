## Purpose

Permite retirar de forma verificable el entorno aislado que Termdroid administra en Termux, sin afectar instalaciones ni datos ajenos de esa aplicación.

## ADDED Requirements

### Requirement: Entorno de Termux identificable y aislado
El sistema SHALL instalar las dependencias que administra dentro de un entorno de Termux identificable de forma exclusiva por Termdroid. No SHALL reutilizar ni modificar una distribución, lanzador o configuración existente que no esté identificada como administrada por Termdroid.

#### Scenario: Termux tiene una distribución de Debian ajena
- **WHEN** la persona configura Termdroid con una distribución de Debian preexistente en Termux
- **THEN** Termdroid crea o usa solamente su entorno identificado y deja la distribución preexistente sin cambios

### Requirement: Limpieza confirmada del entorno administrado
El sistema SHALL ofrecer una acción visible para eliminar el entorno administrado de Termux. Antes de ejecutarla, SHALL indicar qué componentes administrados se eliminarán y requerirá una confirmación explícita.

#### Scenario: Confirmación de limpieza
- **WHEN** la persona solicita eliminar el entorno de Termux administrado por Termdroid
- **THEN** la interfaz enumera el entorno aislado y sus lanzadores administrados, y no inicia la eliminación hasta que la persona confirme

### Requirement: Preservación de datos no administrados
La limpieza SHALL eliminar exclusivamente componentes identificados como administrados por Termdroid y SHALL conservar Termux, proyectos, credenciales y distribuciones que no pertenezcan a ese entorno.

#### Scenario: Datos propios en Termux
- **WHEN** Termux contiene proyectos o una distribución que no son administrados por Termdroid
- **THEN** ejecutar la limpieza no modifica esos datos ni desinstala Termux

### Requirement: Resultado verificable y manejo de incompatibilidades
El sistema SHALL informar el resultado de la limpieza sólo después de verificar que los componentes administrados ya no existen. Si Termux no está disponible, no concede permiso de ejecución o no contiene un entorno identificado, SHALL explicar que no se realizó ningún borrado.

#### Scenario: No existe entorno administrado
- **WHEN** la persona ejecuta la limpieza sin un entorno Termdroid identificable
- **THEN** la interfaz informa que no había nada seguro para eliminar y no borra archivos de Termux

### Requirement: Transparencia de desinstalación
El sistema SHALL informar que al desinstalar Termdroid Android elimina sus datos privados, mientras que el entorno de Termux requiere usar la acción de limpieza antes de desinstalar Termdroid.

#### Scenario: Información de almacenamiento
- **WHEN** la persona consulta las opciones de limpieza de Termdroid
- **THEN** la interfaz diferencia claramente los datos privados de Termdroid de los componentes administrados dentro de Termux
