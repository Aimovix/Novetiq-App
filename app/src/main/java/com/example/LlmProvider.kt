package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

enum class ChatRole { USER, ASSISTANT }

data class LlmMessage(val role: ChatRole, val content: String)

class LlmException(val kind: Kind, message: String) : Exception(message) {
    enum class Kind { NOT_CONFIGURED, AUTH, RATE_LIMIT, NETWORK, TIMEOUT, SERVER, BAD_REQUEST, REFUSED, EMPTY }
}

/** A chat completion back-end. Implementations are stateless; history is passed every call. */
interface LlmClient {
    val provider: LlmProviderType
    val model: String

    suspend fun complete(system: String, messages: List<LlmMessage>): String

    /** Model ids the account/server can use. Also serves as a cheap credential check. */
    suspend fun listModels(): List<String>
}

data class LocalModelInfo(
    val id: String,
    val name: String,
    /** Model tag used by Ollama (`ollama pull <tag>`). */
    val ollamaTag: String,
    val ramRequirementGb: Int,
    val parameters: String,
    val badgeKey: String,
)

object LocalModels {
    val AVAILABLE_MODELS = listOf(
        LocalModelInfo("gemma_2_2b", "Gemma 2 (2B)", "gemma2:2b", 4, "2B", "recommended"),
        LocalModelInfo("qwen_2_5_1_5b", "Qwen 2.5 (1.5B)", "qwen2.5:1.5b", 4, "1.5B", "fast_inference"),
        LocalModelInfo("phi_3_mini", "Phi-3 Mini (3.8B)", "phi3:mini", 6, "3.8B", "efficient"),
        LocalModelInfo("llama_3_1_8b", "Llama 3.1 (8B)", "llama3.1:8b", 10, "8B", "high_end"),
    )

    val DEFAULT = AVAILABLE_MODELS.first()

    fun byTag(tag: String): LocalModelInfo? = AVAILABLE_MODELS.firstOrNull { it.ollamaTag == tag }
}

/** Suggested models per provider. Users can always type any other model id. */
object ModelCatalog {
    private val SUGGESTIONS = mapOf(
        LlmProviderType.GEMINI to listOf(
            "gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3.1-pro-preview", "gemini-2.5-flash",
        ),
        LlmProviderType.OPENAI to listOf("gpt-5.5", "chat-latest", "gpt-5.6-sol", "gpt-5.6-luna"),
        LlmProviderType.CLAUDE to listOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-haiku-5-5"),
        LlmProviderType.LOCAL to LocalModels.AVAILABLE_MODELS.map { it.ollamaTag },
    )

    fun suggestions(provider: LlmProviderType): List<String> = SUGGESTIONS.getValue(provider)

    fun defaultModel(provider: LlmProviderType): String = suggestions(provider).first()

    fun displayName(provider: LlmProviderType): String = when (provider) {
        LlmProviderType.GEMINI -> "Google Gemini"
        LlmProviderType.OPENAI -> "OpenAI"
        LlmProviderType.CLAUDE -> "Anthropic Claude"
        LlmProviderType.LOCAL -> "Ollama (Termux)"
    }
}

object LlmClientFactory {
    fun create(settings: SecurityHelper, provider: LlmProviderType): LlmClient {
        val model = settings.getSelectedModel(provider)
        if (provider == LlmProviderType.LOCAL) return OllamaClient(model)
        val key = settings.getApiKey(provider)
            ?: throw LlmException(LlmException.Kind.NOT_CONFIGURED, "No API key for $provider")
        return when (provider) {
            LlmProviderType.GEMINI -> GeminiClient(key, model)
            LlmProviderType.OPENAI -> OpenAiClient(key, model)
            LlmProviderType.CLAUDE -> ClaudeClient(key, model)
            LlmProviderType.LOCAL -> OllamaClient(model)
        }
    }
}

// --- HTTP ---------------------------------------------------------------------------------

internal object Http {
    data class Response(val code: Int, val body: String)

