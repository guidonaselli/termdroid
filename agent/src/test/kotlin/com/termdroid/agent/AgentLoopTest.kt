package com.termdroid.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Transporte de mentira: devuelve turnos guionados y registra lo que recibio. */
private class FakeTransport(private val turns: MutableList<TurnResult>) : Transport {
    val requests = mutableListOf<List<Msg>>()
    val systems = mutableListOf<String>()
    val toolSets = mutableListOf<List<String>>()

    override fun stream(
        system: String,
        tools: List<ToolSpec>,
        messages: List<Msg>,
    ): Flow<StreamEvent> = flow {
        requests += messages.map { it.copy() }
        systems += system
        toolSets += tools.map { it.name }
        val turn = turns.removeAt(0)
        turn.blocks.filterIsInstance<Block.Text>().forEach { emit(StreamEvent.Text(it.text)) }
        emit(StreamEvent.Final(turn))
    }
}

private class FakeTool(
    name: String,
    override val risk: ToolRisk,
    private val body: suspend (JSONObject) -> ToolOutcome = { ToolOutcome("ok de $name") },
) : AgentTool {
    var calls = 0
    override val spec = ToolSpec(name, "tool de prueba", objectSchema("x" to stringProp("x")))
    override fun describe(input: JSONObject) = "${spec.name}(${input.optString("x")})"
    override suspend fun execute(input: JSONObject): ToolOutcome {
        calls++
        return body(input)
    }
}

private fun toolUse(id: String, name: String, x: String = "1") =
    Block.ToolUse(id, name, JSONObject().put("x", x))

private fun textTurn(text: String) =
    TurnResult(listOf(Block.Text(text)), StopReason.END_TURN)

private fun toolTurn(vararg uses: Block.ToolUse) =
    TurnResult(uses.toList(), StopReason.TOOL_USE)

class AgentLoopTest {

    private fun loop(
        turns: MutableList<TurnResult>,
        tools: List<AgentTool> = emptyList(),
        approve: Boolean = true,
        autonomy: AutonomyMode = AutonomyMode.AUTO_ALL,
    ): Pair<AgentLoop, FakeTransport> {
        val transport = FakeTransport(turns)
        val l = AgentLoop(
            transport = transport,
            tools = ToolRegistry(tools),
            systemPrompt = "SYSTEM ESTABLE",
            approvalGate = { _, _ -> approve },
        )
        l.autonomy = autonomy
        return l to transport
    }

    @Test
    fun unTurnoSimpleTerminaEnDone() = runTest {
        val (l, _) = loop(mutableListOf(textTurn("hola")))
        val events = l.run("che").toList()

        assertTrue(events.any { it is AgentEvent.TextDelta })
        assertTrue(events.last() is AgentEvent.Done)
    }

    /** La regla que se rompe mas facil y falla en silencio: si los `tool_result` se reparten entre. */
    @Test
    fun losResultadosDeVariosToolsVanEnUnSoloMensaje() = runTest {
        val a = FakeTool("a", ToolRisk.READ)
        val b = FakeTool("b", ToolRisk.READ)
        val (l, _) = loop(
            mutableListOf(
                toolTurn(toolUse("t1", "a"), toolUse("t2", "b")),
                textTurn("listo"),
            ),
            tools = listOf(a, b),
        )

        l.run("hace las dos cosas").toList()

        val userMsgs = l.messages.filter { it.role == Role.USER }
        val conResultados = userMsgs.filter { m -> m.blocks.any { it is Block.ToolResult } }
        assertEquals("los tool_result se repartieron en varios mensajes", 1, conResultados.size)
        assertEquals(2, conResultados.single().blocks.count { it is Block.ToolResult })
    }

    @Test
    fun losResultadosVuelvenEnElOrdenEnQueSePidieron() = runTest {
        val a = FakeTool("a", ToolRisk.READ)
        val b = FakeTool("b", ToolRisk.READ)
        val (l, _) = loop(
            mutableListOf(toolTurn(toolUse("t1", "a"), toolUse("t2", "b")), textTurn("ok")),
            tools = listOf(a, b),
        )

        l.run("dale").toList()

        val results = l.messages.flatMap { it.blocks }.filterIsInstance<Block.ToolResult>()
        assertEquals(listOf("t1", "t2"), results.map { it.toolUseId })
    }

    /** Un tool que falla devuelve un bloque con is_error, nunca se omite. */
    @Test
    fun unToolQueFallaDevuelveIsError() = runTest {
        val roto = FakeTool("roto", ToolRisk.READ) { error("se rompio") }
        val (l, _) = loop(
            mutableListOf(toolTurn(toolUse("t1", "roto")), textTurn("bueno")),
            tools = listOf(roto),
        )

        l.run("proba").toList()

        val res = l.messages.flatMap { it.blocks }.filterIsInstance<Block.ToolResult>().single()
        assertTrue("el fallo tiene que marcarse como error", res.isError)
        assertEquals("t1", res.toolUseId)
    }

