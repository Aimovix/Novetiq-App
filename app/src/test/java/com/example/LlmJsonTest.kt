package com.example

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class LlmJsonTest {

    private val history = listOf(LlmMessage(ChatRole.USER, "Hi"), LlmMessage(ChatRole.ASSISTANT, "Hello"))

    private fun expectKind(kind: LlmException.Kind, block: () -> Unit) {
        try {
            block()
            fail("Expected LlmException($kind)")
        } catch (e: LlmException) {
            assertEquals(kind, e.kind)
        }
    }

    @Test fun geminiRequestUsesModelRoleAndSystemInstruction() {
        val json = LlmJson.geminiRequest("sys", history)
        assertEquals("sys", json.getJSONObject("systemInstruction").getJSONArray("parts").getJSONObject(0).getString("text"))
        val contents = json.getJSONArray("contents")
        assertEquals("user", contents.getJSONObject(0).getString("role"))
        assertEquals("model", contents.getJSONObject(1).getString("role"))
    }

    @Test fun geminiResponseSkipsThoughtParts() {
        val response = JSONObject(
            """{"candidates":[{"content":{"parts":[{"text":"hidden","thought":true},{"text":"Answer"}]},"finishReason":"STOP"}]}""",
        )
        assertEquals("Answer", LlmJson.parseGemini(response))
    }

    @Test fun geminiBlockedPromptIsRefusal() {
        expectKind(LlmException.Kind.REFUSED) {
            LlmJson.parseGemini(JSONObject("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        }
    }

    @Test fun geminiModelListFiltersNonGenerativeModels() {
        val json = JSONObject(
            """{"models":[
                {"name":"models/gemini-3.6-flash","supportedGenerationMethods":["generateContent"]},
                {"name":"models/text-embedding","supportedGenerationMethods":["embedContent"]}]}""",
        )
        assertEquals(listOf("gemini-3.6-flash"), LlmJson.parseGeminiModels(json))
    }

    @Test fun openAiRequestAndResponse() {
        val json = LlmJson.openAiRequest("gpt-5.5", "sys", history)
        assertEquals("gpt-5.5", json.getString("model"))
        assertEquals("system", json.getJSONArray("messages").getJSONObject(0).getString("role"))
        assertFalse(json.has("temperature"))
        val response = JSONObject("""{"choices":[{"message":{"role":"assistant","content":" Hi! "}}]}""")
        assertEquals("Hi!", LlmJson.parseOpenAi(response))
    }

    @Test fun openAiRefusal() {
        expectKind(LlmException.Kind.REFUSED) {
            LlmJson.parseOpenAi(JSONObject("""{"choices":[{"message":{"content":null,"refusal":"No."}}]}"""))
        }
    }

    @Test fun claudeRequestForCurrentModels() {
        val json = LlmJson.claudeRequest("claude-opus-5-5", "sys", history)
        assertEquals("sys", json.getString("system"))
        assertEquals("medium", json.getJSONObject("output_config").getString("effort"))
        assertEquals("default", json.getString("fallbacks"))
        assertFalse(json.has("temperature"))
        assertFalse(json.has("thinking"))
        val headers = LlmJson.claudeHeaders("key", "claude-opus-5-5")
        assertEquals(LlmJson.ANTHROPIC_VERSION, headers["anthropic-version"])
        assertEquals(LlmJson.ANTHROPIC_FALLBACK_BETA, headers["anthropic-beta"])
    }

    @Test fun claudeHaikuHasNoServerSideFallback() {
        val json = LlmJson.claudeRequest("claude-haiku-5-5", "sys", history)
        assertFalse(json.has("fallbacks"))
        assertTrue(json.has("output_config"))
        assertFalse(LlmJson.claudeHeaders("key", "claude-haiku-5-5").containsKey("anthropic-beta"))
    }

    @Test fun claudeResponseConcatenatesTextBlocksOnly() {
        val response = JSONObject(
            """{"stop_reason":"end_turn","content":[{"type":"thinking","thinking":""},{"type":"text","text":"Hello"},{"type":"text","text":" world"}]}""",
        )
        assertEquals("Hello world", LlmJson.parseClaude(response))
    }

    @Test fun claudeRefusalIsDetectedBeforeReadingContent() {
        expectKind(LlmException.Kind.REFUSED) {
            LlmJson.parseClaude(JSONObject("""{"stop_reason":"refusal","content":[],"stop_details":{"type":"refusal","category":null,"explanation":"x"}}"""))
        }
    }

    @Test fun ollamaRoundTrip() {
        val json = LlmJson.ollamaRequest("gemma2:2b", "sys", history)
        assertFalse(json.getBoolean("stream"))
        assertEquals("Hey", LlmJson.parseOllama(JSONObject("""{"message":{"role":"assistant","content":"Hey"}}""")))
        assertEquals(listOf("gemma2:2b"), LlmJson.parseOllamaModels(JSONObject("""{"models":[{"name":"gemma2:2b"}]}""")))
    }

    @Test fun errorMessagesAreExtractedFromAllProviderFormats() {
        assertEquals("bad key", LlmJson.extractErrorMessage("""{"error":{"message":"bad key"}}"""))
        assertEquals("oops", LlmJson.extractErrorMessage("""{"error":"oops"}"""))
        assertEquals("plain text", LlmJson.extractErrorMessage("plain text"))
    }

    @Test fun httpErrorCodesMapToKinds() {
        expectKind(LlmException.Kind.AUTH) { Http.ensureSuccess(Http.Response(401, "{}")) }
        expectKind(LlmException.Kind.RATE_LIMIT) { Http.ensureSuccess(Http.Response(429, "{}")) }
        expectKind(LlmException.Kind.BAD_REQUEST) { Http.ensureSuccess(Http.Response(404, "{}")) }
        expectKind(LlmException.Kind.SERVER) { Http.ensureSuccess(Http.Response(529, "{}")) }
        expectKind(LlmException.Kind.SERVER) { Http.ensureSuccess(Http.Response(200, "not json")) }
    }

    @Test fun everyProviderHasSuggestions() {
        LlmProviderType.entries.forEach { assertTrue(ModelCatalog.suggestions(it).isNotEmpty()) }
        assertEquals("claude-opus-5-5", ModelCatalog.defaultModel(LlmProviderType.CLAUDE))
    }
}
