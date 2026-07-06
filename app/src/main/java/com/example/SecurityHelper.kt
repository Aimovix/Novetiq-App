package com.example

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

enum class LlmProviderType {
    OPENAI, GEMINI, CLAUDE
}

class SecurityHelper(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = try {
        EncryptedSharedPreferences.create(
            context,
            "synaptiq_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        context.getSharedPreferences("synaptiq_secure_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        EncryptedSharedPreferences.create(
            context,
            "synaptiq_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveApiKey(provider: LlmProviderType, apiKey: String) {
        sharedPreferences.edit().putString("API_KEY_${provider.name}", apiKey).apply()
    }

    fun getApiKey(provider: LlmProviderType): String? {
        return sharedPreferences.getString("API_KEY_${provider.name}", null)
    }

    fun removeApiKey(provider: LlmProviderType) {
        sharedPreferences.edit().remove("API_KEY_${provider.name}").apply()
    }
    
    // Legacy support for string based provider
    fun saveApiKey(provider: String, apiKey: String) {
        sharedPreferences.edit().putString("API_KEY_$provider", apiKey).apply()
    }

    fun getApiKey(provider: String): String? {
        return sharedPreferences.getString("API_KEY_$provider", null)
    }

    fun setLocalModelConfigured(configured: Boolean) {
        sharedPreferences.edit().putBoolean("LOCAL_MODEL_CONFIGURED", configured).apply()
    }

    fun isLocalModelConfigured(): Boolean {
        return sharedPreferences.getBoolean("LOCAL_MODEL_CONFIGURED", false)
    }

    fun setLanguage(language: String) {
        sharedPreferences.edit().putString("APP_LANGUAGE", language).apply()
    }

    fun getLanguage(): String {
        return sharedPreferences.getString("APP_LANGUAGE", "English") ?: "English"
    }

    fun setSecurityLevel(level: Int) {
        val validLevel = if (level in 1..3) level else 2
        sharedPreferences.edit().putInt("SECURITY_LEVEL", validLevel).apply()
    }

    fun getSecurityLevel(): Int {
        // Defaults to Level 2 (Supervised)
        return sharedPreferences.getInt("SECURITY_LEVEL", 2)
    }
}
