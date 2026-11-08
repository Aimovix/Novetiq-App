package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Read-only web access for the agent. Only HTTPS GET to public hosts, small responses, text only.
 * Everything returned is marked as untrusted so the model treats it as data, not as instructions.
 */
object WebTool {

    const val MAX_URL_LENGTH = 2_000
    const val MAX_DOWNLOAD_BYTES = 1_000_000
    const val MAX_TEXT_CHARS = 6_000

    data class FetchResult(val url: String, val status: Int?, val contentType: String?, val text: String, val error: String?) {
        val isSuccess: Boolean get() = error == null && status != null && status in 200..299
    }

    /** Returns `null` if the URL may be fetched, otherwise a short reason. */
    fun validateUrl(raw: String): String? {
        val url = raw.trim()
        if (url.isEmpty()) return "empty URL"
        if (url.length > MAX_URL_LENGTH) return "URL too long"
        if (url.any { it.isWhitespace() || it.isISOControl() }) return "URL contains whitespace"
        val uri = try {
            URI(url)
        } catch (e: Exception) {
            return "malformed URL"
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) return "only https:// is allowed"
        if (uri.userInfo != null) return "credentials in URL are not allowed"
        val host = uri.host?.lowercase()?.trim('[', ']') ?: return "missing host"
        if (isPrivateHost(host)) return "local or private addresses are not allowed"
        return null
    }

    internal fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) return true
        if (host == "::1" || host == "::" || host.startsWith("fc") || host.startsWith("fd") || host.startsWith("fe80")) {
            return host.contains(':')
        }
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { p -> p.isNotEmpty() && p.all(Char::isDigit) }) {
            val (a, b) = parts.take(2).map { it.toInt() }
            return a == 0 || a == 10 || a == 127 || (a == 169 && b == 254) || (a == 172 && b in 16..31) ||
                (a == 192 && b == 168) || (a == 100 && b in 64..127) || a >= 224
        }
        return false
    }

    suspend fun fetch(url: String): FetchResult = withContext(Dispatchers.IO) {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: IOException) {
            return@withContext FetchResult(url, null, null, "", e.message ?: "network error")
        }
        try {
            connection.instanceFollowRedirects = true // HttpURLConnection never downgrades https -> http.
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Aimovix-Agent/${BuildConfig.VERSION_NAME} (Android)")
            connection.setRequestProperty("Accept", "text/html,text/plain,application/json,application/xml;q=0.9,*/*;q=0.5")
            val code = connection.responseCode
            // A redirect may have led to a private host.
            val finalUrl = connection.url.toString()
            validateUrl(finalUrl)?.let { return@withContext FetchResult(finalUrl, code, null, "", "redirect blocked: $it") }
            val type = connection.contentType
            if (type != null && !isTextual(type)) {
                return@withContext FetchResult(finalUrl, code, type, "", "not a text document ($type)")
            }
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { input ->
                val buffer = java.io.ByteArrayOutputStream()
                val chunk = ByteArray(16 * 1024)
                while (buffer.size() < MAX_DOWNLOAD_BYTES) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    buffer.write(chunk, 0, minOf(n, MAX_DOWNLOAD_BYTES - buffer.size()))
                }
                buffer.toByteArray()
            } ?: ByteArray(0)
            val raw = String(bytes, Charsets.UTF_8)
            val text = if (type == null || type.contains("html", ignoreCase = true)) htmlToText(raw) else raw
            FetchResult(finalUrl, code, type, AgentProtocol.truncate(text.trim(), MAX_TEXT_CHARS), null)
        } catch (e: IOException) {
            FetchResult(url, null, null, "", e.message ?: e.javaClass.simpleName)
        } finally {
            connection.disconnect()
        }
    }

    private fun isTextual(type: String): Boolean {
        val t = type.lowercase()
        return t.startsWith("text/") || t.contains("json") || t.contains("xml") || t.contains("javascript")
    }

    private val DROP_BLOCKS = Regex("<(script|style|noscript|svg|head|template)[^>]*>.*?</\\1\\s*>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val COMMENTS = Regex("<!--.*?-->", RegexOption.DOT_MATCHES_ALL)
    private val BLOCK_TAGS = Regex("</?(p|div|br|li|ul|ol|h[1-6]|tr|table|section|article|header|footer|pre|blockquote)[^>]*>", RegexOption.IGNORE_CASE)
    private val TAGS = Regex("<[^>]+>")

    /** Very small HTML-to-text conversion: enough for the model to read an article. */
    fun htmlToText(html: String): String {
        var text = COMMENTS.replace(html, " ")
        text = DROP_BLOCKS.replace(text, " ")
        text = BLOCK_TAGS.replace(text, "\n")
        text = TAGS.replace(text, " ")
        text = text.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
            .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")
        return text.lineSequence()
            .map { it.replace(Regex("[ \\t\\x0B\\f\\r]+"), " ").trim() }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }
}
