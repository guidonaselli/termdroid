package com.termdroid.terminal

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.exec.ExecBackend
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica el camino completo: PTY -> parser -> pantalla, contra un shell real.
 *
 * Un test unitario del parser no alcanza: lo que puede romperse aca es la union
 * de las piezas, y eso solo se ve corriendo un shell de verdad.
 */
class ShellSessionTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var session: ShellSession? = null

    @After
    fun tearDown() {
        session?.stop()
        scope.cancel()
    }

    /** Espera a que la pantalla contenga [needle], o se rinde. */
    private suspend fun waitForScreen(s: ShellSession, needle: String, timeoutMs: Long = 6000): Boolean =
        withTimeoutOrNull(timeoutMs) {
            while (!s.screen.value.text().contains(needle)) delay(50)
            true
        } ?: false

    @Test
    fun elShellArrancaYRespondeUnComando() = runBlocking {
        val s = ShellSession(context, ExecBackend.NATIVE_LIB_DIR, scope).also { session = it }
        s.start(rows = 24, cols = 80)

        s.send("echo TERMDROID_VIVE\n")

        val visto = waitForScreen(s, "TERMDROID_VIVE")
        assertTrue("no aparecio la salida. Pantalla:\n${s.screen.value.text()}", visto)
    }

    /**
     * Cero friccion: esto tiene que andar sin ningun rootfs instalado, porque
     * Android ya trae un entorno POSIX usable. Ver 10_TECH/ONBOARDING.md.
     */
    @Test
    fun andaSinRootfsUsandoLoQueTraeAndroid() = runBlocking {
        val s = ShellSession(context, ExecBackend.NATIVE_LIB_DIR, scope).also { session = it }
        s.start()

        s.send("uname -o; echo MARCA-\$((6*7))\n")

        val visto = waitForScreen(s, "MARCA-42")
        assertTrue("toybox deberia alcanzar. Pantalla:\n${s.screen.value.text()}", visto)
    }

    /**
     * El color que emite un programa tiene que llegar a la celda, no al texto.
     *
     * No se puede afirmar sobre la pantalla entera: el terminal hace eco de lo
     * que uno tipea, y el comando tipeado contiene los caracteres `\033[1;32m`
     * como texto literal. Lo que se verifica es la linea que **produjo** el
     * programa, y sobre ella el estilo de la celda.
     */
    @Test
    fun elColorEmitidoLlegaALaCelda() = runBlocking {
        val s = ShellSession(context, ExecBackend.NATIVE_LIB_DIR, scope).also { session = it }
        s.start()

        // El ESC lo genera printf, no se manda crudo: el line editor del shell
        // interpreta un ESC entrante como el inicio de una tecla y se come la
        // secuencia antes de que llegue a ejecutarse el comando.
        s.send("printf '\\033[1;32mVERDE\\033[0m\\n'\n")

        assertTrue(waitForScreen(s, "VERDE"))

        val screen = s.screen.value
        // La linea de salida es la que tiene VERDE al principio, no la del eco.
        val row = (0 until screen.rows).firstOrNull { r ->
            String(CharArray(screen.cols) { screen.cells[r][it].char }).trimEnd() == "VERDE"
        }
        assertTrue("no aparecio VERDE en una linea propia:\n${screen.text()}", row != null)

        val cell = screen.cells[row!!][0]
        assertEquals("el verde no llego a la celda", TermColor.Indexed(2), cell.style.fg)
        assertTrue("la negrita no llego a la celda", cell.style.bold)
    }

    /** Cambiar el tamano tiene que llegar al proceso, no solo a la vista. */
    @Test
    fun elResizeLlegaAlProceso() = runBlocking {
        val s = ShellSession(context, ExecBackend.NATIVE_LIB_DIR, scope).also { session = it }
        s.start(rows = 24, cols = 80)
        delay(400)

        s.resize(30, 100)
        delay(200)
        s.send("stty size\n")

        val visto = waitForScreen(s, "30 100")
        assertTrue("el proceso no vio el tamano nuevo. Pantalla:\n${s.screen.value.text()}", visto)
    }

    /**
     * Regresion: cerrar una sesion dejaba al hijo como zombie.
     *
     * Matar no alcanza, hay que cosecharlo. Se veia como entradas en estado Z
     * acumulandose en la tabla de procesos de la app.
     */
    @Test
    fun cerrarLaSesionNoDejaZombies() = runBlocking {
        val antes = contarZombies()
        repeat(3) {
            val s = ShellSession(context, ExecBackend.NATIVE_LIB_DIR, scope)
            s.start()
            delay(200)
            s.stop()
            delay(200)
        }
        delay(500)
        val despues = contarZombies()
        assertTrue("quedaron zombies: antes=$antes despues=$despues", despues <= antes)
    }

    /**
     * Regresion: el hilo lector del PTY y el de UI tocaban el buffer a la vez.
     *
     * Pasa de verdad cada vez que se abre el teclado: la vista se achica y llama
     * a resize mientras el shell sigue escribiendo. Sin serializar, la pantalla
     * quedaba vacia o inconsistente.
     */
    @Test
    fun redimensionarMientrasLlegaSalidaNoRompeLaPantalla() = runBlocking {
        val s = ShellSession(context, ExecBackend.NATIVE_LIB_DIR, scope).also { session = it }
        s.start(rows = 24, cols = 80)
        delay(300)

        // Genera salida continua mientras la vista cambia de tamano.
        s.send("i=0; while [ \$i -lt 60 ]; do echo LINEA-\$i; i=\$((i+1)); done\n")

        repeat(12) { n ->
            s.resize(if (n % 2 == 0) 12 else 28, if (n % 2 == 0) 40 else 90)
            delay(40)
        }

        s.send("echo FIN_DE_LA_PRUEBA\n")
        val visto = waitForScreen(s, "FIN_DE_LA_PRUEBA")
        assertTrue("la sesion no sobrevivio al resize concurrente:\n${s.screen.value.text()}", visto)

        // La pantalla tiene que seguir siendo coherente con su propio tamano.
        val screen = s.screen.value
        assertEquals(screen.rows, screen.cells.size)
        screen.cells.forEach { fila -> assertEquals(screen.cols, fila.size) }
    }

    /** Cuenta hijos zombie del proceso de test. */
    private fun contarZombies(): Int = runCatching {
        java.io.File("/proc").listFiles()
            ?.filter { it.name.toIntOrNull() != null }
            ?.count { dir ->
                runCatching {
                    val stat = java.io.File(dir, "stat").readText()
                    val campos = stat.substringAfterLast(')').trim().split(' ')
                    campos.getOrNull(0) == "Z" && campos.getOrNull(1) == android.os.Process.myPid().toString()
                }.getOrDefault(false)
            } ?: 0
    }.getOrDefault(0)

    @Test
    fun laSesionTerminaCuandoElShellSale() = runBlocking {
        val s = ShellSession(context, ExecBackend.NATIVE_LIB_DIR, scope).also { session = it }
        s.start()
        assertTrue(s.alive.value)

        s.send("exit\n")
        val murio = withTimeoutOrNull(5000) {
            while (s.alive.value) delay(50)
            true
        } ?: false
        assertTrue("la sesion quedo viva despues de exit", murio)
    }
}
