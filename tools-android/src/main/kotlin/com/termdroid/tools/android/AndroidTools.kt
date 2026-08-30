package com.termdroid.tools.android

import android.app.ActivityManager
import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.provider.Settings
import com.termdroid.adb.AdbClient
import com.termdroid.agent.AgentTool
import com.termdroid.agent.ToolOutcome
import com.termdroid.agent.ToolRisk
import com.termdroid.agent.ToolSpec
import com.termdroid.agent.intProp
import com.termdroid.agent.objectSchema
import com.termdroid.agent.stringProp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Acceso especial que Android concede desde Ajustes, no con un dialogo. */
enum class SpecialAccess(val label: String) {
    USAGE_STATS("Acceso al uso de apps"),
    NOTIFICATIONS("Acceso a las notificaciones"),
    ;

    fun isGranted(context: Context): Boolean = when (this) {
        USAGE_STATS -> {
            val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            @Suppress("DEPRECATION")
            val mode = ops.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
            mode == AppOpsManager.MODE_ALLOWED
        }

        NOTIFICATIONS -> NotificationLog.isEnabled(context)
    }

    fun settingsIntent(): Intent = when (this) {
        USAGE_STATS -> Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
        NOTIFICATIONS -> Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }
}

/** Tools que leen el propio Android. */
class AndroidToolset(
    private val context: Context,
    private val adbHost: String = "127.0.0.1",
    private val adbPort: Int = 5555,
    private val onAccessNeeded: (SpecialAccess) -> Unit = {},
) {
    init {
        NotificationLog.attach(context)
    }

    fun all(): List<AgentTool> =
        listOf(
            ListAppsTool(),
            AppUsageTool(),
            DeviceStateTool(),
            NotificationsTool(),
            ShellPrivTool(),
            ClipboardTool(),
            BatteryInfoTool(),
            TtsTool(),
            VibrateTool(),
        )

    private fun requireAccess(access: SpecialAccess): ToolOutcome? {
        if (access.isGranted(context)) return null
        onAccessNeeded(access)
        return ToolOutcome(
            "Falta el permiso '${access.label}'. Se concede una sola vez desde Ajustes; " +
                "la app ya le ofrecio al usuario abrirlo. Sin eso este dato no esta disponible.",
            isError = true,
        )
    }

    private inner class ListAppsTool : AgentTool {
        override val spec = ToolSpec(
            name = "list_apps",
            description = "Lista las apps instaladas en el telefono.",
            inputSchema = objectSchema(
                "include_system" to stringProp("'si' para incluir las apps del sistema. Por defecto no."),
                required = emptyList(),
            ),
        )
        override val risk = ToolRisk.READ

        override fun describe(input: JSONObject) = "listar apps instaladas"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            runCatching {
                val includeSystem = input.optString("include_system").equals("si", ignoreCase = true)
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                    .filter { includeSystem || (it.flags and ApplicationInfo.FLAG_SYSTEM) == 0 }
                    .map { JSONObject().put("package", it.packageName).put("label", pm.getApplicationLabel(it).toString()) }
                    .sortedBy { it.getString("label").lowercase() }

                ToolOutcome(
                    JSONObject()
                        .put("total", apps.size)
                        .put("apps", JSONArray(apps))
                        .toString(2),
                )
            }.getOrElse { ToolOutcome("No se pudieron listar las apps: $it", isError = true) }
        }
    }

    private inner class AppUsageTool : AgentTool {
        override val spec = ToolSpec(
            name = "app_usage",
            description = "Tiempo de uso por app en los ultimos dias, de mayor a menor.",
            inputSchema = objectSchema(
                "days" to intProp("Cuantos dias hacia atras. Por defecto 7."),
                "limit" to intProp("Cuantas apps devolver. Por defecto 20."),
                required = emptyList(),
            ),
        )
        override val risk = ToolRisk.READ

        override fun describe(input: JSONObject) =
            "uso de apps de los ultimos ${input.optInt("days", 7)} dias"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            requireAccess(SpecialAccess.USAGE_STATS)?.let { return@withContext it }

            runCatching {
                val days = input.optInt("days", 7).coerceIn(1, 90)
                val limit = input.optInt("limit", 20).coerceIn(1, 200)
                val end = System.currentTimeMillis()
                val begin = end - TimeUnit.DAYS.toMillis(days.toLong())

                val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
                val stats = usm.queryAndAggregateUsageStats(begin, end)
                val pm = context.packageManager

                val rows = stats.values
                    .filter { it.totalTimeInForeground > 0 }
                    .sortedByDescending { it.totalTimeInForeground }
                    .take(limit)
                    .map { s ->
                        val label = runCatching {
                            pm.getApplicationLabel(pm.getApplicationInfo(s.packageName, 0)).toString()
                        }.getOrDefault(s.packageName)
                        JSONObject()
                            .put("package", s.packageName)
                            .put("label", label)
                            .put("minutes", s.totalTimeInForeground / 60_000)
                            .put("last_used", s.lastTimeUsed)
                    }

                if (rows.isEmpty()) {
                    return@runCatching ToolOutcome(
                        "No hay datos de uso en los ultimos $days dias.",
                    )
                }

                ToolOutcome(
                    JSONObject()
                        .put("days", days)
                        .put("apps", JSONArray(rows))
                        .toString(2),
                )
            }.getOrElse { ToolOutcome("No se pudo leer el uso: $it", isError = true) }
        }
    }

    private inner class NotificationsTool : AgentTool {
        override val spec = ToolSpec(
            name = "notifications",
            description = "Notificaciones recientes que llegaron al telefono, de la mas nueva a la mas vieja.",
            inputSchema = objectSchema(
                "limit" to intProp("Cuantas devolver. Por defecto 20."),
                "package_filter" to stringProp("Filtra por nombre de paquete."),
                required = emptyList(),
            ),
        )
        override val risk = ToolRisk.READ

        override fun describe(input: JSONObject) = "leer notificaciones recientes"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            requireAccess(SpecialAccess.NOTIFICATIONS)?.let { return@withContext it }

            val limit = input.optInt("limit", 20).coerceIn(1, 200)
            val filtro = input.optString("package_filter").takeIf { it.isNotBlank() }
            val recientes = NotificationLog.recent(limit, filtro)

            if (recientes.isEmpty()) {
                return@withContext ToolOutcome(
                    "No hay notificaciones registradas todavia. Solo se ven las que llegan " +
                        "desde que se concedio el permiso.",
                )
            }

            ToolOutcome(
                JSONObject()
                    .put("total", recientes.size)
                    .put(
                        "notifications",
                        JSONArray(
                            recientes.map {
                                JSONObject()
                                    .put("package", it.packageName)
                                    .put("title", it.title)
                                    .put("text", it.text)
                                    .put("posted_at", it.postedAt)
                            },
                        ),
                    )
                    .toString(2),
            )
        }
    }

    private inner class DeviceStateTool : AgentTool {
        override val spec = ToolSpec(
            name = "device_state",
            description = "Estado del telefono: bateria, memoria, almacenamiento y version.",
            inputSchema = objectSchema(required = emptyList()),
        )
        override val risk = ToolRisk.READ

        override fun describe(input: JSONObject) = "estado del telefono"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            runCatching {
                val bm = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                val mem = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
                val fs = StatFs(context.filesDir.absolutePath)

                ToolOutcome(
                    JSONObject()
                        .put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
                        .put("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                        .put("abi", Build.SUPPORTED_ABIS.firstOrNull().orEmpty())
                        .put("battery_percent", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                        .put("ram_total_mb", mem.totalMem / (1024 * 1024))
                        .put("ram_available_mb", mem.availMem / (1024 * 1024))
                        .put("ram_low", mem.lowMemory)
                        .put("storage_free_mb", fs.availableBytes / (1024 * 1024))
                        .toString(2),
                )
            }.getOrElse { ToolOutcome("No se pudo leer el estado: $it", isError = true) }
        }
    }

    private inner class ShellPrivTool : AgentTool {
        override val spec = ToolSpec(
            name = "shell_priv",
            description = "Ejecuta un comando con permisos de UID shell (2000) a traves del canal ADB embebido.",
            inputSchema = objectSchema(
                "command" to stringProp("Comando privilegiado a ejecutar."),
                required = listOf("command"),
            ),
        )
        override val risk = ToolRisk.PRIVILEGED

        override fun describe(input: JSONObject) = "adb shell: ${input.optString("command")}"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            val cmd = input.optString("command").ifBlank {
                return@withContext ToolOutcome("Falta 'command'.", isError = true)
            }
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                return@withContext ToolOutcome(
                    "El canal ADB embebido requiere Android 11 o superior (depuracion inalambrica). " +
                        "En este device (API ${Build.VERSION.SDK_INT}) no esta disponible sin una PC.",
                    isError = true,
                )
            }
            runCatching {
                val client = AdbClient(host = adbHost, port = adbPort)
                val res = client.execute(cmd)
                ToolOutcome(res.output.ifBlank { "[sin salida]" }, isError = res.isError)
            }.getOrElse {
                ToolOutcome(
                    "No se pudo conectar con el canal ADB ($adbHost:$adbPort). " +
                        "Verifica que la depuracion inalambrica este activa: $it",
                    isError = true,
                )
            }
        }
    }

    private inner class ClipboardTool : AgentTool {
        override val spec = ToolSpec(
            name = "clipboard",
            description = "Lee o escribe texto en el portapapeles del sistema.",
            inputSchema = objectSchema(
                "action" to stringProp("'get' para leer, 'set' para escribir. Por defecto 'get'."),
                "text" to stringProp("Texto a copiar al portapapeles si action es 'set'."),
                required = emptyList(),
            ),
        )
        override val risk = ToolRisk.WRITE

        override fun describe(input: JSONObject): String {
            val act = input.optString("action", "get").lowercase()
            return if (act == "set") "copiar texto al portapapeles" else "leer portapapeles"
        }

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.Main) {
            runCatching {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val act = input.optString("action", "get").lowercase()
                if (act == "set") {
                    val text = input.optString("text")
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("termdroid", text))
                    ToolOutcome("Texto copiado al portapapeles con exito.")
                } else {
                    val clip = cm.primaryClip
                    val text = if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(context).toString() else ""
                    ToolOutcome(JSONObject().put("clipboard", text).toString(2))
                }
            }.getOrElse { ToolOutcome("No se pudo acceder al portapapeles: $it", isError = true) }
        }
    }

    private inner class BatteryInfoTool : AgentTool {
        override val spec = ToolSpec(
            name = "battery_info",
            description = "Informacion detallada de la bateria: porcentaje, estado de carga, fuente de alimentacion, salud y temperatura.",
            inputSchema = objectSchema(required = emptyList()),
        )
        override val risk = ToolRisk.READ

        override fun describe(input: JSONObject) = "informacion de la bateria"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            runCatching {
                val ifilter = android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val statusIntent = context.registerReceiver(null, ifilter)

                val level = statusIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = statusIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val pct = if (scale > 0 && level >= 0) (level * 100) / scale else -1

                val status = statusIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val statusStr = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "cargando"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "descargando"
                    BatteryManager.BATTERY_STATUS_FULL -> "llena"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "no_cargando"
                    else -> "desconocido"
                }

                val plugged = statusIntent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
                val plugStr = when (plugged) {
                    BatteryManager.BATTERY_PLUGGED_AC -> "ac"
                    BatteryManager.BATTERY_PLUGGED_USB -> "usb"
                    BatteryManager.BATTERY_PLUGGED_WIRELESS -> "inalambrico"
                    else -> "bateria"
                }

                val temp = (statusIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10.0
                val health = statusIntent?.getIntExtra(BatteryManager.EXTRA_HEALTH, -1) ?: -1
                val healthStr = when (health) {
                    BatteryManager.BATTERY_HEALTH_GOOD -> "buena"
                    BatteryManager.BATTERY_HEALTH_OVERHEAT -> "sobrecalentada"
                    BatteryManager.BATTERY_HEALTH_DEAD -> "agotada"
                    BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "sobrevoltaje"
                    else -> "normal"
                }

                ToolOutcome(
                    JSONObject()
                        .put("percent", pct)
                        .put("status", statusStr)
                        .put("power_source", plugStr)
                        .put("temperature_celsius", temp)
                        .put("health", healthStr)
                        .toString(2),
                )
            }.getOrElse { ToolOutcome("No se pudo leer el estado de la bateria: $it", isError = true) }
        }
    }

    private inner class TtsTool : AgentTool {
        override val spec = ToolSpec(
            name = "tts_speak",
            description = "Sintetiza voz a traves del altavoz del telefono usando Text-To-Speech.",
            inputSchema = objectSchema(
                "text" to stringProp("Texto a decir en voz alta."),
                required = listOf("text"),
            ),
        )
        override val risk = ToolRisk.WRITE

        override fun describe(input: JSONObject) = "hablar: ${input.optString("text").take(40)}"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.Main) {
            val text = input.optString("text").ifBlank {
                return@withContext ToolOutcome("Falta el parametro 'text'.", isError = true)
            }
            runCatching {
                var ttsInstance: android.speech.tts.TextToSpeech? = null
                ttsInstance = android.speech.tts.TextToSpeech(context) { status ->
                    if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                        ttsInstance?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "termdroid_tts")
                    }
                }
                ToolOutcome("Texto enviado a sintesis de voz.")
            }.getOrElse { ToolOutcome("No se pudo sintetizar voz: $it", isError = true) }
        }
    }

    private inner class VibrateTool : AgentTool {
        override val spec = ToolSpec(
            name = "vibrate",
            description = "Genera una vibracion / feedback haptico en el telefono.",
            inputSchema = objectSchema(
                "duration_ms" to intProp("Duracion de la vibracion en milisegundos. Por defecto 300."),
                required = emptyList(),
            ),
        )
        override val risk = ToolRisk.WRITE

        override fun describe(input: JSONObject) = "vibrar ${input.optInt("duration_ms", 300)}ms"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            runCatching {
                val ms = input.optInt("duration_ms", 300).coerceIn(50, 5000).toLong()
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? android.os.VibratorManager
                    vm?.defaultVibrator ?: @Suppress("DEPRECATION") (context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator)
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? android.os.Vibrator
                }
                if (vibrator != null && vibrator.hasVibrator()) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(android.os.VibrationEffect.createOneShot(ms, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(ms)
                    }
                    ToolOutcome("Vibracion ejecutada ($ms ms).")
                } else {
                    ToolOutcome("El dispositivo no tiene motor de vibracion disponible.")
                }
            }.getOrElse { ToolOutcome("No se pudo ejecutar la vibracion: $it", isError = true) }
        }
    }
}