    suspend fun request(
        url: String,
        method: String = "GET",
        headers: Map<String, String> = emptyMap(),
        body: String? = null,
        readTimeoutMs: Int = 180_000,
    ): Response = withContext(Dispatchers.IO) {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, e.message ?: "Network error")
        }
        try {
            connection.requestMethod = method
            connection.connectTimeout = 15_000
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            Response(code, text)
        } catch (e: SocketTimeoutException) {
            throw LlmException(LlmException.Kind.TIMEOUT, e.message ?: "Timeout")
        } catch (e: IOException) {
            throw LlmException(LlmException.Kind.NETWORK, e.message ?: "Network error")
        } finally {
            connection.disconnect()
        }
    }

    /** Converts a non-2xx response into a typed [LlmException]. */
    fun ensureSuccess(response: Response): JSONObject {
        if (response.code in 200..299) {
            return try {
                JSONObject(response.body)
            } catch (e: JSONException) {
                throw LlmException(LlmException.Kind.SERVER, "Invalid JSON response")
            }
        }
        val message = LlmJson.extractErrorMessage(response.body) ?: "HTTP ${response.code}"
        val kind = when (response.code) {
            401, 403 -> LlmException.Kind.AUTH
            429 -> LlmException.Kind.RATE_LIMIT
            400, 404, 413, 422 -> LlmException.Kind.BAD_REQUEST
            else -> LlmException.Kind.SERVER
        }
        throw LlmException(kind, message)
    }
}

// --- Request builders & response parsers (pure, unit tested) ---------------------------------

object LlmJson {
    const val ANTHROPIC_VERSION = "2026-10-21"
    const val ANTHROPIC_FALLBACK_BETA = "server-side-fallback-2026-07-01"
    private const val MAX_OUTPUT_TOKENS = 16_000

    fun extractErrorMessage(body: String): String? = try {
        val json = JSONObject(body)
        when (val error = json.opt("error")) {
            is JSONObject -> error.optString("message").takeIf { it.isNotBlank() }
            is String -> error.takeIf { it.isNotBlank() }
            else -> json.optString("message").takeIf { it.isNotBlank() }
        }
    } catch (e: JSONException) {
        body.take(200).takeIf { it.isNotBlank() }
    }

    // Gemini ------------------------------------------------------------------------------

    fun geminiRequest(system: String, messages: List<LlmMessage>): JSONObject = JSONObject().apply {
        put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", system))))
        put("contents", JSONArray().apply {
            messages.forEach { m ->
                put(JSONObject().apply {
                    put("role", if (m.role == ChatRole.USER) "user" else "model")
                    put("parts", JSONArray().put(JSONObject().put("text", m.content)))
                })
            }
        })
    }

    fun parseGemini(json: JSONObject): String {
        val candidates = json.optJSONArray("candidates")
        if (candidates == null || candidates.length() == 0) {
            val blockReason = json.optJSONObject("promptFeedback")?.optString("blockReason")
            if (!blockReason.isNullOrBlank()) throw LlmException(LlmException.Kind.REFUSED, blockReason)
            throw LlmException(LlmException.Kind.EMPTY, "No candidates")
        }
        val candidate = candidates.getJSONObject(0)
        val parts = candidate.optJSONObject("content")?.optJSONArray("parts")
        val text = buildString {
            if (parts != null) {
                for (i in 0 until parts.length()) {
                    val part = parts.getJSONObject(i)
                    if (part.optBoolean("thought", false)) continue
                    append(part.optString("text"))
                }
            }
        }
        if (text.isBlank()) {
            val reason = candidate.optString("finishReason")
            if (reason == "SAFETY" || reason == "PROHIBITED_CONTENT" || reason == "BLOCKLIST") {
                throw LlmException(LlmException.Kind.REFUSED, reason)
            }
            throw LlmException(LlmException.Kind.EMPTY, reason.ifBlank { "Empty response" })
        }
        return text.trim()
    }

    fun parseGeminiModels(json: JSONObject): List<String> {
        val models = json.optJSONArray("models") ?: return emptyList()
        return (0 until models.length()).mapNotNull { i ->
            val m = models.getJSONObject(i)
            val methods = m.optJSONArray("supportedGenerationMethods")
            val canGenerate = methods == null ||
                (0 until methods.length()).any { methods.optString(it) == "generateContent" }
            m.optString("name").removePrefix("models/").takeIf { canGenerate && it.isNotBlank() }
        }
    }

    // OpenAI ------------------------------------------------------------------------------

