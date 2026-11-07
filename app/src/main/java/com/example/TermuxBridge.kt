package com.example

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.concurrent.atomic.AtomicInteger

@SuppressLint("SdCardPath") // Fixed Termux paths, not our own app storage.
object TermuxConstants {
    const val PACKAGE = "com.termux"
    const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"

    const val PREFIX = "/data/data/com.termux/files/usr"
    const val HOME = "/data/data/com.termux/files/home"
    const val BIN = "$PREFIX/bin"

    const val EXTRA_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    const val EXTRA_COMMAND_LABEL = "com.termux.RUN_COMMAND_COMMAND_LABEL"
    const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    const val RESULT_BUNDLE = "result"
    const val RESULT_STDOUT = "stdout"
    const val RESULT_STDERR = "stderr"
    const val RESULT_EXIT_CODE = "exitCode"
    const val RESULT_ERR = "err"
    const val RESULT_ERRMSG = "errmsg"

    /** Termux reports `err == -1` (Errno.ERRNO_SUCCESS) when the command could be started. */
    const val ERR_SUCCESS = -1

    /** Line the user has to add once in Termux so that other apps may run commands. */
    const val ALLOW_EXTERNAL_APPS_COMMAND =
        "mkdir -p ~/.termux && echo \"allow-external-apps=true\" >> ~/.termux/termux.properties && termux-reload-settings"
}

/**
 * A command to run inside Termux. The executable is started directly (no shell), the arguments
 * are passed verbatim.
 *
 * @param background `true` runs it as an invisible background task whose output is returned to
 *   the app. `false` opens a visible Termux terminal session (used for long running setup steps).
 */
data class TermuxCommand(
    val executable: String,
    val arguments: List<String>,
    val background: Boolean,
    val label: String,
    val risk: CommandRisk = CommandRisk.WRITE,
) {
    val displayText: String
        get() = (listOf(executable.substringAfterLast('/')) + arguments.map(::quoteIfNeeded)).joinToString(" ")

    companion object {
        /** Builds the command for a validated agent request (background, output captured). */
        fun fromValidated(allowed: ValidationResult.Allowed, label: String = "Synaptiq agent") = TermuxCommand(
            executable = "${TermuxConstants.BIN}/${allowed.executable}",
            arguments = allowed.args,
            background = true,
            label = label,
            risk = allowed.risk,
        )

        private fun quoteIfNeeded(arg: String): String =
            if (arg.isEmpty() || arg.any { it.isWhitespace() || it in "\"'\\;|&<>`$" }) {
                "'" + arg.replace("'", "'\\''") + "'"
            } else arg
    }
}

/** Fixed, app-defined commands for the setup screens. They are trusted (not AI generated). */
object SetupCommands {
    /** Refresh package lists and upgrade without interactive prompts (correct order!). */
    val UPDATE_PACKAGES = TermuxCommand(
        executable = "${TermuxConstants.BIN}/bash",
        arguments = listOf("-c", "pkg update -y && pkg upgrade -y"),
        background = false,
        label = "Synaptiq setup: pkg update",
    )

    val INSTALL_PYTHON = TermuxCommand(
        executable = "${TermuxConstants.BIN}/pkg",
        arguments = listOf("install", "-y", "python"),
        background = false,
        label = "Synaptiq setup: python",
    )

    val INSTALL_OLLAMA = TermuxCommand(
        executable = "${TermuxConstants.BIN}/pkg",
        arguments = listOf("install", "-y", "ollama"),
        background = false,
        label = "Synaptiq setup: ollama",
    )

    /** Starts the local model server in its own visible Termux session (keeps running). */
    val START_OLLAMA = TermuxCommand(
        executable = "${TermuxConstants.BIN}/ollama",
        arguments = listOf("serve"),
        background = false,
        label = "Synaptiq: ollama serve",
    )

    fun pullModel(tag: String) = TermuxCommand(
        executable = "${TermuxConstants.BIN}/ollama",
        arguments = listOf("pull", tag),
        background = false,
        label = "Synaptiq setup: ollama pull $tag",
    )
}

data class TermuxResult(
    val executionId: Int,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val errCode: Int,
    val errMessage: String?,
) {
    val started: Boolean get() = errCode == TermuxConstants.ERR_SUCCESS
    val isSuccess: Boolean get() = started && (exitCode == null || exitCode == 0)
}

