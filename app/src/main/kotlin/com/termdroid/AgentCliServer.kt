package com.termdroid

import android.app.Application
import android.content.Context
import android.util.Base64
import com.termdroid.agent.AgentEvent
import com.termdroid.agent.AgentLoop
import com.termdroid.agent.AutonomyMode
import com.termdroid.agent.LlmProvider
import com.termdroid.agent.ProviderConfig
import com.termdroid.agent.ToolRegistry
import com.termdroid.agent.TransportFactory
import com.termdroid.core.SecretStore
import com.termdroid.exec.ExecEnvironment
import com.termdroid.probe.CapabilityProbe
import com.termdroid.rootfs.NodeInstaller
import com.termdroid.rootfs.TermuxCommandRunner
import com.termdroid.tools.android.AndroidToolset
import com.termdroid.tools.unix.UnixToolset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket

/**
 * Servidor local en 127.0.0.1:8765 para conectar los comandos CLI de la terminal
 * (claude, codex, gemini, agy) directamente con el motor nativo de IA de Termdroid.
 */
object AgentCliServer {
    private const val PORT = 8765
    private var serverJob: Job? = null
    private var serverSocket: ServerSocket? = null

    fun start(context: Context, scope: CoroutineScope) {
        if (serverJob != null && serverSocket?.isClosed == false) return

        serverJob = scope.launch(Dispatchers.IO) {
            runCatching {
                val ss = ServerSocket(PORT, 50, InetAddress.getByName("127.0.0.1"))
                serverSocket = ss
                while (isActive && !ss.isClosed) {
                    val client = ss.accept()
                    scope.launch(Dispatchers.IO) {
                        handleClient(context.applicationContext as Application, client)
                    }
                }
            }
        }
    }

