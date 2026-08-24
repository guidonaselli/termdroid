package com.termdroid.tools.unix

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.agent.AgentTool
import com.termdroid.exec.ExecBackend
import com.termdroid.exec.ExecEnvironment
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** Las tools se prueban contra el entorno real del device. */
class UnixToolsetTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workspace: File
    private lateinit var tools: Map<String, AgentTool>

    @Before
    fun setUp() {
        workspace = File(context.cacheDir, "ws-test").apply {
            deleteRecursively()
            mkdirs()
        }
        val env = ExecEnvironment(context)
        tools = UnixToolset(env, ExecBackend.NATIVE_LIB_DIR, workspace)
            .all()
            .associateBy { it.spec.name }
    }

    private fun run(tool: String, vararg args: Pair<String, Any>) = runBlocking {
        tools.getValue(tool).execute(JSONObject().apply { args.forEach { put(it.first, it.second) } })
    }

    @Test
    fun bashCorreSobreLoQueTraeAndroid() {
        val r = run("bash", "command" to "echo HOLA && echo MUNDO")
        assertFalse(r.content, r.isError)
        assertTrue(r.content, r.content.contains("HOLA"))
        assertTrue(r.content, r.content.contains("MUNDO"))
    }

    @Test
    fun bashMarcaErrorCuandoElComandoFalla() {
        val r = run("bash", "command" to "exit 3")
        assertTrue("un exit distinto de cero es un error", r.isError)
    }

    @Test
    fun escribirYLeerUnArchivo() {
        val w = run("write_file", "path" to "notas.txt", "content" to "linea uno\nlinea dos\n")
        assertFalse(w.content, w.isError)

        val r = run("read_file", "path" to "notas.txt")
        assertFalse(r.content, r.isError)
        assertTrue(r.content, r.content.contains("linea uno"))
        // Con numeros de linea, que es lo que hace util un read para un agente.
        assertTrue(r.content, r.content.contains("1\t"))
    }

    @Test
    fun editarReemplazaUnaCadenaUnica() {
        run("write_file", "path" to "a.txt", "content" to "hola mundo")
        val e = run("edit_file", "path" to "a.txt", "old" to "mundo", "new" to "gente")
        assertFalse(e.content, e.isError)
        assertEquals("hola gente", File(workspace, "a.txt").readText())
    }

    /** Fallar es mejor que adivinar cual de las coincidencias era. */
    @Test
    fun editarFallaSiLaCadenaNoEsUnica() {
        run("write_file", "path" to "b.txt", "content" to "x\nx\n")
        val e = run("edit_file", "path" to "b.txt", "old" to "x", "new" to "y")
        assertTrue(e.isError)
        assertTrue(e.content, e.content.contains("2 veces"))
        assertEquals("el archivo no debio tocarse", "x\nx\n", File(workspace, "b.txt").readText())
    }

    @Test
    fun editarFallaSiNoEncuentraElTexto() {
        run("write_file", "path" to "c.txt", "content" to "hola")
        val e = run("edit_file", "path" to "c.txt", "old" to "chau", "new" to "x")
        assertTrue(e.isError)
    }

    @Test
    fun globYGrepEncuentranLoQueHay() {
        run("write_file", "path" to "src/uno.kt", "content" to "fun uno() = TODO()")
        run("write_file", "path" to "src/dos.kt", "content" to "fun dos() = 2")
        run("write_file", "path" to "leeme.md", "content" to "documentacion")

        val g = run("glob", "pattern" to "**/*.kt")
        assertTrue(g.content, g.content.contains("src/uno.kt"))
        assertTrue(g.content, g.content.contains("src/dos.kt"))
        assertFalse(g.content, g.content.contains("leeme.md"))

        val gr = run("grep", "pattern" to "fun dos")
        assertTrue(gr.content, gr.content.contains("src/dos.kt:1"))
    }

    /** Un agente no puede escribir fuera de su workspace, ni siquiera con `../` o siguiendo un symlink. */
    @Test
    fun noSePuedeEscaparDelWorkspaceConDosPuntos() {
        val r = run("write_file", "path" to "../afuera.txt", "content" to "no")
        assertTrue("deberia rechazarse", r.isError)
        assertFalse(File(workspace.parentFile, "afuera.txt").exists())
    }

    @Test
    fun noSePuedeLeerUnaRutaAbsolutaDeAfuera() {
        val r = run("read_file", "path" to "/system/build.prop")
        assertTrue("deberia rechazarse", r.isError)
    }

    @Test
    fun leerAlgoQueNoExisteEsErrorNoExcepcion() {
        val r = run("read_file", "path" to "no-existe.txt")
        assertTrue(r.isError)
        assertTrue(r.content, r.content.isNotBlank())
    }

    /** El riesgo declarado es lo que decide si hace falta aprobacion. */
    @Test
    fun losRiesgosEstanBienDeclarados() {
        assertEquals(com.termdroid.agent.ToolRisk.READ, tools.getValue("read_file").risk)
        assertEquals(com.termdroid.agent.ToolRisk.READ, tools.getValue("glob").risk)
        assertEquals(com.termdroid.agent.ToolRisk.READ, tools.getValue("grep").risk)
        assertEquals(com.termdroid.agent.ToolRisk.WRITE, tools.getValue("write_file").risk)
        assertEquals(com.termdroid.agent.ToolRisk.WRITE, tools.getValue("edit_file").risk)
        assertEquals(com.termdroid.agent.ToolRisk.EXEC, tools.getValue("bash").risk)
    }

    /** La descripcion es lo que ve el usuario al aprobar: tiene que ser la accion exacta. */
    @Test
    fun laDescripcionMuestraLaAccionExacta() {
        val d = tools.getValue("bash").describe(JSONObject().put("command", "rm -rf /tmp/x"))
        assertEquals("rm -rf /tmp/x", d)
    }
}
