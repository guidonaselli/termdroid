package com.termdroid.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MessageCodecTest {

    private fun roundTrip(block: Block): Block =
        MessageCodec.decodeBlock(MessageCodec.encodeBlock(block))

    @Test
    fun textoSobreviveElViaje() {
        val b = Block.Text("hola\ncon salto y \"comillas\"")
        assertEquals(b, roundTrip(b))
    }

    @Test
    fun thinkingConservaLaFirma() {
        val b = Block.Thinking("razonando", signature = "abc123")
        assertEquals(b, roundTrip(b))
    }

    @Test
    fun thinkingSinFirmaSigueSinFirma() {
        val b = Block.Thinking("sin firma")
        val r = roundTrip(b) as Block.Thinking
        assertEquals("sin firma", r.text)
        assertNull(r.signature)
    }

    @Test
    fun toolUseConservaIdNombreYEntradaAnidada() {
        val input = JSONObject().put("path", "a/b.txt").put("nested", JSONObject().put("n", 3))
        val r = roundTrip(Block.ToolUse("t1", "read_file", input)) as Block.ToolUse

        assertEquals("t1", r.id)
        assertEquals("read_file", r.name)
        assertEquals("a/b.txt", r.input.getString("path"))
        assertEquals(3, r.input.getJSONObject("nested").getInt("n"))
    }

    @Test
    fun toolResultConservaLaMarcaDeError() {
        val b = Block.ToolResult("t1", "fallo feo", isError = true)
        assertEquals(b, roundTrip(b))

        val ok = Block.ToolResult("t2", "salida", isError = false)
        assertEquals(ok, roundTrip(ok))
    }

    @Test
    fun elBloqueOpacoSobreviveIntacto() {
        val b = Block.Opaque("""{"type":"compaction","data":"xyz"}""")
        assertEquals(b, roundTrip(b))
    }

    @Test
    fun unTipoDesconocidoSeConservaComoOpaco() {
        val futuro = JSONObject().put("type", "algo_nuevo").put("campo", "valor")
        val r = MessageCodec.decodeBlock(futuro)

        assertTrue(r is Block.Opaque)
        assertTrue((r as Block.Opaque).raw.contains("algo_nuevo"))
        assertTrue(r.raw.contains("valor"))
    }

    @Test
    fun unMensajeCompletoSobreviveConTodosSusBloques() {
        val msg = Msg.assistant(
            listOf(
                Block.Thinking("pienso", "sig"),
                Block.Text("respondo"),
                Block.ToolUse("t1", "bash", JSONObject().put("command", "ls")),
                Block.Opaque("""{"type":"compaction"}"""),
            ),
        )

        val r = MessageCodec.decodeMessage(MessageCodec.encodeMessage(msg))

        assertEquals(Role.ASSISTANT, r.role)
        assertEquals(4, r.blocks.size)
        assertTrue(r.blocks[0] is Block.Thinking)
        assertTrue(r.blocks[2] is Block.ToolUse)
        assertTrue(r.blocks[3] is Block.Opaque)
    }
}

class SessionStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private lateinit var store: SessionStore

    @Before
    fun setUp() {
        store = SessionStore(temp.newFolder("sesiones"))
    }

    private fun session(id: String, title: String = "charla") = StoredSession(
        meta = SessionMeta(id, title, updatedAt = 1000, tokensIn = 10, tokensOut = 20, cacheRead = 5),
        messages = listOf(
            Msg.user("hola"),
            Msg.assistant(listOf(Block.Thinking("mmm", "s"), Block.Text("que tal"))),
        ),
    )

    @Test
    fun guardarYCargar() {
        store.save(session("s1"))
        val cargada = store.load("s1")!!

        assertEquals("s1", cargada.meta.id)
        assertEquals("charla", cargada.meta.title)
        assertEquals(10, cargada.meta.tokensIn)
        assertEquals(2, cargada.messages.size)
        assertTrue(cargada.messages[1].blocks[0] is Block.Thinking)
    }

    @Test
    fun cargarAlgoQueNoExisteEsNull() {
        assertNull(store.load("no-existe"))
    }

    @Test
    fun listarDevuelveLaMasRecientePrimero() {
        store.save(session("vieja").copy(meta = SessionMeta("vieja", "v", updatedAt = 100)))
        store.save(session("nueva").copy(meta = SessionMeta("nueva", "n", updatedAt = 900)))

        assertEquals(listOf("nueva", "vieja"), store.list().map { it.id })
    }

    @Test
    fun sobrescribirReemplaza() {
        store.save(session("s1", "primera"))
        store.save(session("s1", "segunda"))

        assertEquals("segunda", store.load("s1")!!.meta.title)
        assertEquals(1, store.list().size)
    }

    @Test
    fun borrar() {
        store.save(session("s1"))
        store.delete("s1")

        assertNull(store.load("s1"))
        assertTrue(store.list().isEmpty())
    }

    @Test
    fun unArchivoCorruptoNoRompeElListado() {
        store.save(session("buena"))
        temp.root.resolve("sesiones/rota.json").writeText("{ esto no es json")

        val listado = store.list()

        assertEquals(1, listado.size)
        assertEquals("buena", listado.single().id)
    }

    @Test
    fun noDejaArchivosTemporales() {
        store.save(session("s1"))
        val sobrantes = temp.root.resolve("sesiones").listFiles()!!.filter { it.name.endsWith(".tmp") }
        assertTrue("quedaron temporales: $sobrantes", sobrantes.isEmpty())
    }
}
