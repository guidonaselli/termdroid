package com.termdroid.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.helpers.MessageAccumulator
import com.anthropic.models.messages.CacheControlEphemeral
import com.anthropic.models.messages.ContentBlockParam
import com.anthropic.models.messages.Message
import com.anthropic.models.messages.MessageCreateParams
import com.anthropic.models.messages.MessageParam
import com.anthropic.models.messages.OutputConfig
import com.anthropic.models.messages.StopReason as SdkStopReason
import com.anthropic.models.messages.TextBlockParam
import com.anthropic.models.messages.ThinkingConfigAdaptive
import com.anthropic.models.messages.Tool
import com.anthropic.models.messages.ToolResultBlockParam
import com.anthropic.models.messages.ToolUseBlockParam
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject

/** Transporte real contra la Messages API. */
class ClaudeTransport(
    apiKey: String,
    private val model: String = DEFAULT_MODEL,
    private val maxTokens: Long = DEFAULT_MAX_TOKENS,
    private val effort: OutputConfig.Effort = OutputConfig.Effort.XHIGH,
) : Transport {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder()
        .apiKey(apiKey)
        .build()

    override fun stream(
        system: String,
        tools: List<ToolSpec>,
        messages: List<Msg>,
    ): Flow<StreamEvent> = flow {
        val params = buildParams(system, tools, messages)
        val accumulator = MessageAccumulator.create()

        client.messages().createStreaming(params).use { response ->
            val events = response.stream().iterator()
            while (events.hasNext()) {
                val event = events.next()
                accumulator.accumulate(event)

                // Se juntan en el callback y se emiten afuera: ifPresent no suspende.
                val pending = mutableListOf<StreamEvent>()
                event.contentBlockDelta().ifPresent { d ->
                    d.delta().text().ifPresent { pending += StreamEvent.Text(it.text()) }
                    d.delta().thinking().ifPresent { pending += StreamEvent.Thinking(it.thinking()) }
                }
                pending.forEach { emit(it) }
            }
        }

        emit(StreamEvent.Final(accumulator.message().toTurnResult()))
    }.flowOn(Dispatchers.IO)

    private fun buildParams(
        system: String,
        tools: List<ToolSpec>,
        messages: List<Msg>,
    ): MessageCreateParams {
        val builder = MessageCreateParams.builder()
            .model(model)
            .maxTokens(maxTokens)
            .thinking(
                ThinkingConfigAdaptive.builder()
                    .display(ThinkingConfigAdaptive.Display.SUMMARIZED)
                    .build(),
            )
            .outputConfig(OutputConfig.builder().effort(effort).build())
            .systemOfTextBlockParams(
                listOf(
                    TextBlockParam.builder()
                        .text(system)
                        .cacheControl(CacheControlEphemeral.builder().build())
                        .build(),
                ),
            )

        tools.forEach { builder.addTool(it.toSdkTool()) }
        messages.forEach { msg -> msg.toSdkMessage()?.let { builder.addMessage(it) } }

        return builder.build()
    }

    private fun ToolSpec.toSdkTool(): Tool {
        val props = inputSchema.optJSONObject("properties") ?: JSONObject()
        val schemaBuilder = Tool.InputSchema.builder()
            .properties(
                Tool.InputSchema.Properties.builder().apply {
                    props.keys().forEach { key ->
                        putAdditionalProperty(key, JsonValue.from(props.getJSONObject(key).toMap()))
                    }
                }.build(),
            )
            .putAdditionalProperty("additionalProperties", JsonValue.from(false))

        inputSchema.optJSONArray("required")?.let { req ->
            schemaBuilder.required((0 until req.length()).map { req.getString(it) })
        }

        return Tool.builder()
            .name(name)
            .description(description)
            .inputSchema(schemaBuilder.build())
            .strict(strict)
            .build()
    }

    private fun Msg.toSdkMessage(): MessageParam? {
        val sdkRole = when (role) {
            Role.USER -> MessageParam.Role.USER
            Role.ASSISTANT -> MessageParam.Role.ASSISTANT
            // Los system message a mitad de conversacion no se mandan.
            Role.SYSTEM -> return null
        }

        val params = blocks.mapNotNull { it.toSdkBlock() }
        if (params.isEmpty()) return null

        return MessageParam.builder().role(sdkRole).contentOfBlockParams(params).build()
    }

    private fun Block.toSdkBlock(): ContentBlockParam? = when (this) {
        is Block.Text ->
            if (text.isBlank()) null
            else ContentBlockParam.ofText(TextBlockParam.builder().text(text).build())

        is Block.ToolUse ->
            ContentBlockParam.ofToolUse(
                ToolUseBlockParam.builder()
                    .id(id)
                    .name(name)
                    .input(JsonValue.from(input.toMap()))
                    .build(),
            )

        is Block.ToolResult ->
            ContentBlockParam.ofToolResult(
                ToolResultBlockParam.builder()
                    .toolUseId(toolUseId)
                    .content(content)
                    .isError(isError)
                    .build(),
            )

        // Thinking y opacos no se reconstruyen hacia el SDK.
        is Block.Thinking -> null
        is Block.Opaque -> null
    }

    private fun Message.toTurnResult(): TurnResult {
        val blocks = content().mapNotNull { cb ->
            when {
                cb.text().isPresent -> Block.Text(cb.text().get().text())
                cb.thinking().isPresent -> {
                    val t = cb.thinking().get()
                    Block.Thinking(t.thinking(), t.signature())
                }
                cb.toolUse().isPresent -> {
                    val tu = cb.toolUse().get()
                    Block.ToolUse(tu.id(), tu.name(), JSONObject(tu._input().toString()))
                }
                else -> null
            }
        }

        val stop = stopReason().map { it.toStopReason() }.orElse(StopReason.OTHER)

        // stop_details solo viene poblado con refusal.
        val refusal = if (stop == StopReason.REFUSAL) {
            stopDetails().map { Refusal(it.category().toString(), it.explanation().orElse(null)) }
                .orElse(Refusal(null, null))
        } else {
            null
        }

        val u = usage()
        return TurnResult(
            blocks = blocks,
            stopReason = stop,
            usage = Usage(
                inputTokens = u.inputTokens(),
                outputTokens = u.outputTokens(),
                cacheReadInputTokens = u.cacheReadInputTokens().orElse(0),
                cacheCreationInputTokens = u.cacheCreationInputTokens().orElse(0),
            ),
            refusal = refusal,
        )
    }

    private fun SdkStopReason.toStopReason(): StopReason = when (this) {
        SdkStopReason.END_TURN -> StopReason.END_TURN
        SdkStopReason.TOOL_USE -> StopReason.TOOL_USE
        SdkStopReason.MAX_TOKENS -> StopReason.MAX_TOKENS
        SdkStopReason.REFUSAL -> StopReason.REFUSAL
        SdkStopReason.PAUSE_TURN -> StopReason.PAUSE_TURN
        else -> StopReason.OTHER
    }

    companion object {
        const val DEFAULT_MODEL = "claude-opus-5"
        const val DEFAULT_MAX_TOKENS = 64_000L
    }
}

/** JSONObject a Map, para pasarlo como JsonValue. */
private fun JSONObject.toMap(): Map<String, Any?> =
    keys().asSequence().associateWith { k -> unwrap(get(k)) }

private fun JSONArray.toList(): List<Any?> = (0 until length()).map { unwrap(get(it)) }

private fun unwrap(v: Any?): Any? = when (v) {
    is JSONObject -> v.toMap()
    is JSONArray -> v.toList()
    JSONObject.NULL -> null
    else -> v
}
