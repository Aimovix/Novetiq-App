package com.example

/**
 * The tools the model can call. Exactly one action per reply, written as a fenced block at the
 * end of the reply (see [AgentProtocol]).
 */
sealed class AgentAction {
    /** ```termux block. In full autonomy the whole block is a bash script, otherwise only [firstLine] runs. */
    data class Shell(val script: String) : AgentAction() {
        val firstLine: String?
            get() = script.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() && !it.startsWith("#") }
    }

    /** ```write <path> block: creates or replaces a text file inside Termux. */
    data class WriteFile(val path: String, val content: String) : AgentAction()

    /** ```fetch <url> block: downloads a web page (read only) and returns its text. */
    data class Fetch(val url: String) : AgentAction()
}

/** What the app actually does once an action passed validation. */
sealed class PreparedAction {
    data class Termux(val command: TermuxCommand, val tool: AgentTool) : PreparedAction()
    data class Fetch(val url: String) : PreparedAction()
}

/** Shown as the label of the action card in the chat. Names are persisted - do not rename. */
enum class AgentTool { TERMUX, FILE, WEB }

sealed class ActionCheck {
    data class Ok(val prepared: PreparedAction, val risk: CommandRisk, val display: String) : ActionCheck()
    data class Rejected(val reason: RejectReason, val detail: String, val display: String) : ActionCheck()
}

/** Turns a model action into something executable, according to the security level. */
object ActionValidator {

    const val MAX_FILE_CHARS = 200_000
    private const val MAX_PATH_LENGTH = 500

    fun validate(action: AgentAction, level: Int): ActionCheck {
        val display = displayOf(action, level)
        if (level == 3) return ActionCheck.Rejected(RejectReason.SANDBOX, "", display)
        return when (action) {
            is AgentAction.Shell -> validateShell(action, level, display)
            is AgentAction.WriteFile -> validateWrite(action, display)
            is AgentAction.Fetch -> validateFetch(action, display)
        }
    }

    fun displayOf(action: AgentAction, level: Int): String = when (action) {
        is AgentAction.Shell -> if (level == 0) action.script.trim() else action.firstLine.orEmpty()
        is AgentAction.WriteFile -> "write ${action.path} (${action.content.length} chars)"
        is AgentAction.Fetch -> "fetch ${action.url}"
    }

    private fun validateShell(action: AgentAction.Shell, level: Int, display: String): ActionCheck {
        if (level == 0) {
            return when (val verdict = ShellGuard.check(action.script)) {
                is ShellVerdict.Blocked -> ActionCheck.Rejected(verdict.reason, verdict.detail, display)
                is ShellVerdict.Allowed -> ActionCheck.Ok(
                    PreparedAction.Termux(
                        TermuxCommand(
                            executable = "${TermuxConstants.BIN}/bash",
                            arguments = listOf("-c", action.script.trim()),
                            background = true,
                            label = "Synaptiq agent (full autonomy)",
                            risk = verdict.risk,
                        ),
                        AgentTool.TERMUX,
                    ),
                    verdict.risk,
                    display,
                )
            }
        }
        val line = action.firstLine ?: return ActionCheck.Rejected(RejectReason.EMPTY, "", display)
        return when (val v = CommandValidator.validate(line)) {
            is ValidationResult.Rejected -> ActionCheck.Rejected(v.reason, v.detail, display)
            is ValidationResult.Allowed ->
                ActionCheck.Ok(PreparedAction.Termux(TermuxCommand.fromValidated(v), AgentTool.TERMUX), v.risk, display)
        }
    }

