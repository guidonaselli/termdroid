package com.termdroid

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.tools.android.AndroidToolset
import com.termdroid.tools.android.SpecialAccess
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** El camino con el permiso concedido, contra el contexto de la app. */
class AppUsageGrantedTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun appUsage(days: Int) = runBlocking {
        AndroidToolset(context).all()
            .first { it.spec.name == "app_usage" }
            .execute(JSONObject().put("days", days))
    }

    @Test
    fun conElPermisoDevuelveUsoReal() {
        assumeTrue(
            "requiere el acceso al uso de apps concedido",
            SpecialAccess.USAGE_STATS.isGranted(context),
        )

        val r = appUsage(7)
        assertFalse(r.content, r.isError)

        val json = JSONObject(r.content)
        val apps = json.getJSONArray("apps")
        assertTrue("no hubo apps con uso registrado", apps.length() > 0)

        val primera = apps.getJSONObject(0)
        assertTrue(primera.getString("package").isNotBlank())
        assertTrue("los minutos deberian ser >= 0", primera.getInt("minutes") >= 0)

        android.util.Log.i("TDUsageOk", "apps=${apps.length()} top=${primera}")
    }

    @Test
    fun vienenOrdenadasDeMayorAMenorUso() {
        assumeTrue(SpecialAccess.USAGE_STATS.isGranted(context))

        val apps = JSONObject(appUsage(30).content).getJSONArray("apps")
        assumeTrue("hacen falta al menos dos apps", apps.length() >= 2)

        val minutos = (0 until apps.length()).map { apps.getJSONObject(it).getInt("minutes") }
        assertTrue("no estan ordenadas: $minutos", minutos == minutos.sortedDescending())
    }
}
