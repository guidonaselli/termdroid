package com.termdroid.exec

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ExecutorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var nativeLibDir: File
    private lateinit var env: ExecEnvironment

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        nativeLibDir = tempFolder.newFolder("lib")
        env = ExecEnvironment(nativeLibDir = nativeLibDir, filesDir = filesDir)
    }

    @Test
    fun directBackendUsaElArchivoDirecto() {
        val executor = Executor(env, ExecBackend.DIRECT)
        val bin = File(filesDir, "script.sh")
        val argv = executor.buildArgv(bin, listOf("-a", "-b"))

        assertEquals(listOf(bin.absolutePath, "-a", "-b"), argv)
    }

    @Test
    fun linkerBackendEnvuelveConLinker() {
        val executor = Executor(env, ExecBackend.LINKER)
        val bin = File(filesDir, "my_elf")
        val argv = executor.buildArgv(bin, listOf("arg1"))

        assertEquals(listOf(env.linker.absolutePath, bin.absolutePath, "arg1"), argv)
    }

    @Test
    fun nativeLibDirNoSeEnvuelveConLinker() {
        val executor = Executor(env, ExecBackend.LINKER)
        val bin = File(nativeLibDir, "librg.so")
        val argv = executor.buildArgv(bin, listOf("--version"))

        assertEquals(listOf(bin.absolutePath, "--version"), argv)
    }

    @Test
    fun resuelveShebangSimple() {
        val executor = Executor(env, ExecBackend.DIRECT)
        val script = File(filesDir, "run.sh").apply {
            writeText("#!/system/bin/sh\necho hello\n")
        }

        val argv = executor.buildArgv(script, listOf("extra"))
        assertEquals(listOf(File("/system/bin/sh").absolutePath, script.absolutePath, "extra"), argv)
    }

    @Test
    fun resuelveShebangConArgumentos() {
        val executor = Executor(env, ExecBackend.DIRECT)
        val script = File(filesDir, "run_x.sh").apply {
            writeText("#!/system/bin/sh -e\necho hello\n")
        }

        val argv = executor.buildArgv(script)
        assertEquals(listOf(File("/system/bin/sh").absolutePath, "-e", script.absolutePath), argv)
    }

    @Test
    fun supportsRuntimeInstallEsCorrecto() {
        assertTrue(ExecBackend.DIRECT.supportsRuntimeInstall)
        assertTrue(ExecBackend.LINKER.supportsRuntimeInstall)
        assertFalse(ExecBackend.NATIVE_LIB_DIR.supportsRuntimeInstall)
        assertFalse(ExecBackend.NONE.supportsRuntimeInstall)
    }
}
