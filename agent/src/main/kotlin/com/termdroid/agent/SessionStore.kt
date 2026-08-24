package com.termdroid.agent

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

data class SessionMeta(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val tokensIn: Long = 0,
    val tokensOut: Long = 0,
    val cacheRead: Long = 0,
)

data class StoredSession(val meta: SessionMeta, val messages: List<Msg>)

class SessionStore(private val dir: File) {

    init {
        dir.mkdirs()
    }

    fun list(): List<SessionMeta> = (dir.listFiles { f -> f.extension == "json" } ?: emptyArray())
        .mapNotNull { runCatching { readMeta(it) }.getOrNull() }
        .sortedByDescending { it.updatedAt }

    fun load(id: String): StoredSession? = runCatching {
        val json = JSONObject(fileFor(id).readText())
        StoredSession(metaOf(json), MessageCodec.decodeMessages(json.getJSONArray("messages")))
    }.getOrNull()

    fun save(session: StoredSession) {
        val json = JSONObject()
            .put("id", session.meta.id)
            .put("title", session.meta.title)
            .put("updatedAt", session.meta.updatedAt)
            .put("tokensIn", session.meta.tokensIn)
            .put("tokensOut", session.meta.tokensOut)
            .put("cacheRead", session.meta.cacheRead)
            .put("messages", MessageCodec.encodeMessages(session.messages))

        val tmp = File(dir, "${session.meta.id}.json.tmp")
        tmp.writeText(json.toString())
        try {
            Files.move(
                tmp.toPath(),
                fileFor(session.meta.id).toPath(),
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (e: IOException) {
            tmp.delete()
            throw e
        }
    }

    fun delete(id: String) {
        fileFor(id).delete()
    }

    private fun fileFor(id: String) = File(dir, "$id.json")

    private fun readMeta(f: File) = metaOf(JSONObject(f.readText()))

    private fun metaOf(json: JSONObject) = SessionMeta(
        id = json.getString("id"),
        title = json.optString("title"),
        updatedAt = json.optLong("updatedAt"),
        tokensIn = json.optLong("tokensIn"),
        tokensOut = json.optLong("tokensOut"),
        cacheRead = json.optLong("cacheRead"),
    )
}

object MessageCodec {

    fun encodeMessages(messages: List<Msg>): JSONArray =
        JSONArray(messages.map { encodeMessage(it) })

    fun decodeMessages(array: JSONArray): List<Msg> =
        (0 until array.length()).map { decodeMessage(array.getJSONObject(it)) }

    fun encodeMessage(msg: Msg): JSONObject = JSONObject()
        .put("role", msg.role.name)
        .put("blocks", JSONArray(msg.blocks.map { encodeBlock(it) }))

    fun decodeMessage(json: JSONObject): Msg {
        val blocks = json.getJSONArray("blocks")
        return Msg(
            role = Role.valueOf(json.getString("role")),
            blocks = (0 until blocks.length()).map { decodeBlock(blocks.getJSONObject(it)) },
        )
    }

    fun encodeBlock(block: Block): JSONObject = when (block) {
        is Block.Text -> JSONObject().put("type", "text").put("text", block.text)

        is Block.Thinking -> JSONObject()
            .put("type", "thinking")
            .put("text", block.text)
            .putOpt("signature", block.signature)

        is Block.ToolUse -> JSONObject()
            .put("type", "tool_use")
            .put("id", block.id)
            .put("name", block.name)
            .put("input", block.input)

        is Block.ToolResult -> JSONObject()
            .put("type", "tool_result")
            .put("tool_use_id", block.toolUseId)
            .put("content", block.content)
            .put("is_error", block.isError)

        is Block.Opaque -> JSONObject().put("type", "opaque").put("raw", block.raw)
    }

    fun decodeBlock(json: JSONObject): Block = when (val type = json.getString("type")) {
        "text" -> Block.Text(json.getString("text"))
        "thinking" -> Block.Thinking(
            json.getString("text"),
            if (json.isNull("signature")) null else json.getString("signature"),
        )
        "tool_use" -> Block.ToolUse(
            json.getString("id"),
            json.getString("name"),
            json.getJSONObject("input"),
        )
        "tool_result" -> Block.ToolResult(
            json.getString("tool_use_id"),
            json.getString("content"),
            json.optBoolean("is_error"),
        )
        else -> Block.Opaque(json.optString("raw").ifBlank { json.toString() })
    }
}