    fun openAiRequest(model: String, system: String, messages: List<LlmMessage>): JSONObject =
        JSONObject().apply {
            put("model", model)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                messages.forEach { m ->
                    put(JSONObject()
                        .put("role", if (m.role == ChatRole.USER) "user" else "assistant")
                        .put("content", m.content))
                }
            })
        }

    fun parseOpenAi(json: JSONObject): String {
        val choices = json.optJSONArray("choices")
        if (choices == null || choices.length() == 0) throw LlmException(LlmException.Kind.EMPTY, "No choices")
        val message = choices.getJSONObject(0).optJSONObject("message")
        val refusal = message?.optString("refusal")
        if (!refusal.isNullOrBlank() && refusal != "null") throw LlmException(LlmException.Kind.REFUSED, refusal)
        val content = message?.optString("content").orEmpty()
        if (content.isBlank() || content == "null") throw LlmException(LlmException.Kind.EMPTY, "Empty response")
        return content.trim()
    }

    fun parseOpenAiModels(json: JSONObject): List<String> {
        val data = json.optJSONArray("data") ?: return emptyList()
        val chatPrefixes = listOf("gpt-", "o1", "o3", "o4", "chat-latest")
        val excluded = listOf("audio", "realtime", "transcribe", "tts", "image", "embedding", "search")
        return (0 until data.length()).map { data.getJSONObject(it).optString("id") }
            .filter { id -> chatPrefixes.any { id.startsWith(it) } && excluded.none { id.contains(it) } }
    }

    // Anthropic Claude --------------------------------------------------------------------

    /** Current-generation models that accept `output_config.effort`. */
    fun claudeSupportsEffort(model: String): Boolean =
        listOf("claude-opus-5", "claude-sonnet-5", "claude-haiku-5", "claude-fable-5", "claude-opus-4-6",
            "claude-opus-4-7", "claude-opus-4-8", "claude-sonnet-4-6").any { model.startsWith(it) }

    /** Models that support the server-side refusal fallback in its `"default"` form. */
    fun claudeSupportsFallback(model: String): Boolean =
        model in setOf("claude-opus-5-5", "claude-sonnet-5-5", "claude-opus-5", "claude-fable-5-1")

    fun claudeRequest(model: String, system: String, messages: List<LlmMessage>): JSONObject =
        JSONObject().apply {
            put("model", model)
            put("max_tokens", MAX_OUTPUT_TOKENS)
            put("system", system)
            put("messages", JSONArray().apply {
                messages.forEach { m ->
                    put(JSONObject()
                        .put("role", if (m.role == ChatRole.USER) "user" else "assistant")
                        .put("content", m.content))
                }
            })
            if (claudeSupportsEffort(model)) put("output_config", JSONObject().put("effort", "medium"))
            if (claudeSupportsFallback(model)) put("fallbacks", "default")
        }

    fun claudeHeaders(apiKey: String, model: String): Map<String, String> = buildMap {
        put("x-api-key", apiKey)
        put("anthropic-version", ANTHROPIC_VERSION)
        if (claudeSupportsFallback(model)) put("anthropic-beta", ANTHROPIC_FALLBACK_BETA)
    }

    fun parseClaude(json: JSONObject): String {
        val stopReason = json.optString("stop_reason")
        if (stopReason == "refusal") {
            val explanation = json.optJSONObject("stop_details")?.optString("explanation")
            throw LlmException(LlmException.Kind.REFUSED, explanation?.takeIf { it.isNotBlank() } ?: "refusal")
        }
        val content = json.optJSONArray("content") ?: throw LlmException(LlmException.Kind.EMPTY, "No content")
        val text = buildString {
            for (i in 0 until content.length()) {
                val block = content.getJSONObject(i)
                if (block.optString("type") == "text") append(block.optString("text"))
            }
        }
        if (text.isBlank()) throw LlmException(LlmException.Kind.EMPTY, stopReason.ifBlank { "Empty response" })
        return text.trim()
    }

    fun parseClaudeModels(json: JSONObject): List<String> {
        val data = json.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).map { data.getJSONObject(it).optString("id") }.filter { it.isNotBlank() }
    }

    // Ollama ------------------------------------------------------------------------------

    fun ollamaRequest(model: String, system: String, messages: List<LlmMessage>): JSONObject =
        JSONObject().apply {
            put("model", model)
            put("stream", false)
            put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", system))
                messages.forEach { m ->
                    put(JSONObject()
                        .put("role", if (m.role == ChatRole.USER) "user" else "assistant")
                        .put("content", m.content))
                }
            })
        }

    fun parseOllama(json: JSONObject): String {
        val text = json.optJSONObject("message")?.optString("content").orEmpty()
        if (text.isBlank()) throw LlmException(LlmException.Kind.EMPTY, "Empty response")
        return text.trim()
    }

    fun parseOllamaModels(json: JSONObject): List<String> {
        val models = json.optJSONArray("models") ?: return emptyList()
        return (0 until models.length()).map { models.getJSONObject(it).optString("name") }.filter { it.isNotBlank() }
    }
}

