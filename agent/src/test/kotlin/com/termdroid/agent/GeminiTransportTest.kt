package com.termdroid.agent

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeminiTransportTest {

    private val transport = GeminiTransport(token = "AIzaSyTest", model = "gemini-2.5-flash")

    private val tool = ToolSpec(
        name = "battery_status",
        description = "Gets battery info",
        inputSchema = objectSchema(),
    )

    @Test
    fun payloadGeminiFormateaInstruccionSistemaYTools() {
        val payload = transport.buildPayload(
            system = "System prompt gemini",
            tools = listOf(tool),
            messages = listOf(
                Msg.user("dame bateria"),
                Msg.assistant(
                    listOf(
                        Block.ToolUse("call_gemini_1", "battery_status", JSONObject()),
                    ),
                ),
                Msg.userBlocks(
                    listOf(
                        Block.ToolResult("call_gemini_1", "85% charging", isError = false),
                    ),
                ),
            ),
        )

        assertTrue(payload.has("system_instruction"))
        val sysText = payload.getJSONObject("system_instruction")
            .getJSONArray("parts").getJSONObject(0).getString("text")
        assertEquals("System prompt gemini", sysText)

        val contents = payload.getJSONArray("contents")
        assertEquals(3, contents.length())
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))
        assertEquals("user", contents.getJSONObject(2).getString("role"))

        assertTrue(payload.has("tools"))
        val decls = payload.getJSONArray("tools").getJSONObject(0).getJSONArray("function_declarations")
        assertEquals(1, decls.length())
        assertEquals("battery_status", decls.getJSONObject(0).getString("name"))
    }
}
