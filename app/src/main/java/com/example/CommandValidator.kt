package com.example

/**
 * How dangerous an allowed command is. Drives the human-in-the-loop policy:
 * - [READ_ONLY] commands never change anything.
 * - [WRITE] commands create/modify files or install packages.
 * - [DESTRUCTIVE] commands can delete or overwrite data and always require an
 *   explicit double confirmation, regardless of the security level.
 * - [EXTERNAL] commands reach the outside world or cost money (SMS, calls, uploads, running
 *   downloaded scripts) and always require approval, regardless of the security level.
 *
 * The names are persisted - do not rename.
 */
enum class CommandRisk { READ_ONLY, WRITE, DESTRUCTIVE, EXTERNAL }

enum class RejectReason {
    EMPTY,
    TOO_LONG,
    CONTROL_CHARACTER,
    UNCLOSED_QUOTE,
    SHELL_OPERATOR,
    NOT_WHITELISTED,
    FORBIDDEN_ARGUMENT,
    FORBIDDEN_SUBCOMMAND,
    PATH_TRAVERSAL,
    /** Catastrophic operation (root, formatting, wiping home/system folders): never executed. */
    HARD_BLOCKED,
    INVALID_PATH,
    INVALID_URL,
    /** Security level 3 forbids every action. */
    SANDBOX,
}

sealed class ValidationResult {
    /** The command is allowed. [executable] is the bare program name, [args] its arguments. */
    data class Allowed(
        val executable: String,
        val args: List<String>,
        val risk: CommandRisk,
    ) : ValidationResult()

    data class Rejected(val reason: RejectReason, val detail: String = "") : ValidationResult()
}

/**
 * Whitelist based validation of commands proposed by the AI agent.
 *
 * Commands are never passed to a shell. They are tokenized here (quote aware) and executed
 * directly as `program arg1 arg2 ...` inside Termux. That removes the whole class of shell
 * injection attacks (`;`, `&&`, `$(...)`, redirections, globbing, variable expansion). Unquoted
 * shell operators are still rejected so that the agent gets a clear error instead of a command
 * that silently does something different than intended.
 */
object CommandValidator {

    const val MAX_LENGTH = 1000

    private val READ_ONLY_COMMANDS = setOf(
        "ls", "cat", "echo", "pwd", "whoami", "date", "uname", "df", "du",
        "head", "tail", "wc", "grep", "find", "stat", "file", "which",
    )

    private val WRITE_COMMANDS = setOf("mkdir", "touch", "cp")

    private val DESTRUCTIVE_COMMANDS = setOf("rm", "mv")

    private val SPECIAL_COMMANDS = setOf("python", "python3", "pkg", "ollama")

    /** All program names the agent is allowed to run (for the system prompt and the UI). */
    val ALLOWED_COMMANDS: Set<String> =
        READ_ONLY_COMMANDS + WRITE_COMMANDS + DESTRUCTIVE_COMMANDS + SPECIAL_COMMANDS

    private val PKG_READ_ONLY = setOf("list-installed", "list-all", "search", "show", "files")
    private val PKG_WRITE = setOf("install", "in", "update", "upgrade", "up")

    private val OLLAMA_READ_ONLY = setOf("list", "ls", "ps", "show", "--version", "-v")
    private val OLLAMA_WRITE = setOf("pull")
    private val OLLAMA_DESTRUCTIVE = setOf("rm")

    private val FIND_FORBIDDEN = setOf(
        "-exec", "-execdir", "-ok", "-okdir", "-delete",
        "-fprint", "-fprint0", "-fprintf", "-fls",
    )

    private val PYTHON_ALLOWED_FLAGS = setOf("-u", "-B", "-V", "--version", "-O")

    private const val UNQUOTED_OPERATORS = ";|&<>`$"

    fun isValid(command: String): Boolean = validate(command) is ValidationResult.Allowed

    fun validate(command: String): ValidationResult {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return ValidationResult.Rejected(RejectReason.EMPTY)
        if (trimmed.length > MAX_LENGTH) return ValidationResult.Rejected(RejectReason.TOO_LONG)
        if (trimmed.any { it.isISOControl() && it != '\t' }) {
            return ValidationResult.Rejected(RejectReason.CONTROL_CHARACTER)
        }

        val tokens = when (val t = tokenize(trimmed)) {
            is TokenizeResult.Ok -> t.tokens
            is TokenizeResult.UnclosedQuote -> return ValidationResult.Rejected(RejectReason.UNCLOSED_QUOTE)
            is TokenizeResult.Operator ->
                return ValidationResult.Rejected(RejectReason.SHELL_OPERATOR, t.operator.toString())
        }
        if (tokens.isEmpty()) return ValidationResult.Rejected(RejectReason.EMPTY)

        val program = tokens.first()
        val args = tokens.drop(1)

        if (program !in ALLOWED_COMMANDS) {
            return ValidationResult.Rejected(RejectReason.NOT_WHITELISTED, program)
        }

        val risk = when (program) {
            in READ_ONLY_COMMANDS -> CommandRisk.READ_ONLY
            in WRITE_COMMANDS -> CommandRisk.WRITE
            in DESTRUCTIVE_COMMANDS -> CommandRisk.DESTRUCTIVE
            "python", "python3" -> return validatePython(program, args)
            "pkg" -> return validatePkg(args)
            "ollama" -> return validateOllama(args)
            else -> return ValidationResult.Rejected(RejectReason.NOT_WHITELISTED, program)
        }

        if (program == "find") {
            args.firstOrNull { it in FIND_FORBIDDEN }?.let {
                return ValidationResult.Rejected(RejectReason.FORBIDDEN_ARGUMENT, it)
            }
        }

        if (program == "rm") {
            args.firstOrNull { isRecursiveFlag(it) }?.let {
                return ValidationResult.Rejected(RejectReason.FORBIDDEN_ARGUMENT, it)
            }
        }

        if (risk != CommandRisk.READ_ONLY) {
            args.firstOrNull { hasParentSegment(it) }?.let {
                return ValidationResult.Rejected(RejectReason.PATH_TRAVERSAL, it)
            }
        }

        return ValidationResult.Allowed(program, args, risk)
    }

