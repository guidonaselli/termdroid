package com.termdroid.tools.unix

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.agent.AgentEvent
import com.termdroid.agent.AgentLoop
import com.termdroid.agent.AutonomyMode
import com.termdroid.agent.Block
import com.termdroid.agent.Msg
import com.termdroid.agent.Role
import com.termdroid.agent.StopReason
import com.termdroid.agent.StreamEvent
import com.termdroid.agent.ToolRegistry
import com.termdroid.agent.ToolSpec
import com.termdroid.agent.Transport
import com.termdroid.agent.TurnResult
import com.termdroid.exec.ExecBackend
import com.termdroid.exec.ExecEnvironment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * El camino completo del agente contra tools reales, en un device real.
 *
 * El modelo se reemplaza por un guion; todo lo demas es de verdad: el shell, el
 * filesystem y las aprobaciones. Es lo que verifica que las piezas encajan.
 */
class AgentIntegrationTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workspace: File

    @Before
    fun setUp() {
        workspace = File(context.cacheDir, "ws-integ").apply {
            deleteRecursively()
            mkdirs()
        }
    }

    private class ScriptedTransport(private val turns: MutableList<TurnResult>) : Transport {
        override fun stream(
            system: String,
            tools: List<ToolSpec>,
            messages: List<Msg>,
        ): Flow<StreamEvent> = flow {
            emit(StreamEvent.Final(turns.removeAt(0)))
        }
    }

    private fun loop(
        turns: MutableList<TurnResult>,
        autonomy: AutonomyMode,
        approve: Boolean = true,
    ): AgentLoop {
        val env = ExecEnvironment(context)
        val tools = UnixToolset(env, ExecBackend.NATIVE_LIB_DIR, workspace).all()
        return AgentLoop(
            transport = ScriptedTransport(turns),
            tools = ToolRegistry(tools),
            systemPrompt = "sos un agente de prueba",
            approvalGate = { _, _ -> approve },
        ).also { it.autonomy = autonomy }
    }

    private fun use(id: String, name: String, vararg args: Pair<String, Any>) =
        Block.ToolUse(id, name, JSONObject().apply { args.forEach { put(it.first, it.second) } })

    @Test
    fun elAgenteEscribeUnArchivoDeVerdad() = runBlocking {
        val l = loop(
            mutableListOf(
                TurnResult(
                    listOf(use("t1", "write_file", "path" to "hola.txt", "content" to "che")),
                    StopReason.TOOL_USE,
                ),
                TurnResult(listOf(Block.Text("listo")), StopReason.END_TURN),
            ),
            autonomy = AutonomyMode.AUTO_ALL,
        )

        val events = l.run("escribi hola.txt").toList()

        assertEquals("che", File(workspace, "hola.txt").readText())
        assertTrue(events.last() is AgentEvent.Done)
    }

    @Test
    fun leerYEditarEnDosTurnosEncadenados() = runBlocking {
        File(workspace, "config.txt").writeText("modo=viejo\n")

        val l = loop(
            mutableListOf(
                TurnResult(listOf(use("t1", "read_file", "path" to "config.txt")), StopReason.TOOL_USE),
                TurnResult(
                    listOf(use("t2", "edit_file", "path" to "config.txt", "old" to "viejo", "new" to "nuevo")),
                    StopReason.TOOL_USE,
                ),
                TurnResult(listOf(Block.Text("cambiado")), StopReason.END_TURN),
            ),
            autonomy = AutonomyMode.AUTO_ALL,
        )

        l.run("cambia el modo").toList()

        assertEquals("modo=nuevo\n", File(workspace, "config.txt").readText())
    }

    /** El resultado real del tool tiene que volver al modelo, no un resumen. */
    @Test
    fun laSalidaDelToolVuelveAlHistorial() = runBlocking {
        val l = loop(
            mutableListOf(
                TurnResult(listOf(use("t1", "bash", "command" to "echo MARCA-99")), StopReason.TOOL_USE),
                TurnResult(listOf(Block.Text("ok")), StopReason.END_TURN),
            ),
            autonomy = AutonomyMode.AUTO_ALL,
        )

        l.run("corre el comando").toList()

        val res = l.messages.flatMap { it.blocks }.filterIsInstance<Block.ToolResult>().single()
        assertFalse(res.isError)
        assertTrue(res.content, res.content.contains("MARCA-99"))
    }

    /** En Auto-lectura una escritura pide permiso; si se rechaza, no toca el disco. */
    @Test
    fun enAutoLecturaUnaEscrituraRechazadaNoTocaElDisco() = runBlocking {
        val l = loop(
            mutableListOf(
                TurnResult(
                    listOf(use("t1", "write_file", "path" to "no-va.txt", "content" to "x")),
                    StopReason.TOOL_USE,
                ),
                TurnResult(listOf(Block.Text("bueno")), StopReason.END_TURN),
            ),
            autonomy = AutonomyMode.AUTO_READ,
            approve = false,
        )

        val events = l.run("escribi algo").toList()

        assertFalse("no debio crearse", File(workspace, "no-va.txt").exists())
        assertTrue(events.any { it is AgentEvent.ToolApprovalNeeded })
        assertTrue(events.any { it is AgentEvent.ToolRejected })
    }

    /** En Auto-lectura una lectura corre sola: no debe pedir aprobacion. */
    @Test
    fun enAutoLecturaLaLecturaNoPidePermiso() = runBlocking {
        File(workspace, "libre.txt").writeText("contenido")

        val l = loop(
            mutableListOf(
                TurnResult(listOf(use("t1", "read_file", "path" to "libre.txt")), StopReason.TOOL_USE),
                TurnResult(listOf(Block.Text("ok")), StopReason.END_TURN),
            ),
            autonomy = AutonomyMode.AUTO_READ,
            approve = false,
        )

        val events = l.run("lee el archivo").toList()

        assertFalse(events.any { it is AgentEvent.ToolApprovalNeeded })
        val res = l.messages.flatMap { it.blocks }.filterIsInstance<Block.ToolResult>().single()
        assertTrue(res.content, res.content.contains("contenido"))
    }

    /** Varios tools en un turno corren y vuelven juntos, contra el filesystem real. */
    @Test
    fun variosToolsEnUnTurnoCorrenYVuelvenJuntos() = runBlocking {
        val l = loop(
            mutableListOf(
                TurnResult(
                    listOf(
                        use("t1", "write_file", "path" to "a.txt", "content" to "A"),
                        use("t2", "write_file", "path" to "b.txt", "content" to "B"),
                    ),
                    StopReason.TOOL_USE,
                ),
                TurnResult(listOf(Block.Text("hechos")), StopReason.END_TURN),
            ),
            autonomy = AutonomyMode.AUTO_ALL,
        )

        l.run("escribi dos archivos").toList()

        assertEquals("A", File(workspace, "a.txt").readText())
        assertEquals("B", File(workspace, "b.txt").readText())

        val conResultados = l.messages
            .filter { it.role == Role.USER }
            .filter { m -> m.blocks.any { it is Block.ToolResult } }
        assertEquals(1, conResultados.size)
        assertEquals(2, conResultados.single().blocks.size)
    }

    /** Un intento de salir del workspace vuelve como error, no como excepcion. */
    @Test
    fun elAgenteNoPuedeEscaparDelWorkspace() = runBlocking {
        val l = loop(
            mutableListOf(
                TurnResult(
                    listOf(use("t1", "write_file", "path" to "../fuga.txt", "content" to "x")),
                    StopReason.TOOL_USE,
                ),
                TurnResult(listOf(Block.Text("no pude")), StopReason.END_TURN),
            ),
            autonomy = AutonomyMode.AUTO_ALL,
        )

        val events = l.run("escribi afuera").toList()

        assertFalse(File(workspace.parentFile, "fuga.txt").exists())
        val res = l.messages.flatMap { it.blocks }.filterIsInstance<Block.ToolResult>().single()
        assertTrue("deberia informarse como error", res.isError)
        assertTrue(events.last() is AgentEvent.Done)
    }
}