/** In-process bus that delivers results from [TermuxResultReceiver] to the view model. */
object TermuxResultHub {
    private val _results = MutableSharedFlow<TermuxResult>(extraBufferCapacity = 32)
    val results: SharedFlow<TermuxResult> = _results.asSharedFlow()

    private val nextId = AtomicInteger((System.currentTimeMillis() % 100_000).toInt())

    fun newExecutionId(): Int = nextId.incrementAndGet()

    fun publish(result: TermuxResult) {
        _results.tryEmit(result)
    }
}

/** Receives the PendingIntent Termux fires once a background command finished. */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
        if (executionId < 0) return
        val bundle = intent.getBundleExtra(TermuxConstants.RESULT_BUNDLE)
        val result = if (bundle == null) {
            TermuxResult(executionId, "", "", null, 0, "Termux returned no result")
        } else {
            TermuxResult(
                executionId = executionId,
                stdout = bundle.getString(TermuxConstants.RESULT_STDOUT).orEmpty(),
                stderr = bundle.getString(TermuxConstants.RESULT_STDERR).orEmpty(),
                exitCode = if (bundle.containsKey(TermuxConstants.RESULT_EXIT_CODE)) {
                    bundle.getInt(TermuxConstants.RESULT_EXIT_CODE)
                } else null,
                errCode = bundle.getInt(TermuxConstants.RESULT_ERR, TermuxConstants.ERR_SUCCESS),
                errMessage = bundle.getString(TermuxConstants.RESULT_ERRMSG),
            )
        }
        TermuxResultHub.publish(result)
    }

    companion object {
        const val EXTRA_EXECUTION_ID = "com.example.EXTRA_EXECUTION_ID"
    }
}

sealed class TermuxLaunch {
    object Sent : TermuxLaunch()
    object NotInstalled : TermuxLaunch()
    object PermissionMissing : TermuxLaunch()
    data class Failed(val message: String) : TermuxLaunch()
}

object TermuxBridge {

    fun isInstalled(context: Context): Boolean = try {
        context.packageManager.getPackageInfo(TermuxConstants.PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    fun hasRunCommandPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, TermuxConstants.PERMISSION_RUN_COMMAND) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Sends [command] to Termux. For background commands with an [executionId] the result is
     * delivered asynchronously through [TermuxResultHub].
     */
    fun execute(context: Context, command: TermuxCommand, executionId: Int? = null): TermuxLaunch {
        if (!isInstalled(context)) return TermuxLaunch.NotInstalled
        if (!hasRunCommandPermission(context)) return TermuxLaunch.PermissionMissing

        val intent = Intent(TermuxConstants.ACTION_RUN_COMMAND).apply {
            setClassName(TermuxConstants.PACKAGE, TermuxConstants.RUN_COMMAND_SERVICE)
            putExtra(TermuxConstants.EXTRA_PATH, command.executable)
            putExtra(TermuxConstants.EXTRA_ARGUMENTS, command.arguments.toTypedArray())
            putExtra(TermuxConstants.EXTRA_WORKDIR, TermuxConstants.HOME)
            putExtra(TermuxConstants.EXTRA_BACKGROUND, command.background)
            // 0 = open the new session and bring Termux to the foreground.
            putExtra(TermuxConstants.EXTRA_SESSION_ACTION, "0")
            putExtra(TermuxConstants.EXTRA_COMMAND_LABEL, command.label)
            if (executionId != null) {
                putExtra(TermuxConstants.EXTRA_PENDING_INTENT, resultPendingIntent(context, executionId))
            }
        }

        return try {
            ContextCompat.startForegroundService(context, intent)
            TermuxLaunch.Sent
        } catch (e: SecurityException) {
            TermuxLaunch.PermissionMissing
        } catch (e: Exception) {
            TermuxLaunch.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    private fun resultPendingIntent(context: Context, executionId: Int): PendingIntent {
        val resultIntent = Intent(context, TermuxResultReceiver::class.java)
            .putExtra(TermuxResultReceiver.EXTRA_EXECUTION_ID, executionId)
        // Termux fills in the result extras, so the PendingIntent must be mutable. It is explicit
        // (targets our non-exported receiver), which keeps this safe on Android 12+.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        return PendingIntent.getBroadcast(context, executionId, resultIntent, flags)
    }
}
