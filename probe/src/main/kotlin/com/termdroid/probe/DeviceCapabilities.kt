package com.termdroid.probe

import com.termdroid.exec.ExecBackend

/**
 * Lo que este device concreto puede hacer, medido y no supuesto.
 *
 * Cada campo que sea `false` tiene que poder explicarse al usuario: la UI dice
 * que falta y que se pierde, nunca esconde la funcion en silencio.
 * Ver 10_TECH/COMPATIBILITY.md.
 */
data class DeviceCapabilities(
    val backend: ExecBackend,
    val directExec: Boolean,
    val nativeLibDirExec: Boolean,
    val linkerExec: Boolean,
    val pty: Boolean,
    val wirelessDebuggingPossible: Boolean,
    val abi: String,
    val sdkInt: Int,
    val manufacturer: String,
    val model: String,
    val totalRamMb: Long,
    val probeMillis: Long,
    val failures: List<String>,
) {
    /** Se pueden instalar paquetes despues del build (Node, el CLI, todo lo demas). */
    val canInstallPackages: Boolean get() = backend.supportsRuntimeInstall

    /** Hay shell utilizable. Sin esto la app sigue abriendo, pero sin terminal. */
    val hasShell: Boolean get() = backend != ExecBackend.NONE && pty

    /** Memoria justa para sesiones largas: se avisa, no se bloquea. */
    val lowMemory: Boolean get() = totalRamMb in 1..3584

    fun summary(): String = buildString {
        appendLine("backend            = $backend")
        appendLine("exec directo       = $directExec")
        appendLine("exec nativeLibDir  = $nativeLibDirExec")
        appendLine("exec linker        = $linkerExec")
        appendLine("pty                = $pty")
        appendLine("adb inalambrico    = $wirelessDebuggingPossible")
        appendLine("instalar paquetes  = $canInstallPackages")
        appendLine("device             = $manufacturer $model (API $sdkInt, $abi, ${totalRamMb}MB)")
        appendLine("probe              = ${probeMillis}ms")
        if (failures.isNotEmpty()) {
            appendLine("fallos:")
            failures.forEach { appendLine("  - $it") }
        }
    }
}