    private fun validateWrite(action: AgentAction.WriteFile, display: String): ActionCheck {
        val path = normalizePath(action.path)
            ?: return ActionCheck.Rejected(RejectReason.INVALID_PATH, action.path, display)
        if (action.content.length > MAX_FILE_CHARS) {
            return ActionCheck.Rejected(RejectReason.TOO_LONG, "> $MAX_FILE_CHARS chars", display)
        }
        val name = path.substringAfterLast('/')
        val protected = path.contains("/.termux/") || name in PROTECTED_DOTFILES
        val risk = if (protected) CommandRisk.DESTRUCTIVE else CommandRisk.WRITE
        // The path is passed as $1, never interpolated into the script, so it cannot inject code.
        val command = TermuxCommand(
            executable = "${TermuxConstants.BIN}/bash",
            arguments = listOf("-c", WRITE_SCRIPT, "synaptiq-write", path),
            background = true,
            label = "Synaptiq agent: write file",
            risk = risk,
            stdin = action.content,
        )
        return ActionCheck.Ok(PreparedAction.Termux(command, AgentTool.FILE), risk, display)
    }

    private fun validateFetch(action: AgentAction.Fetch, display: String): ActionCheck {
        val error = WebTool.validateUrl(action.url)
        if (error != null) return ActionCheck.Rejected(RejectReason.INVALID_URL, error, display)
        return ActionCheck.Ok(PreparedAction.Fetch(action.url.trim()), CommandRisk.READ_ONLY, display)
    }

    private const val WRITE_SCRIPT = "mkdir -p -- \"\$(dirname -- \"\$1\")\" && cat > \"\$1\""

    private val PROTECTED_DOTFILES = setOf(".bashrc", ".bash_profile", ".profile", ".zshrc", ".zprofile", ".inputrc")

    private val WRITABLE_ROOTS = listOf(
        TermuxConstants.HOME + "/",
        "/sdcard/",
        "/storage/emulated/0/",
    )

    /** Resolves `~` and relative paths against the Termux home and rejects anything outside it. */
    fun normalizePath(raw: String): String? {
        val path = raw.trim().removeSurrounding("\"").removeSurrounding("'").trim()
        if (path.isEmpty() || path.length > MAX_PATH_LENGTH) return null
        if (path.any { it.isISOControl() }) return null
        if (path.endsWith("/")) return null
        if (path.split('/').any { it == ".." }) return null
        val absolute = when {
            path == "~" -> return null
            path.startsWith("~/") -> TermuxConstants.HOME + path.removePrefix("~")
            path.startsWith("/") -> path
            else -> TermuxConstants.HOME + "/" + path.removePrefix("./")
        }
        return absolute.takeIf { abs -> WRITABLE_ROOTS.any { abs.startsWith(it) && abs.length > it.length } }
    }
}

/** Decides whether a validated action runs automatically or needs the user's approval. */
object AutonomyPolicy {
    enum class Decision { AUTO, ASK }

    /**
     * @param untrustedContext `true` once web content entered the current task. On level 1 every
     *   further action that is not read only (and every further web request, which could leak
     *   data through the URL) then needs approval - protection against prompt injection.
     */
    fun decide(level: Int, risk: CommandRisk, isFetch: Boolean, untrustedContext: Boolean): Decision = when {
        level >= 2 -> Decision.ASK
        risk == CommandRisk.DESTRUCTIVE || risk == CommandRisk.EXTERNAL -> Decision.ASK
        level == 1 && untrustedContext && (risk != CommandRisk.READ_ONLY || isFetch) -> Decision.ASK
        else -> Decision.AUTO
    }
}

sealed class ShellVerdict {
    data class Allowed(val risk: CommandRisk) : ShellVerdict()
    data class Blocked(val reason: RejectReason, val detail: String) : ShellVerdict()
}

/**
 * Guard for full autonomy (level 0). Scripts run through bash, so instead of a whitelist this uses
 * - a hard block list of catastrophic operations that are never executed, not even with approval,
 * - a risk classification: anything that deletes, kills, uninstalls or reaches the outside world
 *   (SMS, calls, uploads, piping downloads into a shell) needs the user's approval.
 */
object ShellGuard {