// --- Clients ------------------------------------------------------------------------------

class GeminiClient(private val apiKey: String, override val model: String) : LlmClient {
    override val provider = LlmProviderType.GEMINI
    private val base = "https://generativelanguage.googleapis.com/v1beta"

    override suspend fun complete(system: String, messages: List<LlmMessage>): String {
        val modelPath = URLEncoder.encode(model, "UTF-8")
        val response = Http.request(
            "$base/models/$modelPath:generateContent",
            method = "POST",
            headers = mapOf("x-goog-api-key" to apiKey),
            body = LlmJson.geminiRequest(system, messages).toString(),
        )
        return LlmJson.parseGemini(Http.ensureSuccess(response))
    }

    override suspend fun listModels(): List<String> {
        val response = Http.request("$base/models?pageSize=200", headers = mapOf("x-goog-api-key" to apiKey))
        return LlmJson.parseGeminiModels(Http.ensureSuccess(response))
    }
}

class OpenAiClient(private val apiKey: String, override val model: String) : LlmClient {
    override val provider = LlmProviderType.OPENAI
    private val base = "https://api.openai.com/v1"

    override suspend fun complete(system: String, messages: List<LlmMessage>): String {
        val response = Http.request(
            "$base/chat/completions",
            method = "POST",
            headers = mapOf("Authorization" to "Bearer $apiKey"),
            body = LlmJson.openAiRequest(model, system, messages).toString(),
        )
        return LlmJson.parseOpenAi(Http.ensureSuccess(response))
    }

    override suspend fun listModels(): List<String> {
        val response = Http.request("$base/models", headers = mapOf("Authorization" to "Bearer $apiKey"))
        return LlmJson.parseOpenAiModels(Http.ensureSuccess(response))
    }
}

class ClaudeClient(private val apiKey: String, override val model: String) : LlmClient {
    override val provider = LlmProviderType.CLAUDE
    private val base = "https://api.anthropic.com/v1"

    override suspend fun complete(system: String, messages: List<LlmMessage>): String {
        val response = Http.request(
            "$base/messages",
            method = "POST",
            headers = LlmJson.claudeHeaders(apiKey, model),
            body = LlmJson.claudeRequest(model, system, messages).toString(),
        )
        return LlmJson.parseClaude(Http.ensureSuccess(response))
    }

    override suspend fun listModels(): List<String> {
        val response = Http.request(
            "$base/models?limit=100",
            headers = mapOf("x-api-key" to apiKey, "anthropic-version" to LlmJson.ANTHROPIC_VERSION),
        )
        return LlmJson.parseClaudeModels(Http.ensureSuccess(response))
    }
}

/** Talks to an Ollama server running inside Termux on the same device. */
class OllamaClient(override val model: String) : LlmClient {
    override val provider = LlmProviderType.LOCAL

    override suspend fun complete(system: String, messages: List<LlmMessage>): String {
        val response = Http.request(
            "$BASE_URL/api/chat",
            method = "POST",
            body = LlmJson.ollamaRequest(model, system, messages).toString(),
            readTimeoutMs = 600_000, // CPU inference on a phone can be slow
        )
        return LlmJson.parseOllama(Http.ensureSuccess(response))
    }

    override suspend fun listModels(): List<String> {
        val response = Http.request("$BASE_URL/api/tags", readTimeoutMs = 10_000)
        return LlmJson.parseOllamaModels(Http.ensureSuccess(response))
    }

    companion object {
        const val BASE_URL = "http://127.0.0.1:11434"
    }
}
