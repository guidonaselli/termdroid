package com.termdroid

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.tools.android.NotificationLog
import com.termdroid.tools.android.SpecialAccess
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NotificationCaptureTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun elListenerCapturoAlgoDelSistema() {
        assumeTrue(SpecialAccess.NOTIFICATIONS.isGranted(context))

        NotificationLog.attach(context)
        val capturadas = NotificationLog.recent(200)
        assumeTrue("no hay notificaciones publicadas para observar", capturadas.isNotEmpty())

        android.util.Log.i(
            "TDNotif",
            "capturadas=${capturadas.size} primera=${capturadas.first()}",
        )
        assertTrue(capturadas.all { it.packageName.isNotBlank() })
    }
}
