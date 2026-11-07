package com.example

/**
 * Provider neutral agent protocol. The model proposes at most one Termux command per reply in a
 * fenced ```termux block. The app validates it, asks the user (depending on the security level),
 * runs it and feeds the output back as the next user turn.
 */
object AgentProtocol {

    const val MAX_AUTONOMOUS_STEPS = 6
    const val MAX_OUTPUT_CHARS = 4_000
    const val MAX_HISTORY_MESSAGES = 40

    private val COMMAND_BLOCK = Regex("```termux[ \\t]*\\r?\\n(.*?)\\r?\\n?```", RegexOption.DOT_MATCHES_ALL)

    data class ParsedReply(
        /** Text to show to the user (command block removed). */
        val text: String,
        /** The proposed command, if any. */
        val command: String?,
    )

    fun parse(reply: String): ParsedReply {
        val match = COMMAND_BLOCK.find(reply)
        val command = match?.groupValues?.get(1)
            ?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
        val text = if (match != null) reply.removeRange(match.range).trim() else reply.trim()
        return ParsedReply(text, command)
    }

    fun systemPrompt(securityLevel: Int, language: String): String {
        val commands = CommandValidator.ALLOWED_COMMANDS.sorted().joinToString(", ")
        val levelRules = when (securityLevel) {
            3 -> "SECURITY LEVEL 3 (sandbox): you must NOT propose any commands. Only answer with text."
            2 -> "SECURITY LEVEL 2 (supervised): every command is shown to the user, who approves or blocks it."
            else -> "SECURITY LEVEL 1 (autonomous): read-only and write commands run automatically; " +
                "destructive ones (rm, mv, ollama rm) still need the user's approval. You may chain " +
                "up to $MAX_AUTONOMOUS_STEPS commands to finish a task."
        }
        val reply = if (language == "Deutsch") "Antworte auf Deutsch." else "Reply in English."
        return """
            You are Synaptiq, an autonomous assistant running on an Android phone. You can use the
            Termux Linux environment (home directory: ${TermuxConstants.HOME}).

            $levelRules

            To run a command, end your reply with exactly one block:
            ```termux
            <single command>
            ```
            Rules for commands:
            - Only these programs are available: $commands.
            - The command is executed directly, NOT through a shell: no pipes, redirections,
              `;`, `&&`, variables, `~` or globbing. Use absolute paths or paths relative to home.
            - `python` may only run an existing .py file (no `-c`). `pkg` supports install, update,
              upgrade, search, show, list-installed. `rm` cannot delete recursively.
            - Propose one command at a time and wait for its output, which arrives in the next
              message starting with "[Termux]".
            - Never claim a command ran before you saw its output.
            When the task is done, or no command is needed, answer normally without a block.
            Keep answers concise and use Markdown sparingly. $reply
        """.trimIndent()
    }

    fun formatResult(command: String, result: TermuxResult): String = buildString {
        append("[Termux] `").append(command).append("`\n")
        if (!result.started) {
            append("Could not start: ").append(result.errMessage ?: "error ${result.errCode}")
            return@buildString
        }
        append("exit code: ").append(result.exitCode ?: "?").append('\n')
        if (result.stdout.isNotBlank()) append("stdout:\n").append(truncate(result.stdout)).append('\n')
        if (result.stderr.isNotBlank()) append("stderr:\n").append(truncate(result.stderr)).append('\n')
        if (result.stdout.isBlank() && result.stderr.isBlank()) append("(no output)")
    }.trim()

    fun formatRejected(command: String, reason: String): String =
        "[Termux] `$command` was NOT executed: $reason"

    fun truncate(text: String, max: Int = MAX_OUTPUT_CHARS): String =
        if (text.length <= max) text.trimEnd()
        else text.take(max).trimEnd() + "\n… (${text.length - max} more characters truncated)"

    /**
     * Prepares the history for a request: merges consecutive turns of the same role (not every
     * provider accepts them) and keeps the request small. Dropping from the front is safe because
     * only plain text is replayed.
     */
    fun prepareHistory(messages: List<LlmMessage>): List<LlmMessage> {
        val merged = mutableListOf<LlmMessage>()
        for (m in messages) {
            val last = merged.lastOrNull()
            if (last != null && last.role == m.role) {
                merged[merged.lastIndex] = last.copy(content = last.content + "\n\n" + m.content)
            } else {
                merged += m
            }
        }
        if (merged.size <= MAX_HISTORY_MESSAGES) return merged
        var trimmed: List<LlmMessage> = merged.takeLast(MAX_HISTORY_MESSAGES)
        // Most APIs require the conversation to start with a user turn.
        while (trimmed.isNotEmpty() && trimmed.first().role != ChatRole.USER) trimmed = trimmed.drop(1)
        return trimmed
    }
}
