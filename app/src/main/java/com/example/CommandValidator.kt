package com.example

object CommandValidator {

    // Strict whitelisting of allowed base commands
    private val ALLOWED_BASE_COMMANDS = setOf(
        "python",
        "ls",
        "cat",
        "echo",
        "pwd",
        "pkg"
    )

    private val COMMAND_REGEX = "\\s+".toRegex()

    /**
     * Validates if a command is allowed to be executed in Termux.
     * Returns true if valid, false otherwise.
     */
    fun isValid(command: String): Boolean {
        val trimmedCommand = command.trim()
        if (trimmedCommand.isEmpty()) return false

        // Explicitly allow the termux setup command
        if (trimmedCommand == "pkg upgrade && pkg update -y") {
            return true
        }

        // Extract the base command (e.g., 'python' from 'python script.py')
        val parts = trimmedCommand.split(COMMAND_REGEX)
        val baseCommand = parts.firstOrNull() ?: return false

        if (!ALLOWED_BASE_COMMANDS.contains(baseCommand)) {
            return false
        }

        // Additional checks against dangerous patterns like injection characters
        // Denying logical operators and pipes for strictness, unless specifically needed.
        val dangerousTokens = listOf(
            "&&", ";", "|", ">", ">>", "`", "$(", "mkfs", "dd",
            "||", "\n", "\${", "<", "<<"
        )
        for (token in dangerousTokens) {
            if (trimmedCommand.contains(token)) {
                return false
            }
        }
        
        // Prevent importing os and running system commands in python
        if (baseCommand == "python" && (trimmedCommand.contains("import os") || trimmedCommand.contains("import subprocess") || trimmedCommand.contains("os.system"))) {
            return false
        }

        return true
    }
}
