package com.termdroid.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenAiTransportTest {

    private val transport = OpenAiTransport(token = "sk-test", model = "gpt-4o")

    private val tool = ToolSpec(
        name = "exec_command",
        description = "Runs shell command",
        inputSchema = objectSchema(
            "cmd" to stringProp("Command line"),
            required = listOf("cmd"),
        ),
    )

    @Test
    fun payloadContieneSystemMensajesYTools() {
        val payload = transport.buildPayload(
            system = "Eres un asistente util",
            tools = listOf(tool),
            messages = listOf(
                Msg.user("ls"),
                Msg.assistant(
                    listOf(
                        Block.ToolUse("call_1", "exec_command", JSONObject().put("cmd", "ls -la")),
                    ),
                ),
                Msg.userBlocks(
                    listOf(
                        Block.ToolResult("call_1", "file1.txt\nfile2.txt", isError = false),
                    ),
                ),
            ),
        )

        assertEquals("gpt-4o", payload.getString("model"))
        assertTrue(payload.getBoolean("stream"))

        val msgs = payload.getJSONArray("messages")
        assertEquals(4, msgs.length())
        assertEquals("system", msgs.getJSONObject(0).getString("role"))
        assertEquals("user", msgs.getJSONObject(1).getString("role"))
        assertEquals("assistant", msgs.getJSONObject(2).getString("role"))
        assertEquals("tool", msgs.getJSONObject(3).getString("role"))

        val tools = payload.getJSONArray("tools")
        assertEquals(1, tools.length())
        val t = tools.getJSONObject(0)
        assertEquals("function", t.getString("type"))
        assertEquals("exec_command", t.getJSONObject("function").getString("name"))
    }
}
