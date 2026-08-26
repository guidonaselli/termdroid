package com.termdroid.tools.unix

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.agent.AgentTool
import com.termdroid.exec.ExecBackend
import com.termdroid.exec.ExecEnvironment
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class RipgrepTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var workspace: File
    private lateinit var env: ExecEnvironment
    private lateinit var tools: Map<String, AgentTool>

    @Before
    fun setUp() {
        workspace = File(context.cacheDir, "ws-rg").apply {
            deleteRecursively()
            mkdirs()
        }
        env = ExecEnvironment(context)
        tools = UnixToolset(env, ExecBackend.NATIVE_LIB_DIR, workspace)
            .all().associateBy { it.spec.name }

        File(workspace, "src").mkdirs()
        File(workspace, "src/uno.kt").writeText("fun buscame() = 1\nfun otra() = 2\n")
        File(workspace, "src/dos.kt").writeText("val x = buscame()\n")
        File(workspace, "leeme.md").writeText("aca tambien buscame\n")
    }

    private fun grep(vararg args: Pair<String, Any>) = runBlocking {
        tools.getValue("grep").execute(JSONObject().apply { args.forEach { put(it.first, it.second) } })
    }

    @Test
    fun elBinarioDeRipgrepViajaEnElApk() {
        val bin = env.packaged("rg")
        assertTrue("no se encontro ${bin.path}", bin.exists())
        assertTrue("deberia pesar algo", bin.length() > 1_000_000)
    }

    @Test
    fun ripgrepCorreYEncuentra() {
        assumeTrue(env.packaged("rg").exists())

        val r = grep("pattern" to "buscame")

        assertFalse(r.content, r.isError)
        assertTrue(r.content, r.content.contains("uno.kt"))
        assertTrue(r.content, r.content.contains("dos.kt"))
        assertTrue(r.content, r.content.contains("leeme.md"))
    }

    @Test
    fun elFiltroPorGlobLimitaLosArchivos() {
        assumeTrue(env.packaged("rg").exists())

        val r = grep("pattern" to "buscame", "glob" to "*.kt")

        assertTrue(r.content, r.content.contains("uno.kt"))
        assertFalse("el glob deberia excluir el markdown", r.content.contains("leeme.md"))
    }

    @Test
    fun sinCoincidenciasNoEsUnError() {
        assumeTrue(env.packaged("rg").exists())

        val r = grep("pattern" to "estonoexistenadaquever")

        assertFalse("salir con 1 por no encontrar no es un fallo", r.isError)
        assertTrue(r.content, r.content.contains("Sin coincidencias"))
    }

    @Test
    fun laSalidaTraeArchivoYLinea() {
        assumeTrue(env.packaged("rg").exists())

        val r = grep("pattern" to "val x")

        assertTrue(r.content, Regex("""dos\.kt:\d+""").containsMatchIn(r.content))
    }
}