    const val MAX_SCRIPT_LENGTH = 8_000

    private val IGNORE_CASE = setOf(RegexOption.IGNORE_CASE)

    private val HARD_BLOCKS: List<Pair<Regex, String>> = listOf(
        Regex("""(^|[\s;&|(`])(su|sudo|tsu|doas)(\s|$)""") to "root",
        Regex(""":\s*\(\s*\)\s*\{""") to "fork bomb",
        Regex("""\b(\w+)\s*\(\s*\)\s*\{[^}]*\b\1\s*\|\s*\1""") to "fork bomb",
        Regex("""\b(mkfs(\.\w+)?|mke2fs|wipefs|fdisk|sfdisk|parted|mkswap)\b""") to "disk formatting",
        Regex("""\bof=/dev/""") to "raw device write",
        Regex(""">\s*/dev/(block|sd[a-z]|mmcblk|nvme|loop)""") to "raw device write",
        Regex("""\b(reboot|shutdown|poweroff|halt)\b""") to "power control",
        Regex("""termux\.properties""") to "Termux security settings",
    )

    private val DESTRUCTIVE: List<Regex> = listOf(
        Regex("""(^|[\s;&|(`])(rm|rmdir|unlink|shred|truncate|dd|mv)(\s|$)"""),
        Regex("""\bfind\b.*\s-(delete|exec|execdir|ok)\b"""),
        Regex("""\bgit\s+(reset\s+--hard|clean\b|checkout\s+--|restore\b|branch\s+-D|push\b.*(\s--force|\s-f\b))"""),
        Regex("""\b(pkg|apt|apt-get|dpkg)\s+(uninstall|remove|purge|autoremove|-r|-P)\b"""),
        Regex("""\b(pip3?|npm|pnpm|yarn|gem|cargo)\s+(uninstall|remove|rm)\b"""),
        Regex("""\bollama\s+rm\b"""),
        Regex("""(^|[\s;&|(`])(kill|pkill|killall)(\s|$)"""),
        Regex("""\bch(mod|own)\s+(-\w*R|--recursive)"""),
        Regex("""\bcrontab\s+-r\b"""),
    )

    private val EXTERNAL: List<Regex> = listOf(
        Regex("""\btermux-(sms-send|telephony-call|share|clipboard-set|wallpaper|camera-photo|microphone-record|location)\b"""),
        Regex("""\b(curl|wget)\b[^|]*\|\s*(sudo\s+)?(ba|z|da|k)?sh\b"""),
        Regex("""\bcurl\b.*(\s-X\s*(POST|PUT|PATCH|DELETE)|\s(-d|--data\S*|-F|--form|-T|--upload-file)\s)""", IGNORE_CASE),
        Regex("""\bwget\b.*--post-(data|file)"""),
        Regex("""(^|[\s;&|(`])(ssh|scp|sftp|rsync|ftp|nc|ncat|netcat|telnet)(\s|$)"""),
        Regex("""\bgit\s+push\b"""),
        Regex("""\b(npm|pnpm|yarn)\s+publish\b"""),
    )

    /** Programs whose use never changes anything. */
    private val READ_ONLY_PROGRAMS = setOf(
        "ls", "cat", "echo", "printf", "pwd", "whoami", "date", "uname", "df", "du", "head", "tail",
        "wc", "grep", "egrep", "fgrep", "find", "stat", "file", "which", "type", "env", "printenv",
        "id", "uptime", "free", "ps", "top", "tree", "sort", "uniq", "cut", "tr", "awk", "sed", "less",
        "more", "diff", "cmp", "md5sum", "sha256sum", "basename", "dirname", "realpath", "readlink",
        "cd", "true", "false", "test", "[", "nproc", "lscpu", "getprop", "termux-info",
    )

