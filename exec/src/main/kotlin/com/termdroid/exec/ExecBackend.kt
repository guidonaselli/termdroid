package com.termdroid.exec

/** Como este device puede ejecutar binarios. */
enum class ExecBackend {
    /** `execve` directo sobre filesDir. Devices donde el sepolicy no tiene la regla. */
    DIRECT,

    /** Solo se ejecuta lo que viaja en el APK, desde `nativeLibraryDir`. */
    NATIVE_LIB_DIR,

    /** `execve` del linker con el binario como argumento. Habilita instalar en runtime. */
    LINKER,

    /** Nada ejecuta. La app funciona sin shell y lo dice. */
    NONE;

    /** Si se pueden instalar y correr binarios nuevos despues del build. */
    val supportsRuntimeInstall: Boolean
        get() = this == DIRECT || this == LINKER
}
