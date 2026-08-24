package com.termdroid.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClaudeTransportTest {

    private val transport = ClaudeTransport(apiKey = "sk-ant-de-prueba")

    private val tool = ToolSpec(
        name = "read_file",
        description = "Lee un archivo",
        inputSchema = objectSchema(
            "path" to stringProp("Ruta"),
            "limit" to intProp("Lineas"),
            required = listOf("path"),
        ),
    )

    private fun params(messages: List<Msg>, tools: List<ToolSpec> = listOf(tool)) =
        transport.buildParams("SYSTEM CONGELADO", tools, messages)

    @Test
    fun elModeloYElPresupuestoSonLosEsperados() {
        val p = params(listOf(Msg.user("hola")))
        assertEquals("claude-opus-5", p.model().toString())
        assertEquals(64_000L, p.maxTokens())
    }

    @Test
    fun elThinkingEsAdaptativoYConResumen() {
        val p = params(listOf(Msg.user("hola")))
        val thinking = p.thinking().get()
        assertTrue("deberia ser adaptativo", thinking.adaptive().isPresent)
        assertEquals(
            "display esperado",
            "summarized",
            thinking.adaptive().get().display().get().toString(),
        )
    }

    @Test
    fun elEsfuerzoLlegaEnElOutputConfig() {
        val p = params(listOf(Msg.user("hola")))
        assertEquals("xhigh", p.outputConfig().get().effort().get().toString())
    }

    @Test
    fun elSystemViajaComoBloqueConCacheControl() {
        val p = params(listOf(Msg.user("hola")))
        val bloques = p.system().get().asTextBlockParams()

        assertEquals(1, bloques.size)
        assertEquals("SYSTEM CONGELADO", bloques.first().text())
        assertTrue("falta cache_control", bloques.first().cacheControl().isPresent)
    }

    @Test
    fun elToolLlegaConStrictYSusRequeridos() {
        val p = params(listOf(Msg.user("hola")))
        val t = p.tools().get().single().tool().get()

        assertEquals("read_file", t.name())
        assertEquals(true, t.strict().get())
        assertEquals(listOf("path"), t.inputSchema().required().get())
        assertTrue(t.inputSchema()._properties().toString().contains("path"))
    }

    @Test
    fun losTresRolesSeMapean() {
        val p = params(
            listOf(
                Msg.user("hola"),
                Msg.system("bateria=50"),
                Msg.assistant(listOf(Block.Text("que tal"))),
            ),
        )
        val roles = p.messages().map { it.role().toString() }
        assertEquals(listOf("user", "system", "assistant"), roles)
    }

    @Test
    fun elToolUseYSuResultadoSobrevivenLaTraduccion() {
        val p = params(
            listOf(
                Msg.user("leelo"),
                Msg.assistant(
                    listOf(Block.ToolUse("t1", "read_file", JSONObject().put("path", "a.txt"))),
                ),
                Msg.userBlocks(listOf(Block.ToolResult("t1", "contenido", isError = false))),
            ),
        )

        val asistente = p.messages()[1].content().blockParams().get().single()
        val uso = asistente.toolUse().get()
        assertEquals("t1", uso.id())
        assertEquals("read_file", uso.name())
        assertTrue(uso._input().toString().contains("a.txt"))

        val resultado = p.messages()[2].content().blockParams().get().single().toolResult().get()
        assertEquals("t1", resultado.toolUseId())
        assertEquals(false, resultado.isError().get())
    }

    @Test
    fun unResultadoFallidoViajaMarcadoComoError() {
        val p = params(
            listOf(
                Msg.user("x"),
                Msg.userBlocks(listOf(Block.ToolResult("t1", "se rompio", isError = true))),
            ),
        )
        val r = p.messages()[1].content().blockParams().get().single().toolResult().get()
        assertEquals(true, r.isError().get())
    }

    @Test
    fun elOrdenDeLosToolsEsElQueSeLePasa() {
        val a = tool.copy(name = "alfa")
        val z = tool.copy(name = "zeta")
        val p = params(listOf(Msg.user("x")), tools = listOf(a, z))

        assertEquals(
            listOf("alfa", "zeta"),
            p.tools().get().map { it.tool().get().name() },
        )
    }

    @Test
    fun unMensajeVacioNoSeEnvia() {
        val p = params(listOf(Msg.user("hola"), Msg.assistant(listOf(Block.Text("   ")))))
        assertEquals(1, p.messages().size)
        assertFalse(p.messages().any { it.role().toString() == "assistant" })
    }
}
