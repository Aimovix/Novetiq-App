package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val settings = SecurityHelper(this)
        setContent {
            MyApplicationTheme {
                SynaptiqApp(settings = settings)
            }
        }
    }
}

private enum class Screen { SETUP, CHAT, SETTINGS }

@Composable
fun SynaptiqApp(settings: SecurityHelper) {
    val context = LocalContext.current
    // null = not checked yet (avoids flashing the "Termux missing" screen on start).
    var termuxInstalled by remember { mutableStateOf<Boolean?>(null) }
    var language by rememberSaveable { mutableStateOf(settings.getLanguage()) }
    var screen by rememberSaveable {
        mutableStateOf(if (settings.isSetupComplete()) Screen.CHAT else Screen.SETUP)
    }
    var returnTo by rememberSaveable { mutableStateOf(Screen.CHAT) }
    val chatViewModel: ChatViewModel = viewModel()

    // Re-check on every resume so installing Termux and coming back just works.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        termuxInstalled = TermuxBridge.isInstalled(context)
    }

    val openSettings = {
        returnTo = if (screen == Screen.SETTINGS) returnTo else screen
        screen = Screen.SETTINGS
    }

    AppTranslationProvider(language = language) {
        // Surface sets the default content color, so plain Text is readable in light and dark mode.
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            when {
                termuxInstalled == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                termuxInstalled == false && screen != Screen.SETTINGS ->
                    MissingTermuxScreen(onRetry = { termuxInstalled = TermuxBridge.isInstalled(context) })
                screen == Screen.SETTINGS -> SettingsScreen(
                    settings = settings,
                    currentLanguage = language,
                    onLanguageSelected = {
                        language = it
                        settings.setLanguage(it)
                    },
                    onRestartSetup = {
                        settings.setSetupComplete(false)
                        screen = Screen.SETUP
                    },
                    onClearChat = chatViewModel::clear,
                    onBack = {
                        chatViewModel.refreshProvider()
                        screen = if (returnTo == Screen.CHAT && !settings.isSetupComplete()) Screen.SETUP else returnTo
                    },
                )
                screen == Screen.SETUP -> SetupWizard(
                    settings = settings,
                    onOpenSettings = openSettings,
                    onFinished = {
                        chatViewModel.refreshProvider()
                        screen = Screen.CHAT
                    },
                )
                else -> ChatScreen(
                    viewModel = chatViewModel,
                    settings = settings,
                    onOpenSettings = openSettings,
                )
            }
        }
        }
    }
}