    private fun validatePython(program: String, args: List<String>): ValidationResult {
        if (args.isEmpty()) {
            // Interactive REPL would hang in a background task.
            return ValidationResult.Rejected(RejectReason.FORBIDDEN_ARGUMENT, "(interactive)")
        }
        var index = 0
        while (index < args.size && args[index].startsWith("-")) {
            val flag = args[index]
            if (flag !in PYTHON_ALLOWED_FLAGS) {
                return ValidationResult.Rejected(RejectReason.FORBIDDEN_ARGUMENT, flag)
            }
            if (flag == "-V" || flag == "--version") {
                return ValidationResult.Allowed(program, args, CommandRisk.READ_ONLY)
            }
            index++
        }
        val script = args.getOrNull(index)
            ?: return ValidationResult.Rejected(RejectReason.FORBIDDEN_ARGUMENT, "(no script)")
        if (!script.endsWith(".py")) {
            return ValidationResult.Rejected(RejectReason.FORBIDDEN_ARGUMENT, script)
        }
        if (hasParentSegment(script)) {
            return ValidationResult.Rejected(RejectReason.PATH_TRAVERSAL, script)
        }
        return ValidationResult.Allowed(program, args, CommandRisk.WRITE)
    }

    private fun validatePkg(args: List<String>): ValidationResult {
        val sub = args.firstOrNull()
            ?: return ValidationResult.Rejected(RejectReason.FORBIDDEN_SUBCOMMAND, "(none)")
        val risk = when (sub) {
            in PKG_READ_ONLY -> CommandRisk.READ_ONLY
            in PKG_WRITE -> CommandRisk.WRITE
            else -> return ValidationResult.Rejected(RejectReason.FORBIDDEN_SUBCOMMAND, sub)
        }
        return ValidationResult.Allowed("pkg", args, risk)
    }

    private fun validateOllama(args: List<String>): ValidationResult {
        val sub = args.firstOrNull()
            ?: return ValidationResult.Rejected(RejectReason.FORBIDDEN_SUBCOMMAND, "(none)")
        val risk = when (sub) {
            in OLLAMA_READ_ONLY -> CommandRisk.READ_ONLY
            in OLLAMA_WRITE -> CommandRisk.WRITE
            in OLLAMA_DESTRUCTIVE -> CommandRisk.DESTRUCTIVE
            // `serve` and `run` never terminate / are interactive -> only via the setup screen.
            else -> return ValidationResult.Rejected(RejectReason.FORBIDDEN_SUBCOMMAND, sub)
        }
        return ValidationResult.Allowed("ollama", args, risk)
    }

    private fun isRecursiveFlag(arg: String): Boolean {
        if (arg == "--recursive") return true
        if (arg.startsWith("--") || !arg.startsWith("-") || arg.length < 2) return false
        return arg.drop(1).any { it == 'r' || it == 'R' }
    }

    private fun hasParentSegment(arg: String): Boolean =
        arg.split('/', '=').any { it == ".." }

    sealed class TokenizeResult {
        data class Ok(val tokens: List<String>) : TokenizeResult()
        object UnclosedQuote : TokenizeResult()
        data class Operator(val operator: Char) : TokenizeResult()
    }

    /**
     * Splits a command line like a POSIX shell would (single quotes, double quotes, backslash
     * escapes) without performing any expansion. Unquoted shell operators are reported.
     */
    fun tokenize(input: String): TokenizeResult {
        val tokens = mutableListOf<String>()
        val current = StringBuilder()
        var inToken = false
        var i = 0
        while (i < input.length) {
            val c = input[i]
            when {
                c == '\'' -> {
                    val end = input.indexOf('\'', i + 1)
                    if (end < 0) return TokenizeResult.UnclosedQuote
                    current.append(input, i + 1, end)
                    inToken = true
                    i = end
                }
                c == '"' -> {
                    var j = i + 1
                    var closed = false
                    while (j < input.length) {
                        val d = input[j]
                        if (d == '\\' && j + 1 < input.length && input[j + 1] in "\"\\") {
                            current.append(input[j + 1])
                            j += 2
                            continue
                        }
                        if (d == '"') {
                            closed = true
                            break
                        }
                        current.append(d)
                        j++
                    }
                    if (!closed) return TokenizeResult.UnclosedQuote
                    inToken = true
                    i = j
                }
                c == '\\' -> {
                    if (i + 1 < input.length) {
                        current.append(input[i + 1])
                        i++
                    }
                    inToken = true
                }
                c.isWhitespace() -> {
                    if (inToken) {
                        tokens += current.toString()
                        current.clear()
                        inToken = false
                    }
                }
                c in UNQUOTED_OPERATORS -> return TokenizeResult.Operator(c)
                else -> {
                    current.append(c)
                    inToken = true
                }
            }
            i++
        }
        if (inToken) tokens += current.toString()
        return TokenizeResult.Ok(tokens)
    }
}
