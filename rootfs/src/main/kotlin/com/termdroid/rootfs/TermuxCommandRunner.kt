package com.termdroid.rootfs

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

data class TermuxCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val error: String,
)

object TermuxCommandRunner {
    private const val TERMUX_PACKAGE = "com.termux"
    private const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    private const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    private const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    private const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    private const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    private const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    private const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"
    private const val EXTRA_RESULT_BUNDLE = "result"
    private const val EXTRA_STDOUT = "stdout"
    private const val EXTRA_STDERR = "stderr"
    private const val EXTRA_EXIT_CODE = "exitCode"
    private const val EXTRA_ERROR = "errmsg"

    private val nextId = AtomicInteger()
    private val callbacks = ConcurrentHashMap<Int, (TermuxCommandResult) -> Unit>()

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0)
    }.isSuccess

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission("com.termux.permission.RUN_COMMAND") == PackageManager.PERMISSION_GRANTED

    suspend fun run(context: Context, script: String): TermuxCommandResult = suspendCancellableCoroutine { continuation ->
        checkReady(context)
        val requestId = nextId.incrementAndGet()
        callbacks[requestId] = { result -> if (continuation.isActive) continuation.resume(result) }
        continuation.invokeOnCancellation { callbacks.remove(requestId) }
        val callback = PendingIntent.getBroadcast(
            context,
            requestId,
            Intent(context, TermuxCommandResultReceiver::class.java).putExtra("requestId", requestId),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
        )
        runCatching {
            context.startService(commandIntent(arrayOf("-lc", script), true).putExtra(EXTRA_PENDING_INTENT, callback))
        }.onFailure {
            callbacks.remove(requestId)
            if (continuation.isActive) continuation.resume(TermuxCommandResult(1, "", "", it.message.orEmpty()))
        }
    }

    fun openCli(context: Context, command: String, arguments: List<String> = emptyList()): Result<Unit> = runCatching {
        checkReady(context)
        context.startService(commandIntent(arrayOf("-lc", "exec ${'$'}PREFIX/bin/proot-distro login debian -- $command \"${'$'}@\"", "--") + arguments, false))
        context.startActivity(
            Intent().setClassName(TERMUX_PACKAGE, "com.termux.app.TermuxActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    fun readinessError(installed: Boolean, permissionGranted: Boolean): String? = when {
        !installed -> "Termux no esta instalado. Instala Termux y completá su configuración inicial."
        !permissionGranted -> "Termdroid no tiene permiso para ejecutar comandos en Termux."
        else -> null
    }

    private fun checkReady(context: Context) {
        readinessError(isInstalled(context), hasPermission(context))?.let(::error)
    }

    private fun commandIntent(arguments: Array<String>, background: Boolean) = Intent(ACTION_RUN_COMMAND).apply {
        setClassName(TERMUX_PACKAGE, "com.termux.app.RunCommandService")
        putExtra(EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
        putExtra(EXTRA_ARGUMENTS, arguments)
        putExtra(EXTRA_WORKDIR, "/data/data/com.termux/files/home")
        putExtra(EXTRA_BACKGROUND, background)
        if (!background) putExtra(EXTRA_SESSION_ACTION, "0")
    }

    internal fun deliver(requestId: Int, intent: Intent) {
        val bundle = intent.getBundleExtra(EXTRA_RESULT_BUNDLE)
        callbacks.remove(requestId)?.invoke(
            TermuxCommandResult(
                exitCode = bundle?.getInt(EXTRA_EXIT_CODE, 1) ?: 1,
                stdout = bundle?.getString(EXTRA_STDOUT).orEmpty(),
                stderr = bundle?.getString(EXTRA_STDERR).orEmpty(),
                error = bundle?.getString(EXTRA_ERROR).orEmpty(),
            ),
        )
    }
}
