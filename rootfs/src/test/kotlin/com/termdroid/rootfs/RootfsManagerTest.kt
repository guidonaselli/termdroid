package com.termdroid.rootfs

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class RootfsManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var nativeLibDir: File
    private lateinit var manager: RootfsManager

    @Before
    fun setUp() {
        filesDir = tempFolder.newFolder("files")
        nativeLibDir = tempFolder.newFolder("lib")
        manager = RootfsManager(filesDir = filesDir, nativeLibDir = nativeLibDir)
    }

    @Test
    fun inicializaEstructuraDeDirectoriosBase() {
        assertFalse(manager.isBaseInstalled)
        val ok = manager.ensureBaseEnvironment()
        assertTrue(ok)
        assertTrue(manager.isBaseInstalled)

        assertTrue(File(manager.binDir, "termdroid").exists())
        assertTrue(manager.homeDir.exists())
        assertTrue(manager.tmpDir.exists())
        assertTrue(manager.libDir.exists())
    }

    @Test
    fun envuelveHerramientasPrecompiladasSiExisten() {
        File(nativeLibDir, "librg.so").writeText("fake-rg")
        manager.ensureBaseEnvironment()

        val rg = File(manager.binDir, "rg")
        assertTrue(rg.exists())
        assertTrue(rg.readText().contains("librg.so"))
    }

    @Test
    fun instalaWrapperDeClaude() {
        val claude = manager.installClaudeWrapper()
        assertTrue(claude.exists())
        assertFalse(manager.hasClaude)
        assertTrue(claude.readText().contains("claude"))
        assertFalse(claude.readText().contains("exec \"${manager.binDir.absolutePath}/claude\""))
    }

    @Test
    fun wrappersNoCuentanComoRuntimeInstalado() {
        manager.ensureBaseEnvironment()

        assertFalse(manager.hasNode)
        assertFalse(manager.hasClaude)
    }

    @Test
    fun instalacionSeDetieneSiFallaLaPreparacion() {
        var installs = 0
        var validations = 0

        val result = NodeInstaller.installEnvironment(
            prepare = { error("fallo de descarga") },
            install = { installs++ },
            validate = { validations++ },
        )

        assertTrue(result.isFailure)
        assertEquals(0, installs)
        assertEquals(0, validations)
    }

    @Test
    fun instalacionPuedeReintentarse() {
        var prepares = 0
        var installs = 0
        var validations = 0

        repeat(2) {
            assertTrue(NodeInstaller.installEnvironment(
                prepare = { prepares++ },
                install = { installs++ },
                validate = { validations++ },
            ).isSuccess)
        }

        assertEquals(2, prepares)
        assertEquals(2, installs)
        assertEquals(2, validations)
    }

    @Test
    fun inicializaWrappersDeHardware() {
        manager.ensureBaseEnvironment()
        assertTrue(File(manager.binDir, "termdroid-clipboard").exists())
        assertTrue(File(manager.binDir, "termdroid-battery").exists())
        assertTrue(File(manager.binDir, "termdroid-tts").exists())
    }

    @Test
    fun reseteaEntornoLimpiandoArchivos() {
        manager.ensureBaseEnvironment()
        File(manager.homeDir, "test.txt").writeText("data")
        assertTrue(File(manager.homeDir, "test.txt").exists())

        manager.resetEnvironment()
        assertFalse(File(manager.homeDir, "test.txt").exists())
        assertTrue(manager.isBaseInstalled)
    }
}
