package com.example

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale

/** All model back-ends supported by the agent. The enum names are persisted - do not rename. */
enum class LlmProviderType {
    OPENAI, GEMINI, CLAUDE, LOCAL;

    val isCloud: Boolean get() = this != LOCAL

    companion object {
        val CLOUD = listOf(GEMINI, OPENAI, CLAUDE)
        fun fromName(name: String?): LlmProviderType? = entries.firstOrNull { it.name == name }
    }
}

/**
 * Encrypted on-device storage for API keys and app settings.
 *
 * Every value lives in [EncryptedSharedPreferences] (AES-256). If the keystore entry got lost
 * (e.g. after a backup restore onto another device) the file can no longer be decrypted; in that
 * case it is wiped and recreated instead of crashing the app on every start.
 */
class SecurityHelper(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences = createPrefs()

    private fun createPrefs(): SharedPreferences {
        return try {
            openEncrypted()
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted prefs unreadable, resetting", e)
            appContext.deleteSharedPreferences(PREFS_NAME)
            try {
                openEncrypted()
            } catch (e2: Exception) {
                // Keystore is broken beyond repair. Run without persistence instead of crashing;
                // nothing sensitive is ever written in plain text.
                Log.e(TAG, "Encrypted prefs unavailable, using in-memory storage", e2)
                InMemoryPreferences.shared
            }
        }
    }

    private fun openEncrypted(): SharedPreferences {
        val masterKey = MasterKey.Builder(appContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            appContext,
            PREFS_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    // --- API keys -------------------------------------------------------------------------

    fun saveApiKey(provider: LlmProviderType, apiKey: String) {
        val trimmed = apiKey.trim()
        prefs.edit {
            if (trimmed.isEmpty()) remove(keyName(provider)) else putString(keyName(provider), trimmed)
        }
    }

    fun getApiKey(provider: LlmProviderType): String? =
        prefs.getString(keyName(provider), null)?.takeIf { it.isNotBlank() }

    fun removeApiKey(provider: LlmProviderType) {
        prefs.edit { remove(keyName(provider)) }
    }

    fun hasAnyCloudKey(): Boolean = LlmProviderType.CLOUD.any { getApiKey(it) != null }

    private fun keyName(provider: LlmProviderType) = "API_KEY_${provider.name}"

    // --- Models ---------------------------------------------------------------------------

    fun setSelectedModel(provider: LlmProviderType, model: String) {
        prefs.edit { putString("MODEL_${provider.name}", model.trim()) }
    }

    fun getSelectedModel(provider: LlmProviderType): String =
        prefs.getString("MODEL_${provider.name}", null)?.takeIf { it.isNotBlank() }
            ?: ModelCatalog.defaultModel(provider)

    fun setActiveProvider(provider: LlmProviderType) {
        prefs.edit { putString("ACTIVE_PROVIDER", provider.name) }
    }

    /** The provider used by the chat. Falls back to the first configured one. */
    fun getActiveProvider(): LlmProviderType? {
        val stored = LlmProviderType.fromName(prefs.getString("ACTIVE_PROVIDER", null))
        if (stored != null && isProviderConfigured(stored)) return stored
        return LlmProviderType.CLOUD.firstOrNull { getApiKey(it) != null }
            ?: LlmProviderType.LOCAL.takeIf { isLocalModelConfigured() }
    }

    fun isProviderConfigured(provider: LlmProviderType): Boolean =
        if (provider == LlmProviderType.LOCAL) isLocalModelConfigured() else getApiKey(provider) != null

    fun configuredProviders(): List<LlmProviderType> =
        LlmProviderType.entries.filter { isProviderConfigured(it) }

    fun setLocalModelConfigured(configured: Boolean) {
        prefs.edit { putBoolean("LOCAL_MODEL_CONFIGURED", configured) }
    }

    fun isLocalModelConfigured(): Boolean = prefs.getBoolean("LOCAL_MODEL_CONFIGURED", false)

    fun hasAnyModelConfigured(): Boolean = hasAnyCloudKey() || isLocalModelConfigured()

    // --- App settings ---------------------------------------------------------------------

    fun setLanguage(language: String) {
        prefs.edit { putString("APP_LANGUAGE", language) }
    }

    /** Defaults to the device language (German devices start in German). */
    fun getLanguage(): String =
        prefs.getString("APP_LANGUAGE", null) ?: Strings.defaultLanguageFor(Locale.getDefault())

    /**
     * 0 = full autonomy, 1 = autonomous, 2 = supervised, 3 = sandbox. Level 0 can only be stored
     * after the user gave the separate full-autonomy consent.
     */
    fun setSecurityLevel(level: Int) {
        val validLevel = when {
            level == 0 && !hasFullAutonomyConsent() -> return
            level in 0..3 -> level
            else -> DEFAULT_SECURITY_LEVEL
        }
        prefs.edit { putInt("SECURITY_LEVEL", validLevel) }
    }

    fun getSecurityLevel(): Int {
        val level = prefs.getInt("SECURITY_LEVEL", DEFAULT_SECURITY_LEVEL)
        return when {
            level == 0 && !hasFullAutonomyConsent() -> DEFAULT_SECURITY_LEVEL
            level in 0..3 -> level
            else -> DEFAULT_SECURITY_LEVEL
        }
    }

    /** Maximum number of tool calls the agent may chain for one user message. */
    fun setMaxSteps(steps: Int) {
        prefs.edit { putInt("MAX_STEPS", steps.coerceIn(1, MAX_STEPS_LIMIT)) }
    }

    fun getMaxSteps(): Int = prefs.getInt("MAX_STEPS", AgentProtocol.DEFAULT_MAX_STEPS).coerceIn(1, MAX_STEPS_LIMIT)

    // --- Legal consent --------------------------------------------------------------------

    /** Stores that the user accepted the current terms, disclaimer and privacy notice. */
    fun acceptLegal(version: Int = LegalTexts.VERSION, timestamp: Long = System.currentTimeMillis()) {
        prefs.edit {
            putInt("LEGAL_VERSION", version)
            putLong("LEGAL_ACCEPTED_AT", timestamp)
        }
        appendAudit("CONSENT", "terms v$version accepted")
    }

    fun hasAcceptedCurrentLegal(): Boolean = prefs.getInt("LEGAL_VERSION", 0) >= LegalTexts.VERSION

    fun legalAcceptedAt(): Long? = prefs.getLong("LEGAL_ACCEPTED_AT", 0L).takeIf { it > 0 }

    /** Withdraws every consent. The app returns to the consent screen and full autonomy is off. */
    fun withdrawLegal() {
        prefs.edit {
            remove("LEGAL_VERSION")
            remove("LEGAL_ACCEPTED_AT")
            remove("FULL_AUTONOMY_CONSENT_AT")
            if (prefs.getInt("SECURITY_LEVEL", DEFAULT_SECURITY_LEVEL) == 0) putInt("SECURITY_LEVEL", DEFAULT_SECURITY_LEVEL)
        }
        appendAudit("CONSENT", "all consents withdrawn")
    }

    fun recordFullAutonomyConsent(timestamp: Long = System.currentTimeMillis()) {
        prefs.edit { putLong("FULL_AUTONOMY_CONSENT_AT", timestamp) }
        appendAudit("CONSENT", "full autonomy risks accepted")
    }

    fun hasFullAutonomyConsent(): Boolean = prefs.getLong("FULL_AUTONOMY_CONSENT_AT", 0L) > 0

    fun revokeFullAutonomyConsent() {
        prefs.edit {
            remove("FULL_AUTONOMY_CONSENT_AT")
            if (prefs.getInt("SECURITY_LEVEL", DEFAULT_SECURITY_LEVEL) == 0) putInt("SECURITY_LEVEL", 1)
        }
        appendAudit("CONSENT", "full autonomy consent revoked")
    }

    // --- Audit log ------------------------------------------------------------------------

    /** Appends one line to the encrypted, on-device log of everything the agent did. */
    fun appendAudit(outcome: String, detail: String, timestamp: Long = System.currentTimeMillis()) {
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(java.util.Date(timestamp))
        val line = "$time | L${prefs.getInt("SECURITY_LEVEL", DEFAULT_SECURITY_LEVEL)} | $outcome | " +
            detail.replace('\n', ' ').take(MAX_AUDIT_LINE)
        val lines = (getAuditLog() + line).takeLast(MAX_AUDIT_ENTRIES)
        prefs.edit { putString("AUDIT_LOG", org.json.JSONArray(lines).toString()) }
    }

    fun getAuditLog(): List<String> = try {
        val array = org.json.JSONArray(prefs.getString("AUDIT_LOG", null) ?: "[]")
        (0 until array.length()).map { array.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    fun clearAuditLog() {
        prefs.edit { remove("AUDIT_LOG") }
    }

    /** Deletes every setting, key, chat and log of this app from the device. */
    fun wipeAllData() {
        prefs.edit { clear() }
    }

    fun setSetupComplete(complete: Boolean) {
        prefs.edit { putBoolean("SETUP_COMPLETE", complete) }
    }

    fun isSetupComplete(): Boolean = prefs.getBoolean("SETUP_COMPLETE", false)

    // --- Chat history ---------------------------------------------------------------------

    fun saveChatHistory(json: String) {
        prefs.edit { putString("CHAT_HISTORY", json) }
    }

    fun getChatHistory(): String? = prefs.getString("CHAT_HISTORY", null)

    fun clearChatHistory() {
        prefs.edit { remove("CHAT_HISTORY") }
    }

    companion object {
        private const val TAG = "SecurityHelper"
        private const val PREFS_NAME = "synaptiq_secure_prefs"
        const val DEFAULT_SECURITY_LEVEL = 2
        const val MAX_STEPS_LIMIT = 100
        private const val MAX_AUDIT_ENTRIES = 300
        private const val MAX_AUDIT_LINE = 400
    }
}

/** Minimal non-persistent [SharedPreferences] used only when the keystore is unusable. */
private class InMemoryPreferences private constructor() : SharedPreferences {
    private val values = java.util.concurrent.ConcurrentHashMap<String, Any?>()

    companion object {
        /** Shared per process so that every [SecurityHelper] instance sees the same values. */
        val shared by lazy { InMemoryPreferences() }
    }

    override fun getAll(): MutableMap<String, *> = values.toMutableMap()
    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    @Suppress("UNCHECKED_CAST")
    override fun getStringSet(key: String, defValues: MutableSet<String>?) =
        values[key] as? MutableSet<String> ?: defValues
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun contains(key: String) = values.containsKey(key)
    override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val pending = mutableMapOf<String, Any?>()
        private val removed = mutableSetOf<String>()
        private var clear = false
        override fun putString(key: String, value: String?) = apply { pending[key] = value }
        override fun putStringSet(key: String, values: MutableSet<String>?) = apply { pending[key] = values }
        override fun putInt(key: String, value: Int) = apply { pending[key] = value }
        override fun putLong(key: String, value: Long) = apply { pending[key] = value }
        override fun putFloat(key: String, value: Float) = apply { pending[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { pending[key] = value }
        override fun remove(key: String) = apply { removed += key }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            apply()
            return true
        }
        override fun apply() {
            if (clear) values.clear()
            removed.forEach { values.remove(it) }
            pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
        }
    }
}
