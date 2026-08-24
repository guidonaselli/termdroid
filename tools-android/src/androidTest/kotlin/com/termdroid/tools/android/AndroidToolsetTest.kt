package com.termdroid.tools.android

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.agent.AgentTool
import com.termdroid.agent.ToolRisk
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AndroidToolsetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var tools: Map<String, AgentTool>
    private val accessPedido = mutableListOf<SpecialAccess>()

    @Before
    fun setUp() {
        accessPedido.clear()
        tools = AndroidToolset(context) { accessPedido += it }.all().associateBy { it.spec.name }
    }

    private fun run(tool: String, vararg args: Pair<String, Any>) = runBlocking {
        tools.getValue(tool).execute(JSONObject().apply { args.forEach { put(it.first, it.second) } })
    }

    @Test
    fun listarAppsDevuelveAlgo() {
        val r = run("list_apps")
        assertFalse(r.content, r.isError)

        val json = JSONObject(r.content)
        assertTrue("deberia haber apps instaladas", json.getInt("total") > 0)
        assertTrue(json.getJSONArray("apps").length() > 0)
    }

    @Test
    fun cadaAppTraePaqueteYNombre() {
        val json = JSONObject(run("list_apps").content)
        val primera = json.getJSONArray("apps").getJSONObject(0)
        assertTrue(primera.getString("package").isNotBlank())
        assertTrue(primera.has("label"))
    }

    @Test
    fun incluirElSistemaDevuelveMas() {
        val sinSistema = JSONObject(run("list_apps").content).getInt("total")
        val conSistema = JSONObject(run("list_apps", "include_system" to "si").content).getInt("total")
        assertTrue("con sistema deberia haber mas: $conSistema vs $sinSistema", conSistema > sinSistema)
    }

    @Test
    fun elEstadoDelTelefonoTraeLosCamposEsperados() {
        val r = run("device_state")
        assertFalse(r.content, r.isError)

        val json = JSONObject(r.content)
        listOf("device", "android", "abi", "ram_total_mb", "storage_free_mb").forEach {
            assertTrue("falta $it", json.has(it))
        }
        assertTrue(json.getLong("ram_total_mb") > 0)
    }

    /** Sin el acceso especial, el tool explica que falta en vez de tirar una excepcion. */
    @Test
    fun elUsoDeAppsDegradaConExplicacionSiFaltaElPermiso() {
        val concedido = SpecialAccess.USAGE_STATS.isGranted(context)
        val r = run("app_usage")

        if (concedido) {
            assertFalse(r.content, r.isError)
        } else {
            assertTrue("deberia informar el permiso faltante", r.isError)
            assertTrue(r.content, r.content.contains("permiso"))
            assertEquals(
                "deberia avisarle a la UI para ofrecer Ajustes",
                listOf(SpecialAccess.USAGE_STATS),
                accessPedido,
            )
        }
    }

    @Test
    fun elAccesoEspecialSabeAdondeMandarAlUsuario() {
        val intent = SpecialAccess.USAGE_STATS.settingsIntent()
        assertEquals(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS, intent.action)
        assertTrue(SpecialAccess.USAGE_STATS.label.isNotBlank())
    }

    /** Todo esto solo lee: nada de aca puede pedir aprobacion como si escribiera. */
    @Test
    fun todasLasToolsDeAndroidSonDeLectura() {
        tools.values.forEach { assertEquals(it.spec.name, ToolRisk.READ, it.risk) }
    }
}
