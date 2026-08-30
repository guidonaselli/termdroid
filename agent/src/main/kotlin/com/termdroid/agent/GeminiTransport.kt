package com.termdroid.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Transporte nativo para Google Gemini (Gemini Flash, Pro, Google AI Studio y Google OAuth). */
class GeminiTransport(
    private val token: String,
    private val model: String = "gemini-2.5-flash",
    private val baseUrl: String = "https://generativelanguage.googleapis.com",
) : Transport {

    override fun stream(
        system: String,
        tools: List<ToolSpec>,
        messages: List<Msg>,
    ): Flow<StreamEvent> = flow {
        val urlString = if (token.startsWith("AIzaSy")) {
            "/v1beta/models/=sse&key="
        } else {
            "/v1beta/models/=sse"
        }

        val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            if (!token.startsWith("AIzaSy") && token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ")
            }
            connectTimeout = 30_000
            readTimeout = 120_000
        }

        val payload = buildPayload(system, tools, messages)
        conn.outputStream.use { os ->
            os.write(payload.toString().toByteArray(Charsets.UTF_8))
        }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use(BufferedReader::readText) ?: "HTTP "
            conn.disconnect()
            error("Error de conexion con Gemini (): ")
        }

        val textBuilder = StringBuilder()
        val thinkingBuilder = StringBuilder()
        val toolUses = mutableListOf<Block.ToolUse>()
        var stopReason = StopReason.END_TURN
        var promptTokens = 0L
        var completionTokens = 0L

        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty() || data == "[DONE]") continue

                val json = runCatching { JSONObject(data) }.getOrNull() ?: continue

                json.optJSONObject("usageMetadata")?.let { u ->
                    promptTokens = u.optLong("promptTokenCount", promptTokens)
                    completionTokens = u.optLong("candidatesTokenCount", completionTokens)
                }

                val candidates = json.optJSONArray("candidates") ?: continue
                if (candidates.length() == 0) continue
                val candidate = candidates.getJSONObject(0)

                val finishReason = candidate.optString("finishReason", "")
                if (finishReason == "STOP") stopReason = StopReason.END_TURN
                else if (finishReason == "MAX_TOKENS") stopReason = StopReason.MAX_TOKENS

                val content = candidate.optJSONObject("content") ?: continue
                val parts = content.optJSONArray("parts") ?: continue

                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.has("text")) {
                        val text = part.getString("text")
                        if (part.optBoolean("thought", false)) {
                            thinkingBuilder.append(text)
                            emit(StreamEvent.Thinking(text))
                        } else {
                            textBuilder.append(text)
                            emit(StreamEvent.Text(text))
                        }
                    }

                    if (part.has("functionCall")) {
                        val fn = part.getJSONObject("functionCall")
                        val fnName = fn.optString("name", "")
                        val fnArgs = fn.optJSONObject("args") ?: JSONObject()
                        val callId = "call_gemini_" + System.currentTimeMillis() + "_"
                        toolUses += Block.ToolUse(id = callId, name = fnName, input = fnArgs)
                        stopReason = StopReason.TOOL_USE
                    }
                }
            }
        }
        conn.disconnect()

        val blocks = mutableListOf<Block>()
        if (thinkingBuilder.isNotEmpty()) {
            blocks += Block.Thinking(thinkingBuilder.toString())
        }
        if (textBuilder.isNotEmpty()) {
            blocks += Block.Text(textBuilder.toString())
        }
        blocks.addAll(toolUses)

        emit(
            StreamEvent.Final(
                TurnResult(
                    blocks = blocks,
                    stopReason = stopReason,
                    usage = Usage(inputTokens = promptTokens, outputTokens = completionTokens),
                ),
            ),
        )
    }.flowOn(Dispatchers.IO)

    internal fun buildPayload(
        system: String,
        tools: List<ToolSpec>,
        messages: List<Msg>,
    ): JSONObject {
        val payload = JSONObject()

        if (system.isNotBlank()) {
            payload.put(
                "system_instruction",
                JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))),
            )
        }

        val contents = JSONArray()
        for (msg in messages) {
            val role = when (msg.role) {
                Role.USER, Role.SYSTEM -> "user"
                Role.ASSISTANT -> "model"
            }

            val parts = JSONArray()
            for (block in msg.blocks) {
                when (block) {
                    is Block.Text -> parts.put(JSONObject().put("text", block.text))
                    is Block.ToolUse -> {
                        parts.put(
                            JSONObject().put(
                                "functionCall",
                                JSONObject()
                                    .put("name", block.name)
                                    .put("args", block.input),
                            ),
                        )
                    }
                    is Block.ToolResult -> {
                        parts.put(
                            JSONObject().put(
                                "functionResponse",
                                JSONObject()
                                    .put("name", block.toolUseId)
                                    .put("response", JSONObject().put("content", block.content)),
                            ),
                        )
                    }
                    is Block.Thinking, is Block.Opaque -> Unit
                }
            }
            if (parts.length() > 0) {
                contents.put(JSONObject().put("role", role).put("parts", parts))
            }
        }
        payload.put("contents", contents)

        if (tools.isNotEmpty()) {
            val fnDecls = JSONArray()
            for (t in tools) {
                fnDecls.put(
                    JSONObject()
                        .put("name", t.name)
                        .put("description", t.description)
                        .put("parameters", t.inputSchema),
                )
            }
            payload.put("tools", JSONArray().put(JSONObject().put("function_declarations", fnDecls)))
        }

        return payload
    }
}
