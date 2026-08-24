package com.termdroid.agent

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class Recorder(private val turns: MutableList<TurnResult>) : Transport {
    val systems = mutableListOf<String>()
    val historiales = mutableListOf<List<Msg>>()

    override fun stream(
        system: String,
        tools: List<ToolSpec>,
        messages: List<Msg>,
    ): Flow<StreamEvent> = flow {
        systems += system
        historiales += messages.toList()
        emit(StreamEvent.Final(turns.removeAt(0)))
    }
}

class ContextInjectionTest {

    private fun loop(
        turns: MutableList<TurnResult>,
        contexto: () -> String?,
    ): Pair<AgentLoop, Recorder> {
        val t = Recorder(turns)
        return AgentLoop(
            transport = t,
            tools = ToolRegistry(emptyList()),
            systemPrompt = "SYSTEM CONGELADO",
            approvalGate = { _, _ -> true },
            volatileContext = contexto,
        ) to t
    }

    private fun texto(s: String) = TurnResult(listOf(Block.Text(s)), StopReason.END_TURN)

    @Test
    fun elEstadoVolatilViajaComoMensajeYNoEnElSystem() = runTest {
        var bateria = 87
        val (l, t) = loop(mutableListOf(texto("uno"), texto("dos"))) { "bateria=$bateria" }

        l.run("hola").toList()
        bateria = 42
        l.run("de nuevo").toList()

        assertEquals("el system prompt no puede cambiar", 1, t.systems.toSet().size)

        val sistemas = l.messages.filter { it.role == Role.SYSTEM }
            .map { (it.blocks.first() as Block.Text).text }
        assertEquals(listOf("bateria=87", "bateria=42"), sistemas)
    }

    @Test
    fun elMensajeDeSistemaVaDespuesDelDelUsuario() = runTest {
        val (l, _) = loop(mutableListOf(texto("ok"))) { "estado" }
        l.run("hola").toList()

        val i = l.messages.indexOfFirst { it.role == Role.SYSTEM }
        assertTrue("no puede ser el primer mensaje", i > 0)
        assertEquals(Role.USER, l.messages[i - 1].role)
    }

    @Test
    fun sinContextoNoSeAgregaNada() = runTest {
        val (l, _) = loop(mutableListOf(texto("ok"))) { null }
        l.run("hola").toList()
        assertTrue(l.messages.none { it.role == Role.SYSTEM })

        val (l2, _) = loop(mutableListOf(texto("ok"))) { "   " }
        l2.run("hola").toList()
        assertTrue(l2.messages.none { it.role == Role.SYSTEM })
    }

    @Test
    fun elHistorialQueLlegaAlTransporteIncluyeElEstado() = runTest {
        val (l, t) = loop(mutableListOf(texto("ok"))) { "cwd=/tmp" }
        l.run("donde estoy").toList()

        val enviado = t.historiales.first()
        assertTrue(enviado.any { it.role == Role.SYSTEM })
        assertNotEquals(Role.SYSTEM, enviado.first().role)
    }
}
