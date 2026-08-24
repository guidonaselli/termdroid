package com.termdroid

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.tools.android.AndroidToolset
import com.termdroid.tools.android.LoggedNotification
import com.termdroid.tools.android.NotificationLog
import com.termdroid.tools.android.SpecialAccess
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test

class NotificationsToolTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val accesos = mutableListOf<SpecialAccess>()

    @Before
    fun setUp() {
        accesos.clear()
        NotificationLog.attach(context)
        NotificationLog.clear()
    }

    private fun run(vararg args: Pair<String, Any>) = runBlocking {
        AndroidToolset(context) { accesos += it }.all()
            .first { it.spec.name == "notifications" }
            .execute(JSONObject().apply { args.forEach { put(it.first, it.second) } })
    }

    @Test
    fun sinElAccesoExplicaYAvisaALaUi() {
        assumeTrue(
            "este caso solo aplica sin el listener habilitado",
            !SpecialAccess.NOTIFICATIONS.isGranted(context),
        )

        val r = run()

        assertTrue(r.isError)
        assertTrue(r.content, r.content.contains("permiso"))
        assertEquals(listOf(SpecialAccess.NOTIFICATIONS), accesos)
    }

    @Test
    fun elAccesoSabeAdondeMandarAlUsuario() {
        val intent = SpecialAccess.NOTIFICATIONS.settingsIntent()
        assertEquals(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS, intent.action)
    }

    @Test
    fun conElAccesoDevuelveLoQueLlego() {
        assumeTrue(SpecialAccess.NOTIFICATIONS.isGranted(context))

        NotificationLog.add(LoggedNotification("com.ejemplo", "Titulo", "Cuerpo", 1000))
        val r = run()

        assertFalse(r.content, r.isError)
        val n = JSONObject(r.content).getJSONArray("notifications").getJSONObject(0)
        assertEquals("com.ejemplo", n.getString("package"))
        assertEquals("Titulo", n.getString("title"))
    }

    @Test
    fun elBufferDevuelveLaMasNuevaPrimeroYRespetaElLimite() {
        repeat(5) { NotificationLog.add(LoggedNotification("p$it", "t$it", "c$it", it.toLong())) }

        val recientes = NotificationLog.recent(3)

        assertEquals(3, recientes.size)
        assertEquals("p4", recientes[0].packageName)
        assertEquals("p2", recientes[2].packageName)
    }

    @Test
    fun elFiltroPorPaqueteFunciona() {
        NotificationLog.add(LoggedNotification("com.whatsapp", "a", "b", 1))
        NotificationLog.add(LoggedNotification("com.gmail", "c", "d", 2))

        val soloWhats = NotificationLog.recent(10, "whatsapp")

        assertEquals(1, soloWhats.size)
        assertEquals("com.whatsapp", soloWhats.single().packageName)
    }

    /** El buffer esta acotado: no puede crecer sin limite en un telefono. */
    @Test
    fun elBufferNoCreceSinLimite() {
        repeat(500) { NotificationLog.add(LoggedNotification("p", "t", "c", it.toLong())) }
        assertTrue(NotificationLog.recent(1000).size <= 200)
    }
}
