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

/** Transporte compatible con OpenAI, ChatGPT OAuth, Ollama, LM Studio y LiteLLM. */
class OpenAiTransport(
    private val token: String = "",
    private val baseUrl: String = "https://api.openai.com/v1",
    private val model: String = "gpt-4o",
) : Transport {

    override fun stream(
        system: String,
        tools: List<ToolSpec>,
        messages: List<Msg>,
    ): Flow<StreamEvent> = flow {
        val endpoint = URL("/chat/completions")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "text/event-stream")
            if (token.isNotBlank()) {
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
            error("Error de conexion con OpenAI/Endpoint (): ")
        }

        val toolCallsMap = mutableMapOf<Int, MutableToolCall>()
        val textBuilder = StringBuilder()
        val thinkingBuilder = StringBuilder()
        var stopReason = StopReason.END_TURN
        var promptTokens = 0L
        var completionTokens = 0L

        BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break

                val json = runCatching { JSONObject(data) }.getOrNull() ?: continue

                json.optJSONObject("usage")?.let { u ->
                    promptTokens = u.optLong("prompt_tokens", promptTokens)
                    completionTokens = u.optLong("completion_tokens", completionTokens)
                }

                val choices = json.optJSONArray("choices") ?: continue
                if (choices.length() == 0) continue
                val choice = choices.getJSONObject(0)

                val finishReason = choice.optString("finish_reason", "")
                if (finishReason == "tool_calls") stopReason = StopReason.TOOL_USE
                else if (finishReason == "length") stopReason = StopReason.MAX_TOKENS

                val delta = choice.optJSONObject("delta") ?: continue

                val content = delta.optString("content", "")
                if (content.isNotEmpty()) {
                    textBuilder.append(content)
                    emit(StreamEvent.Text(content))
                }

                val reasoning = delta.optString("reasoning_content", "")
                if (reasoning.isNotEmpty()) {
                    thinkingBuilder.append(reasoning)
                    emit(StreamEvent.Thinking(reasoning))
                }

                val toolCalls = delta.optJSONArray("tool_calls")
                if (toolCalls != null) {
                    for (i in 0 until toolCalls.length()) {
                        val tc = toolCalls.getJSONObject(i)
                        val index = tc.optInt("index", i)
                        val id = tc.optString("id", "")
                        val fn = tc.optJSONObject("function")
                        val fnName = fn?.optString("name", "") ?: ""
                        val fnArgs = fn?.optString("arguments", "") ?: ""

                        val entry = toolCallsMap.getOrPut(index) {
                            MutableToolCall(id = id, name = fnName, args = StringBuilder())
                        }
                        if (id.isNotEmpty()) entry.id = id
                        if (fnName.isNotEmpty()) entry.name = fnName
                        if (fnArgs.isNotEmpty()) entry.args.append(fnArgs)
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

        if (toolCallsMap.isNotEmpty()) {
            stopReason = StopReason.TOOL_USE
            for ((_, tc) in toolCallsMap) {
                val inputJson = runCatching { JSONObject(tc.args.toString()) }.getOrElse { JSONObject() }
                blocks += Block.ToolUse(
                    id = tc.id.ifEmpty { "call_" + System.currentTimeMillis() },
                    name = tc.name,
                    input = inputJson,
                )
            }
        }

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
            .put("model", model)
            .put("stream", true)
            .put("stream_options", JSONObject().put("include_usage", true))

        val msgs = JSONArray()
        if (system.isNotBlank()) {
            msgs.put(JSONObject().put("role", "system").put("content", system))
        }

        for (msg in messages) {
            when (msg.role) {
                Role.SYSTEM -> msgs.put(
                    JSONObject().put("role", "system").put("content", msg.blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text }),
                )
                Role.USER -> {
                    val toolResults = msg.blocks.filterIsInstance<Block.ToolResult>()
                    val texts = msg.blocks.filterIsInstance<Block.Text>()
                    for (tr in toolResults) {
                        msgs.put(
                            JSONObject()
                                .put("role", "tool")
                                .put("tool_call_id", tr.toolUseId)
                                .put("content", tr.content),
                        )
                    }
                    if (texts.isNotEmpty()) {
                        msgs.put(
                            JSONObject()
                                .put("role", "user")
                                .put("content", texts.joinToString("\n") { it.text }),
                        )
                    }
                }
                Role.ASSISTANT -> {
                    val texts = msg.blocks.filterIsInstance<Block.Text>().joinToString("\n") { it.text }
                    val toolUses = msg.blocks.filterIsInstance<Block.ToolUse>()
                    val assistantMsg = JSONObject().put("role", "assistant")
                    if (texts.isNotEmpty()) assistantMsg.put("content", texts)
                    if (toolUses.isNotEmpty()) {
                        val tcArr = JSONArray()
                        for (tu in toolUses) {
                            tcArr.put(
                                JSONObject()
                                    .put("id", tu.id)
                                    .put("type", "function")
                                    .put(
                                        "function",
                                        JSONObject()
                                            .put("name", tu.name)
                                            .put("arguments", tu.input.toString()),
                                    ),
                            )
                        }
                        assistantMsg.put("tool_calls", tcArr)
                    }
                    msgs.put(assistantMsg)
                }
            }
        }
        payload.put("messages", msgs)

        if (tools.isNotEmpty()) {
            val toolsArr = JSONArray()
            for (t in tools) {
                toolsArr.put(
                    JSONObject()
                        .put("type", "function")
                        .put(
                            "function",
                            JSONObject()
                                .put("name", t.name)
                                .put("description", t.description)
                                .put("parameters", t.inputSchema),
                        ),
                )
            }
            payload.put("tools", toolsArr)
        }

        return payload
    }

    private data class MutableToolCall(
        var id: String,
        var name: String,
        val args: StringBuilder,
    )
}
