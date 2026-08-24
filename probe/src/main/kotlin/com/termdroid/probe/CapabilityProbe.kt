package com.termdroid.probe

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.SystemClock
import com.termdroid.exec.ExecBackend
import com.termdroid.exec.ExecEnvironment
import com.termdroid.exec.Executor
import com.termdroid.exec.PtySession
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Mide que puede hacer este device y elige el backend de ejecucion. */
class CapabilityProbe(private val context: Context) {

    private val env = ExecEnvironment(context)

    /** Devuelve lo cacheado si sigue siendo valido; si no, mide de nuevo. */
    fun get(): DeviceCapabilities = cached() ?: run().also { store(it) }

    fun run(): DeviceCapabilities {
        val started = SystemClock.elapsedRealtime()
        val failures = mutableListOf<String>()

        val packaged = env.packaged(PROBE_BIN)
        val staged = stage(packaged, failures)

        val nativeLibDirExec = packaged.exists() &&
            tryExec(ExecBackend.DIRECT, packaged, "nativelibdir", failures)

        // Sin binario en filesDir no se pueden medir los niveles 0 ni 2, pero eso
        val directExec = staged != null && tryExec(ExecBackend.DIRECT, staged, "directo", failures)
        val linkerExec = staged != null && env.linker.exists() &&
            tryExec(ExecBackend.LINKER, staged, "linker", failures)

        val backend = when {
            directExec -> ExecBackend.DIRECT
            linkerExec -> ExecBackend.LINKER
            nativeLibDirExec -> ExecBackend.NATIVE_LIB_DIR
            else -> ExecBackend.NONE
        }

        val pty = if (backend == ExecBackend.NONE) false else tryPty(failures)

        return DeviceCapabilities(
            backend = backend,
            directExec = directExec,
            nativeLibDirExec = nativeLibDirExec,
            linkerExec = linkerExec,
            pty = pty,
            // La depuracion inalambrica, que es lo que permite el canal UID shell
            wirelessDebuggingPossible = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R,
            abi = Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            sdkInt = Build.VERSION.SDK_INT,
            manufacturer = Build.MANUFACTURER,
            model = Build.MODEL,
            totalRamMb = totalRamMb(),
            probeMillis = SystemClock.elapsedRealtime() - started,
            failures = failures,
        )
    }

    /** Copia el binario del probe a filesDir: ahi es `app_data_file` y aplica la restriccion. */
    private fun stage(packaged: File, failures: MutableList<String>): File? {
        if (!packaged.exists()) {
            failures += "no se encontro ${packaged.name} en nativeLibraryDir (revisar useLegacyPackaging)"
            return null
        }
        return runCatching {
            val dir = File(context.filesDir, "probe").apply { mkdirs() }
            File(dir, PROBE_BIN).also {
                packaged.copyTo(it, overwrite = true)
                it.setExecutable(true, false)
            }
        }.getOrElse {
            failures += "no se pudo copiar el binario a filesDir: $it"
            null
        }
    }

    private fun tryExec(
        backend: ExecBackend,
        file: File,
        label: String,
        failures: MutableList<String>,
    ): Boolean {
        val result = runCatching { Executor(env, backend).run(file, listOf(label)) }
            .getOrElse {
                failures += "exec $label lanzo $it"
                return false
            }
        if (!result.output.contains(PROBE_MARKER)) {
            failures += "exec $label: ${result.output.trim().take(120)}"
            return false
        }
        return true
    }

    private fun tryPty(failures: MutableList<String>): Boolean {
        var session: PtySession? = null
        return try {
            session = PtySession.start(
                argv = listOf(SYSTEM_SH),
                env = mapOf("PATH" to "/system/bin", "TERM" to "dumb"),
                cwd = context.filesDir,
            )
            session.write("echo $PTY_MARKER\nexit\n")
            val out = StringBuilder()
            val buf = ByteArray(2048)
            val deadline = SystemClock.elapsedRealtime() + PTY_TIMEOUT_MS
            while (SystemClock.elapsedRealtime() < deadline) {
                val n = session.read(buf)
                if (n <= 0) break
                out.append(String(buf, 0, n))
                if (out.contains(PTY_MARKER)) break
            }
            val ok = out.contains(PTY_MARKER)
            if (!ok) failures += "pty: no llego el marcador"
            ok
        } catch (t: Throwable) {
            failures += "pty: $t"
            false
        } finally {
            session?.close()
        }
    }

    private fun totalRamMb(): Long {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return 0
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return info.totalMem / (1024 * 1024)
    }

    // --- cache -----------------------------------------------------------

    private fun prefs() = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cacheKey(): String {
        val version = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode
        }.getOrDefault(0L)
        return "$version|${Build.FINGERPRINT}"
    }

    private fun cached(): DeviceCapabilities? {
        val raw = prefs().getString(cacheKey(), null) ?: return null
        return runCatching {
            val o = JSONObject(raw)
            val fails = o.optJSONArray("failures") ?: JSONArray()
            DeviceCapabilities(
                backend = ExecBackend.valueOf(o.getString("backend")),
                directExec = o.getBoolean("directExec"),
                nativeLibDirExec = o.getBoolean("nativeLibDirExec"),
                linkerExec = o.getBoolean("linkerExec"),
                pty = o.getBoolean("pty"),
                wirelessDebuggingPossible = o.getBoolean("wireless"),
                abi = o.getString("abi"),
                sdkInt = o.getInt("sdkInt"),
                manufacturer = o.getString("manufacturer"),
                model = o.getString("model"),
                totalRamMb = o.getLong("ram"),
                probeMillis = o.getLong("probeMillis"),
                failures = (0 until fails.length()).map { fails.getString(it) },
            )
        }.getOrNull()
    }

    private fun store(caps: DeviceCapabilities) {
        val o = JSONObject()
            .put("backend", caps.backend.name)
            .put("directExec", caps.directExec)
            .put("nativeLibDirExec", caps.nativeLibDirExec)
            .put("linkerExec", caps.linkerExec)
            .put("pty", caps.pty)
            .put("wireless", caps.wirelessDebuggingPossible)
            .put("abi", caps.abi)
            .put("sdkInt", caps.sdkInt)
            .put("manufacturer", caps.manufacturer)
            .put("model", caps.model)
            .put("ram", caps.totalRamMb)
            .put("probeMillis", caps.probeMillis)
            .put("failures", JSONArray(caps.failures))
        prefs().edit().clear().putString(cacheKey(), o.toString()).apply()
    }

    private companion object {
        const val PROBE_BIN = "tdprobe"
        const val PROBE_MARKER = "TDPROBE_OK"
        const val PTY_MARKER = "TDPTY_OK"
        const val PTY_TIMEOUT_MS = 1200L
        const val SYSTEM_SH = "/system/bin/sh"
        const val PREFS = "termdroid_capabilities"
    }
}
