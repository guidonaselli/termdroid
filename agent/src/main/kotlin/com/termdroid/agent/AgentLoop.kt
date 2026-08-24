package com.termdroid.agent

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.json.JSONObject

/** Lo que la UI observa mientras el agente trabaja. */
sealed interface AgentEvent {
    data class TextDelta(val text: String) : AgentEvent
    data class ThinkingDelta(val text: String) : AgentEvent

    /** Se decidio usar un tool. La UI puede mostrarlo antes de que corra. */
    data class ToolRequested(val call: Block.ToolUse, val description: String) : AgentEvent

    data class ToolApprovalNeeded(val call: Block.ToolUse, val description: String) : AgentEvent
    data class ToolRejected(val call: Block.ToolUse) : AgentEvent
    data class ToolFinished(val call: Block.ToolUse, val outcome: ToolOutcome) : AgentEvent

    data class TurnFinished(val usage: Usage) : AgentEvent
    data class Refused(val refusal: Refusal) : AgentEvent
    data class Truncated(val reason: String) : AgentEvent
    data class Failed(val error: Throwable) : AgentEvent
    data object Done : AgentEvent
}

/** Lo que hace falta para hablar con el modelo. Se abstrae para poder testear el loop sin red. */
interface Transport {
    fun stream(system: String, tools: List<ToolSpec>, messages: List<Msg>): Flow<StreamEvent>
}

sealed interface StreamEvent {
    data class Text(val delta: String) : StreamEvent
    data class Thinking(val delta: String) : StreamEvent
    data class Final(val result: TurnResult) : StreamEvent
}

/** Decide si una accion se ejecuta. Devuelve false si el usuario la rechaza. */
fun interface ApprovalGate {
    suspend fun approve(call: Block.ToolUse, description: String): Boolean
}

/**
 * El loop de agente.
 *
 * Se implementa a mano y no con el tool runner del SDK porque la UI necesita
 * intervenir entre la decision del modelo y la ejecucion: sin ese punto no hay
 * aprobaciones ni cancelacion real. Ver 10_TECH/AGENT_LOOP.md.
 */
class AgentLoop(
    private val transport: Transport,
    private val tools: ToolRegistry,
    private val systemPrompt: String,
    private val approvalGate: ApprovalGate,
) {
    var autonomy: AutonomyMode = AutonomyMode.AUTO_READ

    /** Historial de la sesion. Se guardan los bloques tal cual llegan. */
    private val history = mutableListOf<Msg>()

    val messages: List<Msg> get() = history.toList()

    fun restore(saved: List<Msg>) {
        history.clear()
        history.addAll(saved)
    }

    fun run(userInput: String): Flow<AgentEvent> = flow {
        history.add(Msg.user(userInput))

        try {
            while (true) {
                val result = runTurn(this)

                when (result.stopReason) {
                    StopReason.REFUSAL -> {
                        emit(AgentEvent.Refused(result.refusal ?: Refusal(null, null)))
                        return@flow
                    }
                    StopReason.MAX_TOKENS -> {
                        emit(AgentEvent.Truncated("La respuesta llego al limite de tokens."))
                        return@flow
                    }
                    StopReason.TOOL_USE -> {
                        val keepGoing = runTools(this, result.toolUses)
                        if (!keepGoing) return@flow
                    }
                    else -> {
                        emit(AgentEvent.Done)
                        return@flow
                    }
                }
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            emit(AgentEvent.Failed(t))
        }
    }

    private suspend fun runTurn(
        collector: kotlinx.coroutines.flow.FlowCollector<AgentEvent>,
    ): TurnResult {
        var finalResult: TurnResult? = null

        transport.stream(systemPrompt, tools.specs, history).collect { ev ->
            when (ev) {
                is StreamEvent.Text -> collector.emit(AgentEvent.TextDelta(ev.delta))
                is StreamEvent.Thinking -> collector.emit(AgentEvent.ThinkingDelta(ev.delta))
                is StreamEvent.Final -> finalResult = ev.result
            }
        }

        val result = finalResult ?: error("el transporte termino sin un resultado final")

        // El contenido del asistente se guarda completo, no solo el texto.
        history.add(Msg.assistant(result.blocks))
        collector.emit(AgentEvent.TurnFinished(result.usage))
        return result
    }

    /**
     * Ejecuta los tools de un turno y devuelve sus resultados.
     *
     * Los `tool_result` de un mismo mensaje van juntos en **un unico** mensaje
     * user: repartirlos entre mensajes le ensena al modelo a dejar de paralelizar.
     */
    private suspend fun runTools(
        collector: kotlinx.coroutines.flow.FlowCollector<AgentEvent>,
        calls: List<Block.ToolUse>,
    ): Boolean = coroutineScope {
        val approved = mutableListOf<Block.ToolUse>()
        val results = mutableListOf<Block.ToolResult>()

        // La aprobacion es secuencial: pedirlas todas juntas seria un dialogo
        // ilegible, y ademas el usuario decide una a una.
        for (call in calls) {
            val tool = tools[call.name]
            if (tool == null) {
                results += Block.ToolResult(
                    call.id,
                    "No existe un tool llamado '${call.name}'.",
                    isError = true,
                )
                continue
            }

            val description = runCatching { tool.describe(call.input) }
                .getOrElse { "${call.name}(...)" }
            collector.emit(AgentEvent.ToolRequested(call, description))

            if (autonomy.needsApproval(tool.risk)) {
                collector.emit(AgentEvent.ToolApprovalNeeded(call, description))
                if (!approvalGate.approve(call, description)) {
                    collector.emit(AgentEvent.ToolRejected(call))
                    // Un rechazo es informacion para el modelo, no un final: se le
                    // dice y se lo deja replantear.
                    results += Block.ToolResult(
                        call.id,
                        "El usuario rechazo esta accion.",
                        isError = true,
                    )
                    continue
                }
            }
            approved += call
        }

        // Los aprobados corren en paralelo: el modelo los pidio juntos porque son
        // independientes.
        val running = approved.map { call ->
            call to async {
                val tool = tools[call.name]!!
                runCatching { tool.execute(call.input) }
                    .getOrElse { ToolOutcome("El tool fallo: $it", isError = true) }
            }
        }

        for ((call, job) in running) {
            val outcome = job.await()
            collector.emit(AgentEvent.ToolFinished(call, outcome))
            results += Block.ToolResult(call.id, outcome.content, outcome.isError)
        }

        // Se responde en el orden en que el modelo los pidio.
        val ordered = calls.mapNotNull { c -> results.firstOrNull { it.toolUseId == c.id } }
        history.add(Msg.userBlocks(ordered))
        true
    }
}

/** Ayuda para armar schemas sin pelear con JSONObject a mano. */
fun objectSchema(
    vararg properties: Pair<String, JSONObject>,
    required: List<String> = properties.map { it.first },
): JSONObject = JSONObject()
    .put("type", "object")
    .put("properties", JSONObject().apply { properties.forEach { put(it.first, it.second) } })
    .put("required", required)
    // strict lo exige: sin esto el modelo puede inventar campos.
    .put("additionalProperties", false)

fun stringProp(description: String): JSONObject =
    JSONObject().put("type", "string").put("description", description)

fun intProp(description: String): JSONObject =
    JSONObject().put("type", "integer").put("description", description)