    private suspend fun handleClient(app: Application, socket: Socket) {
        try {
            socket.use { s ->
                val reader = BufferedReader(InputStreamReader(s.getInputStream()))
                val writer = PrintWriter(s.getOutputStream(), true)

                val line = reader.readLine() ?: return

                if (line.startsWith("INSTALL")) {
                    val result = NodeInstaller.installFullEnvironment(app) { p ->
                        val b64 = Base64.encodeToString(p.toByteArray(), Base64.NO_WRAP)
                        writer.println("T:$b64")
                    }
                    result.exceptionOrNull()?.let { writer.println("E:${it.message ?: "Fallo desconocido"}") }
                    writer.println("D:")
                    return
                }

                if (line.startsWith("TERMUX ")) {
                    val parts = line.removePrefix("TERMUX ").split(" ").filter { it.isNotBlank() }
                    val command = when (parts.firstOrNull()?.uppercase()) {
                        "CLAUDE" -> "claude"
                        "CODEX", "OPENAI" -> "codex"
                        else -> null
                    }
                    if (command == null) {
                        writer.println("E:CLI oficial no reconocido.")
                    } else {
                        TermuxCommandRunner.openCli(app, command, parts.drop(1))
                            .onSuccess { writer.println("T:${Base64.encodeToString("Se abrió Termux con $command.".toByteArray(), Base64.NO_WRAP)}") }
                            .onFailure { writer.println("E:${it.message ?: "No se pudo abrir Termux."}") }
                    }
                    writer.println("D:")
                    return
                }

                if (!line.startsWith("RUN ")) {
                    writer.println("E:Comando no reconocido.")
                    writer.println("D:")
                    return
                }

                val parts = line.removePrefix("RUN ").split(" ", limit = 2)
                val providerName = parts.getOrNull(0) ?: "CLAUDE"
                val rawPrompt = parts.getOrNull(1) ?: ""
                val prompt = runCatching {
                    String(Base64.decode(rawPrompt, Base64.DEFAULT))
                }.getOrDefault(rawPrompt)

                if (prompt.isBlank()) {
                    writer.println("E:El prompt no puede estar vacio.")
                    writer.println("D:")
                    return
                }

                val provider = runCatching { LlmProvider.valueOf(providerName.uppercase()) }
                    .getOrElse { LlmProvider.CLAUDE }

                val token = getEffectiveToken(app, provider)
                if (token.isBlank() && provider != LlmProvider.CUSTOM) {
                    val cmdName = when (provider) {
                        LlmProvider.CLAUDE -> "claude"
                        LlmProvider.OPENAI -> "codex"
                        else -> "agy"
                    }
                    writer.println("E:No se encontraron credenciales para $providerName. Ejecuta '$cmdName login' para iniciar sesion.")
                    writer.println("D:")
                    return
                }

                val caps = CapabilityProbe(app).get()
                val env = ExecEnvironment(app)
                val workspace = File(app.filesDir, "workspace").apply { mkdirs() }

                val tools = UnixToolset(env, caps.backend, workspace).all() +
                    AndroidToolset(app) { }.all()

                val config = ProviderConfig(
                    provider = provider,
                    token = token,
                )

                val loop = AgentLoop(
                    transport = TransportFactory.create(config),
                    tools = ToolRegistry(tools),
                    systemPrompt = "Sos un asistente de terminal y agente autonomo para Termdroid en Android. Responde de forma clara y concisa en espanol con formato markdown.",
                    approvalGate = { _, _ -> true },
                ).also { it.autonomy = AutonomyMode.AUTO_READ }

                loop.run(prompt).collect { ev ->
                    when (ev) {
                        is AgentEvent.TextDelta -> {
                            val b64 = Base64.encodeToString(ev.text.toByteArray(), Base64.NO_WRAP)
                            writer.println("T:$b64")
                        }
                        is AgentEvent.ThinkingDelta -> {
                            val b64 = Base64.encodeToString(ev.text.toByteArray(), Base64.NO_WRAP)
                            writer.println("K:$b64")
                        }
                        is AgentEvent.ToolRequested -> {
                            writer.println("R:${ev.call.name} - ${ev.description}")
                        }
                        is AgentEvent.ToolFinished -> {
                            writer.println("F:${if (ev.outcome.isError) "Fallo" else "Completado"}")
                        }
                        is AgentEvent.Failed -> {
                            writer.println("E:${ev.error.message ?: ev.error}")
                        }
                        is AgentEvent.Refused -> {
                            writer.println("E:Modelo rehuso: ${ev.refusal.category}")
                        }
                        is AgentEvent.Truncated -> {
                            writer.println("E:Respuesta truncada: ${ev.reason}")
                        }
                        AgentEvent.Done -> {
                            writer.println("D:")
                        }
                        else -> Unit
                    }
                }
            }
        } catch (t: Throwable) {
            runCatching {
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println("E:Error en ejecucion: ${t.message}")
                writer.println("D:")
            }
        }
    }

    private fun getEffectiveToken(app: Application, provider: LlmProvider): String {
        val secrets = SecretStore(app)
        return when (provider) {
            LlmProvider.GEMINI -> {
                val s = secrets.geminiToken.orEmpty()
                if (s.isNotBlank()) s
                else {
                    val file = File(app.filesDir, "home/.gemini/auth.json")
                    if (file.exists()) runCatching { JSONObject(file.readText()).optString("apiKey", "") }.getOrDefault("")
                    else ""
                }
            }
            LlmProvider.CLAUDE -> {
                val s = secrets.claudeToken.orEmpty()
                if (s.isNotBlank()) s
                else {
                    val file = File(app.filesDir, "home/.claude.json")
                    if (file.exists()) {
                        runCatching {
                            val obj = JSONObject(file.readText())
                            obj.optString("sessionKey", "").takeIf { it.isNotBlank() }
                                ?: obj.optString("primaryApiKey", "")
                        }.getOrDefault("")
                    } else ""
                }
            }
            LlmProvider.OPENAI, LlmProvider.CUSTOM -> {
                val s = secrets.openaiToken.orEmpty()
                if (s.isNotBlank()) s
                else {
                    val file = File(app.filesDir, "home/.codex/auth.json")
                    if (file.exists()) {
                        runCatching {
                            JSONObject(file.readText()).optString("accessToken", "")
                        }.getOrDefault("")
                    } else ""
                }
            }
        }
    }

    fun stop() {
        runCatching { serverSocket?.close() }
        serverJob?.cancel()
        serverSocket = null
        serverJob = null
    }
}
