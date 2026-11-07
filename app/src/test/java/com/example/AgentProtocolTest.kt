package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProtocolTest {

    @Test fun parsesCommandBlockAndStripsItFromText() {
        val reply = "Let me check your files.\n\n```termux\nls -la\n```"
        val parsed = AgentProtocol.parse(reply)
        assertEquals("ls -la", parsed.command)
        assertEquals("Let me check your files.", parsed.text)
    }

    @Test fun ignoresOrdinaryCodeBlocks() {
        val parsed = AgentProtocol.parse("Example:\n```bash\nls\n```")
        assertNull(parsed.command)
        assertTrue(parsed.text.contains("```bash"))
    }

    @Test fun usesFirstNonCommentLineOnly() {
        val parsed = AgentProtocol.parse("```termux\n# list files\nls\nrm x\n```")
        assertEquals("ls", parsed.command)
    }

    @Test fun handlesWindowsLineEndings() {
        assertEquals("pwd", AgentProtocol.parse("```termux\r\npwd\r\n```").command)
    }

    @Test fun systemPromptReflectsSecurityLevelAndLanguage() {
        assertTrue(AgentProtocol.systemPrompt(3, "English").contains("must NOT propose"))
        assertTrue(AgentProtocol.systemPrompt(2, "Deutsch").contains("Antworte auf Deutsch"))
        assertTrue(AgentProtocol.systemPrompt(1, "English").contains("ls"))
    }

    @Test fun formatsResultsAndTruncatesLongOutput() {
        val long = "x".repeat(AgentProtocol.MAX_OUTPUT_CHARS + 50)
        val text = AgentProtocol.formatResult("cat big", TermuxResult(1, long, "", 0, -1, null))
        assertTrue(text.startsWith("[Termux] `cat big`"))
        assertTrue(text.contains("exit code: 0"))
        assertTrue(text.contains("50 more characters truncated"))

        val failed = AgentProtocol.formatResult("ls", TermuxResult(2, "", "", null, 2, "not allowed"))
        assertTrue(failed.contains("Could not start: not allowed"))
    }

    @Test fun prepareHistoryMergesSameRoleTurns() {
        val history = listOf(
            LlmMessage(ChatRole.USER, "a"),
            LlmMessage(ChatRole.USER, "b"),
            LlmMessage(ChatRole.ASSISTANT, "c"),
        )
        val prepared = AgentProtocol.prepareHistory(history)
        assertEquals(2, prepared.size)
        assertEquals("a\n\nb", prepared[0].content)
    }

    @Test fun prepareHistoryTrimsAndStartsWithUser() {
        val history = (0 until 101).map {
            LlmMessage(if (it % 2 == 0) ChatRole.USER else ChatRole.ASSISTANT, "m$it")
        }
        val prepared = AgentProtocol.prepareHistory(history)
        assertTrue(prepared.size <= AgentProtocol.MAX_HISTORY_MESSAGES)
        assertEquals(ChatRole.USER, prepared.first().role)
        assertEquals("m100", prepared.last().content)
    }
}
