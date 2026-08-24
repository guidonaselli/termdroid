package com.termdroid

import androidx.test.platform.app.InstrumentationRegistry
import com.termdroid.agent.Block
import com.termdroid.agent.MessageCodec
import com.termdroid.agent.Msg
import com.termdroid.agent.Role
import com.termdroid.agent.SessionMeta
import com.termdroid.agent.SessionStore
import com.termdroid.agent.StoredSession
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class SessionPersistenceTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var dir: File
    private lateinit var store: SessionStore

    @Before
    fun setUp() {
        dir = File(context.cacheDir, "sesiones-test").apply { deleteRecursively() }
        store = SessionStore(dir)
    }

    private val mensajes = listOf(
        Msg.user("como estas"),
        Msg.assistant(
            listOf(
                Block.Thinking("pensando", "firma-xyz"),
                Block.Text("bien"),
                Block.ToolUse("t1", "bash", JSONObject().put("command", "ls -la")),
                Block.Opaque("""{"type":"compaction","n":1}"""),
            ),
        ),
        Msg.userBlocks(listOf(Block.ToolResult("t1", "total 0", isError = false))),
    )

    @Test
    fun laConversacionSobreviveUnCicloCompleto() {
        store.save(
            StoredSession(
                SessionMeta("s1", "titulo", updatedAt = 5, tokensIn = 7, tokensOut = 9, cacheRead = 3),
                mensajes,
            ),
        )

        val cargada = store.load("s1")!!

        assertEquals(3, cargada.messages.size)
        assertEquals(7, cargada.meta.tokensIn)

        val asistente = cargada.messages[1]
        assertEquals(Role.ASSISTANT, asistente.role)
        assertEquals(4, asistente.blocks.size)

        val thinking = asistente.blocks[0] as Block.Thinking
        assertEquals("firma-xyz", thinking.signature)

        val use = asistente.blocks[2] as Block.ToolUse
        assertEquals("ls -la", use.input.getString("command"))

        val opaco = asistente.blocks[3] as Block.Opaque
        assertTrue(opaco.raw.contains("compaction"))

        val resultado = cargada.messages[2].blocks[0] as Block.ToolResult
        assertEquals("t1", resultado.toolUseId)
    }

    @Test
    fun sobrescribirUnaSesionExistenteFunciona() {
        val meta = SessionMeta("s1", "primera", updatedAt = 1)
        store.save(StoredSession(meta, mensajes))
        store.save(StoredSession(meta.copy(title = "segunda", updatedAt = 2), mensajes))

        assertEquals("segunda", store.load("s1")!!.meta.title)
        assertEquals(1, store.list().size)
    }

    @Test
    fun borrarUnaSesionLaSaca() {
        store.save(StoredSession(SessionMeta("s1", "x", 1), mensajes))
        store.delete("s1")
        assertNull(store.load("s1"))
    }

    @Test
    fun elCodecEsSimetricoEnAndroid() {
        val original = mensajes[1]
        val vuelta = MessageCodec.decodeMessage(MessageCodec.encodeMessage(original))

        assertEquals(original.role, vuelta.role)
        assertEquals(original.blocks.size, vuelta.blocks.size)
        assertEquals(original.blocks[1], vuelta.blocks[1])
        assertEquals(original.blocks[3], vuelta.blocks[3])
    }
}