    @Test
    fun unToolInexistenteNoTiraElLoop() = runTest {
        val (l, _) = loop(
            mutableListOf(toolTurn(toolUse("t1", "fantasma")), textTurn("ok")),
            tools = emptyList(),
        )

        val events = l.run("dale").toList()

        val res = l.messages.flatMap { it.blocks }.filterIsInstance<Block.ToolResult>().single()
        assertTrue(res.isError)
        assertTrue(events.last() is AgentEvent.Done)
    }

    @Test
    fun unRechazoNoEjecutaPeroInformaAlModelo() = runTest {
        val t = FakeTool("escribir", ToolRisk.WRITE)
        val (l, _) = loop(
            mutableListOf(toolTurn(toolUse("t1", "escribir")), textTurn("ok")),
            tools = listOf(t),
            approve = false,
            autonomy = AutonomyMode.ASK_ALL,
        )

        val events = l.run("escribi algo").toList()

        assertEquals("no debio ejecutarse", 0, t.calls)
        assertTrue(events.any { it is AgentEvent.ToolRejected })
        val res = l.messages.flatMap { it.blocks }.filterIsInstance<Block.ToolResult>().single()
        assertTrue(res.isError)
        assertTrue(res.content.contains("rechaz"))
    }

    @Test
    fun elRefusalSeInformaYCorta() = runTest {
        val (l, _) = loop(
            mutableListOf(
                TurnResult(emptyList(), StopReason.REFUSAL, refusal = Refusal("cyber", "no")),
            ),
        )

        val events = l.run("algo").toList()

        val refused = events.filterIsInstance<AgentEvent.Refused>().single()
        assertEquals("cyber", refused.refusal.category)
        assertFalse(events.any { it is AgentEvent.Done })
    }

    @Test
    fun maxTokensSeInformaComoTruncado() = runTest {
        val (l, _) = loop(
            mutableListOf(TurnResult(listOf(Block.Text("a medio")), StopReason.MAX_TOKENS)),
        )

        val events = l.run("escribi largo").toList()

        assertTrue(events.any { it is AgentEvent.Truncated })
    }

    /** El prefijo cacheado es `tools` + `system`. */
    @Test
    fun elSystemYLosToolsSonEstablesEntreTurnos() = runTest {
        val a = FakeTool("zeta", ToolRisk.READ)
        val b = FakeTool("alfa", ToolRisk.READ)
        val (l, transport) = loop(
            mutableListOf(toolTurn(toolUse("t1", "alfa")), textTurn("ok")),
            tools = listOf(a, b),
        )

        l.run("dale").toList()

        assertEquals(2, transport.systems.size)
        assertEquals(1, transport.systems.toSet().size)
        assertEquals(1, transport.toolSets.toSet().size)
        // Y el orden es deterministico, no el de construccion.
        assertEquals(listOf("alfa", "zeta"), transport.toolSets.first())
    }

    @Test
    fun elHistorialCreceYSePuedeRestaurar() = runTest {
        val (l, _) = loop(mutableListOf(textTurn("uno")))
        l.run("hola").toList()
        val guardado = l.messages

        val (otro, _) = loop(mutableListOf(textTurn("dos")))
        otro.restore(guardado)
        otro.run("seguimos").toList()

        assertEquals(Role.USER, otro.messages.first().role)
        assertTrue(otro.messages.size > guardado.size)
    }

    @Test
    fun elContenidoDelAsistenteSeGuardaCompletoNoSoloElTexto() = runTest {
        val turno = TurnResult(
            listOf(
                Block.Thinking("razonando", signature = "sig"),
                Block.Text("respuesta"),
                Block.Opaque("{\"type\":\"compaction\"}"),
            ),
            StopReason.END_TURN,
        )
        val (l, _) = loop(mutableListOf(turno))

        l.run("hola").toList()

        val asistente = l.messages.last { it.role == Role.ASSISTANT }
        assertTrue(asistente.blocks.any { it is Block.Thinking })
        assertTrue(asistente.blocks.any { it is Block.Opaque })
    }
}

class AutonomyTest {

    @Test
    fun privilegiadoSiempreNecesitaAprobacion() {
        AutonomyMode.entries.forEach { modo ->
            assertTrue(
                "en $modo un tool privilegiado no puede correr solo",
                modo.needsApproval(ToolRisk.PRIVILEGED),
            )
        }
    }

    @Test
    fun autoLecturaSoloAutomatizaLectura() {
        val m = AutonomyMode.AUTO_READ
        assertFalse(m.needsApproval(ToolRisk.READ))
        assertTrue(m.needsApproval(ToolRisk.WRITE))
        assertTrue(m.needsApproval(ToolRisk.EXEC))
    }

    @Test
    fun preguntarTodoPreguntaTodo() {
        val m = AutonomyMode.ASK_ALL
        ToolRisk.entries.forEach { assertTrue(m.needsApproval(it)) }
    }

    @Test
    fun autoTodoNoPreguntaSalvoPrivilegiado() {
        val m = AutonomyMode.AUTO_ALL
        assertFalse(m.needsApproval(ToolRisk.READ))
        assertFalse(m.needsApproval(ToolRisk.WRITE))
        assertFalse(m.needsApproval(ToolRisk.EXEC))
        assertTrue(m.needsApproval(ToolRisk.PRIVILEGED))
    }
}
