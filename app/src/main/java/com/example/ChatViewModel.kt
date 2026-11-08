package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject

enum class EntryKind { USER, ASSISTANT, COMMAND, INFO, ERROR }

enum class CommandStatus { PENDING_APPROVAL, RUNNING, DONE, FAILED, BLOCKED }

data class ChatEntry(
    val id: Long,
    val kind: EntryKind,
    val text: String,
    val command: String? = null,
    val status: CommandStatus? = null,
    val risk: CommandRisk? = null,
    val tool: AgentTool? = null,
)

data class PendingApproval(
    val entryId: Long,
    val command: String,
    val prepared: PreparedAction,
    val risk: CommandRisk,
    val step: Int,
) {
    val isDestructive: Boolean get() = risk == CommandRisk.DESTRUCTIVE
}

data class ChatUiState(
    val entries: List<ChatEntry> = emptyList(),
    val isBusy: Boolean = false,
    val isThinking: Boolean = false,
    val pendingApproval: PendingApproval? = null,
    val provider: LlmProviderType? = null,
    val model: String? = null,
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val settings = SecurityHelper(application)
    private val history = mutableListOf<LlmMessage>()
    private var nextId = 1L
    private var job: Job? = null

    /** Set once untrusted web content entered the current task (reset by the next user message). */
    private var untrustedContext = false

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        restore()
        refreshProvider()
    }

    private fun t(key: String) = Strings.get(key, settings.getLanguage())

    fun refreshProvider() {
        val provider = settings.getActiveProvider()
        _state.update { it.copy(provider = provider, model = provider?.let(settings::getSelectedModel)) }
    }

    fun selectProvider(provider: LlmProviderType) {
        settings.setActiveProvider(provider)
        refreshProvider()
    }

    fun send(text: String) {
        val message = text.trim()
        if (message.isEmpty() || _state.value.isBusy) return
        addEntry(EntryKind.USER, message)
        history += LlmMessage(ChatRole.USER, message)
        untrustedContext = false
        persist()
        job = viewModelScope.launch { agentTurn(step = 0) }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { s ->
            s.copy(
                isBusy = false,
                isThinking = false,
                pendingApproval = null,
                entries = s.entries.map {
                    if (it.status == CommandStatus.RUNNING || it.status == CommandStatus.PENDING_APPROVAL) {
                        it.copy(status = CommandStatus.BLOCKED, text = t("cmd_cancelled"))
                    } else it
                },
            )
        }
        addEntry(EntryKind.INFO, t("stopped"))
        persist()
    }

    fun clear() {
        job?.cancel()
        history.clear()
        settings.clearChatHistory()
        _state.update { it.copy(entries = emptyList(), isBusy = false, isThinking = false, pendingApproval = null) }
    }

    fun approvePending() {
        val pending = _state.value.pendingApproval ?: return
        _state.update { it.copy(pendingApproval = null) }
        settings.appendAudit("APPROVED", pending.command)
        job = viewModelScope.launch { execute(pending.entryId, pending.command, pending.prepared, pending.step) }
    }

    fun denyPending() {
        val pending = _state.value.pendingApproval ?: return
        updateEntry(pending.entryId) { it.copy(status = CommandStatus.BLOCKED, text = t("cmd_blocked_by_user")) }
        history += LlmMessage(ChatRole.USER, AgentProtocol.formatRejected(pending.command, "the user blocked it"))
        settings.appendAudit("DENIED", pending.command)
        _state.update { it.copy(pendingApproval = null, isBusy = false) }
        persist()
    }

    // --- Agent loop -----------------------------------------------------------------------

    private suspend fun agentTurn(step: Int) {
        val provider = settings.getActiveProvider()
        if (provider == null) {
            addEntry(EntryKind.ERROR, t("err_no_model"))
            _state.update { it.copy(isBusy = false) }
            return
        }
        _state.update { it.copy(isBusy = true, isThinking = true) }
        val reply = try {
            val client = LlmClientFactory.create(settings, provider)
            client.complete(
                AgentProtocol.systemPrompt(settings.getSecurityLevel(), settings.getLanguage(), settings.getMaxSteps()),
                AgentProtocol.prepareHistory(history),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val error = e as? LlmException ?: LlmException(LlmException.Kind.SERVER, e.message ?: e.javaClass.simpleName)
            addEntry(EntryKind.ERROR, errorText(error, provider))
            _state.update { it.copy(isBusy = false, isThinking = false) }
            persist()
            return
        }
        _state.update { it.copy(isThinking = false) }

        history += LlmMessage(ChatRole.ASSISTANT, reply)
        val parsed = AgentProtocol.parse(reply)
        if (parsed.text.isNotBlank()) addEntry(EntryKind.ASSISTANT, parsed.text)
        persist()

        val action = parsed.action
        if (action == null) {
            _state.update { it.copy(isBusy = false) }
            return
        }
        handleAction(action, step)
    }

    private suspend fun handleAction(action: AgentAction, step: Int) {
        val level = settings.getSecurityLevel()
        when (val check = ActionValidator.validate(action, level)) {
            is ActionCheck.Rejected -> {
                val reason = rejectText(check.reason, check.detail)
                addCommandEntry(check.display, CommandStatus.BLOCKED, reason, tool = toolOf(action))
                history += LlmMessage(
                    ChatRole.USER,
                    AgentProtocol.formatRejected(check.display, "${check.reason} ${check.detail}".trim()),
                )
                settings.appendAudit("BLOCKED", "${check.display} (${check.reason} ${check.detail})".trim())
                persist()
                if (check.reason == RejectReason.SANDBOX) {
                    _state.update { it.copy(isBusy = false) }
                } else {
                    // Let the model correct itself (bounded by the step limit).
                    continueOrFinish(step)
                }
            }
            is ActionCheck.Ok -> {
                val tool = toolOf(action)
                val decision = AutonomyPolicy.decide(level, check.risk, action is AgentAction.Fetch, untrustedContext)
                if (decision == AutonomyPolicy.Decision.ASK) {
                    val id = addCommandEntry(check.display, CommandStatus.PENDING_APPROVAL, t("cmd_waiting"), check.risk, tool)
                    _state.update {
                        it.copy(pendingApproval = PendingApproval(id, check.display, check.prepared, check.risk, step), isBusy = true)
                    }
                    persist()
                } else {
                    val id = addCommandEntry(check.display, CommandStatus.RUNNING, t("cmd_running"), check.risk, tool)
                    settings.appendAudit("AUTO", check.display)
                    execute(id, check.display, check.prepared, step)
                }
            }
        }
    }

    private fun toolOf(action: AgentAction) = when (action) {
        is AgentAction.Shell -> AgentTool.TERMUX
        is AgentAction.WriteFile -> AgentTool.FILE
        is AgentAction.Fetch -> AgentTool.WEB
    }

    private suspend fun execute(entryId: Long, display: String, prepared: PreparedAction, step: Int) {
        _state.update { it.copy(isBusy = true) }
        updateEntry(entryId) { it.copy(status = CommandStatus.RUNNING, text = t("cmd_running")) }
        when (prepared) {
            is PreparedAction.Fetch -> fetch(entryId, prepared.url)
            is PreparedAction.Termux -> if (!runTermux(entryId, display, prepared)) return
        }
        persist()
        continueOrFinish(step)
    }

    private suspend fun fetch(entryId: Long, url: String) {
        val result = WebTool.fetch(url)
        untrustedContext = true
        val display = if (result.error != null) {
            t("web_failed") + result.error
        } else {
            "HTTP ${result.status}\n" + AgentProtocol.truncate(result.text.ifBlank { t("no_output") }, 600)
        }
        updateEntry(entryId) {
            it.copy(status = if (result.isSuccess) CommandStatus.DONE else CommandStatus.FAILED, text = display)
        }
        settings.appendAudit(if (result.isSuccess) "DONE" else "FAILED", "fetch $url -> ${result.status ?: result.error}")
        history += LlmMessage(ChatRole.USER, AgentProtocol.formatFetchResult(result))
    }

    /** Runs a Termux action. Returns `false` if the agent loop has to stop (Termux unreachable). */
    private suspend fun runTermux(entryId: Long, display: String, prepared: PreparedAction.Termux): Boolean {
        val executionId = TermuxResultHub.newExecutionId()
        val result = coroutineScope {
            // Subscribe before sending so a fast result cannot be missed.
            val awaiting = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeoutOrNull(COMMAND_TIMEOUT_MS) {
                    TermuxResultHub.results.first { it.executionId == executionId }
                }
            }
            val launch = TermuxBridge.execute(getApplication(), prepared.command, executionId)
            if (launch != TermuxLaunch.Sent) {
                awaiting.cancel()
                val reason = when (launch) {
                    TermuxLaunch.NotInstalled -> t("termux_required")
                    TermuxLaunch.PermissionMissing -> t("err_termux_permission")
                    is TermuxLaunch.Failed -> t("cmd_failed") + launch.message
                    TermuxLaunch.Sent -> ""
                }
                updateEntry(entryId) { it.copy(status = CommandStatus.FAILED, text = reason) }
                history += LlmMessage(ChatRole.USER, AgentProtocol.formatRejected(display, "Termux is not reachable"))
                settings.appendAudit("FAILED", "$display (Termux not reachable)")
                _state.update { it.copy(isBusy = false) }
                persist()
                return@coroutineScope null
            }
            awaiting.await() ?: run {
                updateEntry(entryId) { it.copy(status = CommandStatus.FAILED, text = t("err_termux_timeout")) }
                history += LlmMessage(ChatRole.USER, AgentProtocol.formatRejected(display, "no result from Termux (timeout)"))
                settings.appendAudit("FAILED", "$display (timeout)")
                _state.update { it.copy(isBusy = false) }
                persist()
                null
            }
        } ?: return false

        val shown = when {
            !result.started -> t("err_termux_start") + (result.errMessage ?: "")
            prepared.tool == AgentTool.FILE && result.isSuccess -> t("file_written")
            else -> buildString {
                append(AgentProtocol.truncate(result.stdout.ifBlank { result.stderr }.ifBlank { t("no_output") }, 2_000))
                if (result.exitCode != null && result.exitCode != 0) append("\n\nexit ${result.exitCode}")
            }
        }
        updateEntry(entryId) {
            it.copy(status = if (result.isSuccess) CommandStatus.DONE else CommandStatus.FAILED, text = shown)
        }
        settings.appendAudit(if (result.isSuccess) "DONE" else "FAILED", "$display (exit ${result.exitCode ?: result.errCode})")
        val feedback = if (prepared.tool == AgentTool.FILE) {
            val path = prepared.command.arguments.last()
            AgentProtocol.formatWriteResult(path, prepared.command.stdin?.length ?: 0, result)
        } else {
            AgentProtocol.formatResult(display, result)
        }
        history += LlmMessage(ChatRole.USER, feedback)
        return true
    }

    private suspend fun continueOrFinish(step: Int) {
        if (step + 1 < settings.getMaxSteps()) {
            agentTurn(step + 1)
        } else {
            addEntry(EntryKind.INFO, t("step_limit"))
            _state.update { it.copy(isBusy = false) }
            persist()
        }
    }

    private fun errorText(e: LlmException, provider: LlmProviderType): String {
        val base = when (e.kind) {
            LlmException.Kind.NOT_CONFIGURED -> t("err_no_model")
            LlmException.Kind.AUTH -> t("err_auth")
            LlmException.Kind.RATE_LIMIT -> t("err_rate_limit")
            LlmException.Kind.NETWORK ->
                if (provider == LlmProviderType.LOCAL) t("err_ollama_offline") else t("err_network")
            LlmException.Kind.TIMEOUT -> t("err_timeout")
            LlmException.Kind.SERVER -> t("err_server")
            LlmException.Kind.BAD_REQUEST -> t("err_bad_request")
            LlmException.Kind.REFUSED -> t("err_refused")
            LlmException.Kind.EMPTY -> t("err_empty")
        }
        val detail = e.message?.takeIf { it.isNotBlank() }?.take(300)
        return if (detail != null) "$base\n($detail)" else base
    }

    private fun rejectText(reason: RejectReason, detail: String): String {
        val key = when (reason) {
            RejectReason.NOT_WHITELISTED -> "rej_not_whitelisted"
            RejectReason.SHELL_OPERATOR -> "rej_shell_operator"
            RejectReason.PATH_TRAVERSAL -> "rej_path"
            RejectReason.FORBIDDEN_ARGUMENT, RejectReason.FORBIDDEN_SUBCOMMAND -> "rej_argument"
            RejectReason.HARD_BLOCKED -> "rej_hard_blocked"
            RejectReason.INVALID_PATH -> "rej_invalid_path"
            RejectReason.INVALID_URL -> "rej_invalid_url"
            RejectReason.SANDBOX -> "sandbox_blocked"
            else -> "security_block"
        }
        return if (detail.isNotBlank()) "${t(key)} ($detail)" else t(key)
    }

    // --- State helpers --------------------------------------------------------------------

    private fun addEntry(kind: EntryKind, text: String): Long {
        val id = nextId++
        _state.update { it.copy(entries = (it.entries + ChatEntry(id, kind, text)).takeLast(MAX_ENTRIES)) }
        return id
    }

    private fun addCommandEntry(
        command: String,
        status: CommandStatus,
        text: String,
        risk: CommandRisk? = null,
        tool: AgentTool? = null,
    ): Long {
        val id = nextId++
        val entry = ChatEntry(id, EntryKind.COMMAND, text, command, status, risk, tool)
        _state.update { it.copy(entries = (it.entries + entry).takeLast(MAX_ENTRIES)) }
        return id
    }

    private fun updateEntry(id: Long, transform: (ChatEntry) -> ChatEntry) {
        _state.update { s -> s.copy(entries = s.entries.map { if (it.id == id) transform(it) else it }) }
    }

    private fun persist() {
        val json = JSONObject()
        json.put("entries", JSONArray().apply {
            _state.value.entries.forEach { e ->
                put(JSONObject().apply {
                    put("id", e.id)
                    put("kind", e.kind.name)
                    put("text", e.text)
                    e.command?.let { put("command", it) }
                    // A pending/running command cannot survive a restart.
                    val status = when (e.status) {
                        CommandStatus.PENDING_APPROVAL, CommandStatus.RUNNING -> CommandStatus.BLOCKED
                        else -> e.status
                    }
                    status?.let { put("status", it.name) }
                    e.risk?.let { put("risk", it.name) }
                    e.tool?.let { put("tool", it.name) }
                })
            }
        })
        json.put("history", JSONArray().apply {
            history.takeLast(AgentProtocol.MAX_HISTORY_MESSAGES * 2).forEach {
                put(JSONObject().put("role", it.role.name).put("content", it.content))
            }
        })
        settings.saveChatHistory(json.toString())
    }

    private fun restore() {
        val raw = settings.getChatHistory() ?: return
        try {
            val json = JSONObject(raw)
            val entries = json.optJSONArray("entries") ?: JSONArray()
            val restored = (0 until entries.length()).map { i ->
                val o = entries.getJSONObject(i)
                ChatEntry(
                    id = o.getLong("id"),
                    kind = EntryKind.valueOf(o.getString("kind")),
                    text = o.optString("text"),
                    command = o.optString("command").takeIf { o.has("command") },
                    status = o.optString("status").takeIf { o.has("status") }?.let { CommandStatus.valueOf(it) },
                    risk = o.optString("risk").takeIf { o.has("risk") }?.let { CommandRisk.valueOf(it) },
                    tool = o.optString("tool").takeIf { o.has("tool") }?.let { AgentTool.valueOf(it) },
                )
            }
            val messages = json.optJSONArray("history") ?: JSONArray()
            for (i in 0 until messages.length()) {
                val o = messages.getJSONObject(i)
                history += LlmMessage(ChatRole.valueOf(o.getString("role")), o.getString("content"))
            }
            nextId = (restored.maxOfOrNull { it.id } ?: 0L) + 1
            _state.update { it.copy(entries = restored) }
        } catch (e: Exception) {
            // Corrupt or incompatible history: start fresh instead of crashing.
            history.clear()
            settings.clearChatHistory()
        }
    }

    companion object {
        private const val MAX_ENTRIES = 200
        private const val COMMAND_TIMEOUT_MS = 5 * 60_000L
    }
}