    private val CRITICAL_TARGETS = setOf(
        "/", "/*", "~", "~/*", "\$HOME", "\$HOME/*", "\${HOME}", "\${HOME}/*", "*", ".", "./*", "..",
        "/data", "/data/*", "/data/data", "/sdcard", "/sdcard/*", "/storage", "/storage/*",
        "/storage/emulated", "/storage/emulated/0", "/storage/emulated/0/*", "/system", "/vendor",
        "\$PREFIX", "\$PREFIX/*", "\${PREFIX}", "/data/data/com.termux", "/data/data/com.termux/files",
        TermuxConstants.PREFIX, "${TermuxConstants.PREFIX}/*", TermuxConstants.HOME, "${TermuxConstants.HOME}/*",
    )

    fun check(script: String): ShellVerdict {
        val text = script.trim()
        if (text.isEmpty()) return ShellVerdict.Blocked(RejectReason.EMPTY, "")
        if (text.length > MAX_SCRIPT_LENGTH) return ShellVerdict.Blocked(RejectReason.TOO_LONG, "")
        if (text.any { it.isISOControl() && it != '\n' && it != '\t' && it != '\r' }) {
            return ShellVerdict.Blocked(RejectReason.CONTROL_CHARACTER, "")
        }
        HARD_BLOCKS.firstOrNull { it.first.containsMatchIn(text) }?.let {
            return ShellVerdict.Blocked(RejectReason.HARD_BLOCKED, it.second)
        }
        recursiveCriticalTarget(text)?.let {
            return ShellVerdict.Blocked(RejectReason.HARD_BLOCKED, "recursive operation on $it")
        }
        val risk = when {
            EXTERNAL.any { it.containsMatchIn(text) } -> CommandRisk.EXTERNAL
            DESTRUCTIVE.any { it.containsMatchIn(text) } -> CommandRisk.DESTRUCTIVE
            isReadOnly(text) -> CommandRisk.READ_ONLY
            else -> CommandRisk.WRITE
        }
        return ShellVerdict.Allowed(risk)
    }

    /** Finds `rm -r`, `chmod -R`, `chown -R` aimed at home, storage or system folders. */
    private fun recursiveCriticalTarget(text: String): String? {
        for (segment in segments(text)) {
            val words = segment.split(Regex("\\s+")).filter { it.isNotEmpty() }
                .map { it.trim('"', '\'') }
                .dropWhile { it == "command" || it == "busybox" || it == "nohup" || it == "time" }
            val program = words.firstOrNull()?.substringAfterLast('/') ?: continue
            if (program !in setOf("rm", "chmod", "chown")) continue
            val args = words.drop(1)
            val recursive = args.any { a ->
                a == "--recursive" || (a.startsWith("-") && !a.startsWith("--") && a.drop(1).any { it == 'r' || it == 'R' })
            }
            if (!recursive) continue
            args.filterNot { it.startsWith("-") }.forEach { target ->
                val normalized = target.trimEnd('/').ifEmpty { "/" }
                if (normalized in CRITICAL_TARGETS || target in CRITICAL_TARGETS) return target
            }
        }
        return null
    }

    private fun isReadOnly(text: String): Boolean {
        // Output redirection into a file (but not into /dev/null or between streams) writes.
        val withoutHarmless = text.replace(Regex("""\d?>&\d"""), "").replace(Regex("""\d?>>?\s*/dev/null"""), "")
        if (withoutHarmless.contains('>')) return false
        if (Regex("""\bsed\b[^|;&]*\s-i""").containsMatchIn(text)) return false
        return segments(text).all { segment ->
            val program = segment.trim().split(Regex("\\s+")).firstOrNull()?.substringAfterLast('/') ?: return@all true
            program.isEmpty() || program in READ_ONLY_PROGRAMS
        }
    }

    private fun segments(text: String): List<String> =
        text.split(Regex("""\|\||&&|[;|&\n]|\$\(|`|\(|\)""")).map { it.trim() }.filter { it.isNotEmpty() }
}
