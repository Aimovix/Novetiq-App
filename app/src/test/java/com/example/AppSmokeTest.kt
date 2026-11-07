package com.example

import android.content.pm.PackageInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Renders the real app under Robolectric. The Android Keystore is not available there, which
 * also exercises the crash-free fallback of [SecurityHelper].
 */
@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "en")
class AppSmokeTest {

    @get:Rule val compose = createComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<android.app.Application>()

    @Before fun resetSettings() {
        SecurityHelper(app).apply {
            setSetupComplete(false)
            setLanguage(Strings.ENGLISH)
            clearChatHistory()
            LlmProviderType.entries.forEach(::removeApiKey)
        }
    }

    private fun installTermux() {
        shadowOf(app.packageManager).installPackage(PackageInfo().apply { packageName = TermuxConstants.PACKAGE })
    }

    @Test fun showsInstallHintWhenTermuxIsMissing() {
        compose.setContent { SynaptiqApp(SecurityHelper(app)) }
        compose.onNodeWithText("Termux is required").assertIsDisplayed()
        compose.onNodeWithText("Open via F-Droid").assertIsDisplayed()
    }

    @Test fun setupWizardBlocksUntilAModelIsConfigured() {
        installTermux()
        compose.setContent { SynaptiqApp(SecurityHelper(app)) }
        compose.onNodeWithText("Setup Wizard").assertIsDisplayed()
        compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Step 1 of 3").assertIsDisplayed()
    }

    @Test fun setupWizardAdvancesWithConfiguredModel() {
        installTermux()
        val settings = SecurityHelper(app)
        settings.saveApiKey(LlmProviderType.CLAUDE, "test-key")
        compose.setContent { SynaptiqApp(settings) }
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Step 2 of 3").assertIsDisplayed()
        compose.onNodeWithText("Level 2: Supervised").assertIsDisplayed()
        compose.onNodeWithText("Continue").performClick()
        compose.onNodeWithText("Step 3 of 3").assertIsDisplayed()
        compose.onNodeWithText("Finish Setup").performClick()
        compose.onNodeWithText("Hello! I am your autonomous AI agent. How can I help you today?").assertIsDisplayed()
    }

    @Test fun chatShowsActiveModelAndOpensSettings() {
        installTermux()
        val settings = SecurityHelper(app)
        settings.saveApiKey(LlmProviderType.CLAUDE, "test-key")
        settings.setSetupComplete(true)
        compose.setContent { SynaptiqApp(settings) }
        compose.onNodeWithText("Anthropic Claude · claude-opus-5-5").assertIsDisplayed()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Language").assertIsDisplayed()
        compose.onNodeWithText("Deutsch").performClick()
        compose.onNodeWithText("Sprache").assertIsDisplayed()
    }
}
