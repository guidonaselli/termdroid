package com.termdroid.agent

import org.json.JSONObject

enum class Role { USER, ASSISTANT, SYSTEM }

/** Un bloque de contenido. */
sealed interface Block {
    data class Text(val text: String) : Block

    data class Thinking(val text: String, val signature: String? = null) : Block

    data class ToolUse(val id: String, val name: String, val input: JSONObject) : Block

    data class ToolResult(
        val toolUseId: String,
        val content: String,
        val isError: Boolean = false,
    ) : Block

    /** Bloque opaco (compaction, tipos nuevos). Se reenvia intacto. */
    data class Opaque(val raw: String) : Block
}

data class Msg(val role: Role, val blocks: List<Block>) {
    companion object {
        fun user(text: String) = Msg(Role.USER, listOf(Block.Text(text)))
        fun userBlocks(blocks: List<Block>) = Msg(Role.USER, blocks)
        fun assistant(blocks: List<Block>) = Msg(Role.ASSISTANT, blocks)

        /** Instruccion de operador a mitad de conversacion. */
        fun system(text: String) = Msg(Role.SYSTEM, listOf(Block.Text(text)))
    }
}

enum class StopReason { END_TURN, TOOL_USE, MAX_TOKENS, REFUSAL, PAUSE_TURN, OTHER }

data class Usage(
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val cacheReadInputTokens: Long = 0,
    val cacheCreationInputTokens: Long = 0,
)

data class Refusal(val category: String?, val explanation: String?)

/** Lo que devolvio un turno del modelo. */
data class TurnResult(
    val blocks: List<Block>,
    val stopReason: StopReason,
    val usage: Usage = Usage(),
    val refusal: Refusal? = null,
) {
    val toolUses: List<Block.ToolUse> get() = blocks.filterIsInstance<Block.ToolUse>()
}
