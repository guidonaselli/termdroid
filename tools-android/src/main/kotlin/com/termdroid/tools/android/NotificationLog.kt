package com.termdroid.tools.android

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import org.json.JSONObject
import java.io.File

data class LoggedNotification(
    val packageName: String,
    val title: String,
    val text: String,
    val postedAt: Long,
)

object NotificationLog {

    private const val MAX = 200
    private const val FILE = "notifications.jsonl"

    @Volatile
    private var store: File? = null

    fun attach(context: Context) {
        if (store == null) {
            store = File(context.filesDir, FILE)
        }
    }

    @Synchronized
    fun add(n: LoggedNotification) {
        val f = store ?: return
        val line = JSONObject()
            .put("p", n.packageName)
            .put("t", n.title)
            .put("x", n.text)
            .put("at", n.postedAt)
            .toString()
        f.appendText(line + "\n")
        trim(f)
    }

    @Synchronized
    fun recent(limit: Int, packageFilter: String? = null): List<LoggedNotification> {
        val f = store ?: return emptyList()
        if (!f.exists()) return emptyList()
        return f.readLines()
            .asReversed()
            .mapNotNull { parse(it) }
            .filter { packageFilter == null || it.packageName.contains(packageFilter, true) }
            .take(limit)
    }

    @Synchronized
    fun clear() {
        store?.delete()
    }

    private fun parse(line: String): LoggedNotification? = runCatching {
        val o = JSONObject(line)
        LoggedNotification(o.getString("p"), o.optString("t"), o.optString("x"), o.optLong("at"))
    }.getOrNull()

    private fun trim(f: File) {
        val lines = f.readLines()
        if (lines.size > MAX) {
            f.writeText(lines.takeLast(MAX).joinToString("\n", postfix = "\n"))
        }
    }

    fun isEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ).orEmpty()
        val expected = ComponentName(context, TermdroidNotificationListener::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == expected }
    }
}

class TermdroidNotificationListener : NotificationListenerService() {

    override fun onCreate() {
        super.onCreate()
        NotificationLog.attach(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        NotificationLog.add(
            LoggedNotification(
                packageName = sbn.packageName,
                title = extras.getCharSequence("android.title")?.toString().orEmpty(),
                text = extras.getCharSequence("android.text")?.toString().orEmpty(),
                postedAt = sbn.postTime,
            ),
        )
    }
}
