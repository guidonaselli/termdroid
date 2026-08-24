package com.termdroid.tools.unix

import com.termdroid.agent.AgentTool
import com.termdroid.agent.ToolOutcome
import com.termdroid.agent.ToolRisk
import com.termdroid.agent.ToolSpec
import com.termdroid.agent.intProp
import com.termdroid.agent.objectSchema
import com.termdroid.agent.stringProp
import com.termdroid.exec.ExecBackend
import com.termdroid.exec.ExecEnvironment
import com.termdroid.exec.Executor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Las tools de filesystem y shell.
 *
 * Funcionan tanto sobre el toybox que trae Android como sobre el rootfs cuando
 * este instalado, asi que no pueden asumir GNU: nada de `--color`, `-printf` ni
 * banderas largas que toybox no tiene. Ver 10_TECH/ARCHITECTURE.md.
 */
class UnixToolset(
    private val env: ExecEnvironment,
    private val backend: ExecBackend,
    /** Raiz a la que se restringen las rutas. */
    private val workspace: File,
) {
    private val executor = Executor(env, backend)

    fun all(): List<AgentTool> = listOf(
        BashTool(),
        ReadFileTool(),
        WriteFileTool(),
        EditFileTool(),
        GlobTool(),
        GrepTool(),
    )

    // --- limites -----------------------------------------------------------

    /**
     * Resuelve una ruta y falla si se sale del workspace.
     *
     * Se compara la ruta **canonica** para que `../` y los symlinks no sirvan
     * para escapar. Ver 10_TECH/SECURITY_MODEL.md.
     */
    private fun resolve(path: String): File {
        val f = if (File(path).isAbsolute) File(path) else File(workspace, path)
        val canonical = f.canonicalFile
        val root = workspace.canonicalFile
        require(canonical == root || canonical.path.startsWith(root.path + File.separator)) {
            "La ruta queda fuera del workspace: $path"
        }
        return canonical
    }

    private fun truncate(text: String, limit: Int = OUTPUT_LIMIT): String =
        if (text.length <= limit) text
        else text.take(limit) + "\n\n[...cortado, ${text.length - limit} caracteres mas]"

    // --- tools -------------------------------------------------------------

    private inner class BashTool : AgentTool {
        override val spec = ToolSpec(
            name = "bash",
            description = "Ejecuta un comando de shell y devuelve su salida combinada. " +
                "El entorno puede ser toybox, no GNU: no asumir banderas de coreutils.",
            inputSchema = objectSchema(
                "command" to stringProp("El comando a ejecutar."),
                "timeout_s" to intProp("Segundos maximos. Por defecto 30."),
                required = listOf("command"),
            ),
        )
        override val risk = ToolRisk.EXEC

        override fun describe(input: JSONObject) = input.optString("command")

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            val command = input.optString("command").ifBlank {
                return@withContext ToolOutcome("Falta 'command'.", isError = true)
            }
            val result = executor.run(
                File(SYSTEM_SH),
                listOf("-c", command),
                cwd = workspace,
            )
            ToolOutcome(truncate(result.output), isError = !result.ok)
        }
    }

    private inner class ReadFileTool : AgentTool {
        override val spec = ToolSpec(
            name = "read_file",
            description = "Lee un archivo de texto. Devuelve el contenido con numeros de linea.",
            inputSchema = objectSchema(
                "path" to stringProp("Ruta del archivo."),
                "offset" to intProp("Primera linea a leer, 1-based."),
                "limit" to intProp("Cuantas lineas leer."),
                required = listOf("path"),
            ),
        )
        override val risk = ToolRisk.READ

        override fun describe(input: JSONObject) = "leer ${input.optString("path")}"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            runCatching {
                val f = resolve(input.getString("path"))
                if (!f.isFile) return@runCatching ToolOutcome("No es un archivo: $f", isError = true)

                val offset = input.optInt("offset", 1).coerceAtLeast(1)
                val limit = input.optInt("limit", 2000).coerceIn(1, 5000)
                val lines = f.readLines()
                val slice = lines.drop(offset - 1).take(limit)
                val body = slice.mapIndexed { i, l -> "${offset + i}\t$l" }.joinToString("\n")
                ToolOutcome(truncate(body.ifEmpty { "[archivo vacio]" }))
            }.getOrElse { ToolOutcome("No se pudo leer: $it", isError = true) }
        }
    }

    private inner class WriteFileTool : AgentTool {
        override val spec = ToolSpec(
            name = "write_file",
            description = "Escribe un archivo completo, creando los directorios que falten.",
            inputSchema = objectSchema(
                "path" to stringProp("Ruta del archivo."),
                "content" to stringProp("Contenido completo."),
            ),
        )
        override val risk = ToolRisk.WRITE

        override fun describe(input: JSONObject): String {
            val bytes = input.optString("content").toByteArray().size
            return "escribir ${input.optString("path")} ($bytes bytes)"
        }

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            runCatching {
                val f = resolve(input.getString("path"))
                f.parentFile?.mkdirs()
                f.writeText(input.getString("content"))
                ToolOutcome("Escrito: ${f.path} (${f.length()} bytes)")
            }.getOrElse { ToolOutcome("No se pudo escribir: $it", isError = true) }
        }
    }

    private inner class EditFileTool : AgentTool {
        override val spec = ToolSpec(
            name = "edit_file",
            description = "Reemplaza una cadena exacta por otra dentro de un archivo. " +
                "La cadena buscada tiene que ser unica en el archivo.",
            inputSchema = objectSchema(
                "path" to stringProp("Ruta del archivo."),
                "old" to stringProp("Texto a reemplazar, exacto y unico."),
                "new" to stringProp("Texto nuevo."),
            ),
        )
        override val risk = ToolRisk.WRITE

        override fun describe(input: JSONObject) = "editar ${input.optString("path")}"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            runCatching {
                val f = resolve(input.getString("path"))
                val old = input.getString("old")
                val new = input.getString("new")
                val text = f.readText()

                val count = text.split(old).size - 1
                when {
                    // Fallar es mejor que adivinar cual de las coincidencias era.
                    count == 0 -> ToolOutcome("No se encontro el texto en ${f.name}.", isError = true)
                    count > 1 -> ToolOutcome(
                        "El texto aparece $count veces en ${f.name}: tiene que ser unico.",
                        isError = true,
                    )
                    else -> {
                        f.writeText(text.replaceFirst(old, new))
                        ToolOutcome("Editado: ${f.path}")
                    }
                }
            }.getOrElse { ToolOutcome("No se pudo editar: $it", isError = true) }
        }
    }

    private inner class GlobTool : AgentTool {
        override val spec = ToolSpec(
            name = "glob",
            description = "Lista archivos que coinciden con un patron, dentro del workspace.",
            inputSchema = objectSchema(
                "pattern" to stringProp("Patron tipo glob, por ejemplo '**/*.kt' o '*.txt'."),
                required = listOf("pattern"),
            ),
        )
        override val risk = ToolRisk.READ

        override fun describe(input: JSONObject) = "buscar ${input.optString("pattern")}"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            runCatching {
                val regex = globToRegex(input.getString("pattern"))
                val root = workspace.canonicalFile
                val hits = root.walkTopDown()
                    .filter { it.isFile }
                    .map { it.relativeTo(root).path.replace(File.separatorChar, '/') }
                    .filter { regex.matches(it) }
                    .take(MATCH_LIMIT)
                    .toList()
                ToolOutcome(if (hits.isEmpty()) "Sin coincidencias." else hits.joinToString("\n"))
            }.getOrElse { ToolOutcome("Fallo el glob: $it", isError = true) }
        }
    }

    private inner class GrepTool : AgentTool {
        override val spec = ToolSpec(
            name = "grep",
            description = "Busca un patron (expresion regular) en los archivos del workspace.",
            inputSchema = objectSchema(
                "pattern" to stringProp("Expresion regular."),
                "glob" to stringProp("Restringe a los archivos que coincidan con este patron."),
                required = listOf("pattern"),
            ),
        )
        override val risk = ToolRisk.READ

        override fun describe(input: JSONObject) = "grep '${input.optString("pattern")}'"

        override suspend fun execute(input: JSONObject): ToolOutcome = withContext(Dispatchers.IO) {
            runCatching {
                val regex = Regex(input.getString("pattern"))
                val fileFilter = input.optString("glob").takeIf { it.isNotBlank() }?.let(::globToRegex)
                val root = workspace.canonicalFile

                val out = StringBuilder()
                var found = 0
                for (f in root.walkTopDown().filter { it.isFile }) {
                    val rel = f.relativeTo(root).path.replace(File.separatorChar, '/')
                    if (fileFilter != null && !fileFilter.matches(rel)) continue
                    // Los binarios se saltan: llenan la salida de basura.
                    if (f.length() > MAX_GREP_BYTES) continue

                    f.useLines { lines ->
                        lines.forEachIndexed { i, line ->
                            if (found < MATCH_LIMIT && regex.containsMatchIn(line)) {
                                out.appendLine("$rel:${i + 1}: ${line.trim().take(200)}")
                                found++
                            }
                        }
                    }
                    if (found >= MATCH_LIMIT) break
                }
                ToolOutcome(if (found == 0) "Sin coincidencias." else truncate(out.toString()))
            }.getOrElse { ToolOutcome("Fallo el grep: $it", isError = true) }
        }
    }

    private companion object {
        const val SYSTEM_SH = "/system/bin/sh"
        const val OUTPUT_LIMIT = 30_000
        const val MATCH_LIMIT = 200
        const val MAX_GREP_BYTES = 2_000_000L
    }
}

/**
 * Traduce un glob a regex.
 *
 * `**` cruza directorios y `*` no, que es lo que espera cualquiera que haya
 * usado un glob antes.
 */
internal fun globToRegex(pattern: String): Regex {
    val sb = StringBuilder("^")
    var i = 0
    while (i < pattern.length) {
        when (val c = pattern[i]) {
            '*' ->
                if (i + 1 < pattern.length && pattern[i + 1] == '*') {
                    sb.append(".*")
                    i++
                    // '**/' tambien tiene que aceptar cero directorios.
                    if (i + 1 < pattern.length && pattern[i + 1] == '/') {
                        sb.append("/?")
                        i++
                    }
                } else {
                    sb.append("[^/]*")
                }
            '?' -> sb.append("[^/]")
            '.', '(', ')', '+', '|', '^', '$', '@', '%', '{', '}', '[', ']', '\\' ->
                sb.append('\\').append(c)
            else -> sb.append(c)
        }
        i++
    }
    sb.append('$')
    return Regex(sb.toString())
}
