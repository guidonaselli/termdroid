package com.termdroid.tools.unix

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.exec.ExecBackend
import com.termdroid.exec.ExecEnvironment
import com.termdroid.exec.Executor
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class PrebuiltBinariesTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val env get() = ExecEnvironment(context)
    private val executor get() = Executor(env, ExecBackend.NATIVE_LIB_DIR)

    private fun binario(nombre: String): File? = env.packaged(nombre).takeIf { it.exists() }

    @Test
    fun losBinariosQueViajanSonEjecutablesDeVerdad() {
        val presentes = listOf("rg", "jaq", "gix").mapNotNull { binario(it) }
        assumeTrue("no viaja ningun binario prebuilt", presentes.isNotEmpty())

        presentes.forEach { bin ->
            val r = executor.run(bin, listOf("--version"))
            assertTrue(
                "${bin.name} no respondio a --version: ${r.output.take(200)}",
                r.output.isNotBlank(),
            )
        }
    }

    @Test
    fun jaqProcesaJson() {
        val jaq = binario("jaq") ?: return
        val r = executor.run(jaq, listOf("-n", "1+1"))
        assertTrue(r.output, r.output.contains("2"))
    }

    @Test
    fun gixRespondeAUnSubcomando() {
        val gix = binario("gix") ?: return
        val r = executor.run(gix, listOf("--version"))
        assertTrue(r.output, r.output.isNotBlank())
    }
}
