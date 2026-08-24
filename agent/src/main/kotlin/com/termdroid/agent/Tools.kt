package com.termdroid.agent

import org.json.JSONObject

/**
 * Cuanto puede doler un tool si sale mal.
 *
 * Es lo que decide si hace falta aprobacion humana, no el nombre del tool.
 */
enum class ToolRisk {
    /** Lee. Reversible por definicion. */
    READ,

    /** Modifica archivos del usuario. */
    WRITE,

    /** Ejecuta comandos arbitrarios. */
    EXEC,

    /** Actua sobre el sistema con permisos de shell o sobre otras apps. */
    PRIVILEGED,
}

data class ToolSpec(
    val name: String,
    val description: String,
    /** JSON Schema del input. Con `strict` el modelo respeta el contrato exacto. */
    val inputSchema: JSONObject,
    val strict: Boolean = true,
)

data class ToolOutcome(val content: String, val isError: Boolean = false)

interface AgentTool {
    val spec: ToolSpec
    val risk: ToolRisk

    /** Lo que se le muestra al usuario al pedirle aprobacion: la accion exacta. */
    fun describe(input: JSONObject): String

    suspend fun execute(input: JSONObject): ToolOutcome
}

/**
 * El set de tools de una sesion.
 *
 * El orden es estable a proposito: `tools` se renderiza antes que `system` y que
 * `messages`, asi que reordenarlo invalida todo el prefijo cacheado.
 * Ver 10_TECH/AGENT_LOOP.md.
 */
class ToolRegistry(tools: List<AgentTool>) {
    private val byName: Map<String, AgentTool> =
        tools.sortedBy { it.spec.name }.associateBy { it.spec.name }

    val specs: List<ToolSpec> get() = byName.values.map { it.spec }

    operator fun get(name: String): AgentTool? = byName[name]
}

/** Cuanto decide el agente por su cuenta. */
enum class AutonomyMode { ASK_ALL, AUTO_READ, AUTO_ALL }

/**
 * Un tool privilegiado nunca se auto-ejecuta, en ningun modo.
 *
 * La defensa real contra prompt injection no es el prompt: es que las acciones
 * peligrosas requieren un toque del usuario. Ver 10_TECH/SECURITY_MODEL.md.
 */
fun AutonomyMode.needsApproval(risk: ToolRisk): Boolean = when {
    risk == ToolRisk.PRIVILEGED -> true
    this == AutonomyMode.ASK_ALL -> true
    this == AutonomyMode.AUTO_READ -> risk != ToolRisk.READ
    else -> false
}
