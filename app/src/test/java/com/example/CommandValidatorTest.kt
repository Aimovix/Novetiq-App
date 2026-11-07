package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandValidatorTest {

    private fun allowed(cmd: String) = CommandValidator.validate(cmd) as? ValidationResult.Allowed
        ?: throw AssertionError("Expected '$cmd' to be allowed but was ${CommandValidator.validate(cmd)}")

    private fun rejected(cmd: String, reason: RejectReason) {
        val result = CommandValidator.validate(cmd)
        assertTrue("Expected '$cmd' to be rejected, was $result", result is ValidationResult.Rejected)
        assertEquals(reason, (result as ValidationResult.Rejected).reason)
    }

    @Test fun simpleReadOnlyCommands() {
        assertEquals(CommandRisk.READ_ONLY, allowed("ls -la").risk)
        assertEquals(listOf("-la"), allowed("ls -la").args)
        assertEquals(CommandRisk.READ_ONLY, allowed("pwd").risk)
        assertEquals(CommandRisk.READ_ONLY, allowed("df -h").risk)
    }

    @Test fun wordsContainingDdAreNoLongerBlocked() {
        // The old validator rejected every command containing "dd" (e.g. "address").
        assertTrue(CommandValidator.isValid("echo address added"))
    }

    @Test fun quotesAreTokenizedLikeAShell() {
        assertEquals(listOf("hello world", "it's"), allowed("echo 'hello world' \"it's\"").args)
        assertEquals(listOf("a\"b"), allowed("echo \"a\\\"b\"").args)
        assertEquals(listOf("a b"), allowed("echo a\\ b").args)
    }

    @Test fun shellOperatorsOutsideQuotesAreRejected() {
        rejected("ls; rm -rf ~", RejectReason.SHELL_OPERATOR)
        rejected("ls && pwd", RejectReason.SHELL_OPERATOR)
        rejected("cat a | grep b", RejectReason.SHELL_OPERATOR)
        rejected("echo hi > file", RejectReason.SHELL_OPERATOR)
        rejected("echo \$(whoami)", RejectReason.SHELL_OPERATOR)
        rejected("echo `id`", RejectReason.SHELL_OPERATOR)
        rejected("echo \$HOME", RejectReason.SHELL_OPERATOR)
    }

    @Test fun operatorsInsideQuotesAreLiteralAndAllowed() {
        // Commands are not run through a shell, so quoted operators are harmless text.
        assertEquals(listOf("-E", "a|b", "file.txt"), allowed("grep -E 'a|b' file.txt").args)
    }

    @Test fun controlCharactersAndNewlinesAreRejected() {
        rejected("ls\nrm x", RejectReason.CONTROL_CHARACTER)
        rejected("ls\u0000", RejectReason.CONTROL_CHARACTER)
    }

    @Test fun unknownProgramsAreRejected() {
        rejected("curl http://evil", RejectReason.NOT_WHITELISTED)
        rejected("sh -c ls", RejectReason.NOT_WHITELISTED)
        rejected("bash", RejectReason.NOT_WHITELISTED)
        rejected("/system/bin/ls", RejectReason.NOT_WHITELISTED)
    }

    @Test fun emptyAndOversizedCommands() {
        rejected("   ", RejectReason.EMPTY)
        rejected("echo " + "a".repeat(CommandValidator.MAX_LENGTH), RejectReason.TOO_LONG)
        rejected("echo 'unclosed", RejectReason.UNCLOSED_QUOTE)
    }

    @Test fun pythonOnlyRunsScripts() {
        assertEquals(CommandRisk.WRITE, allowed("python script.py").risk)
        assertEquals(CommandRisk.WRITE, allowed("python3 -u tools/run.py --flag").risk)
        assertEquals(CommandRisk.READ_ONLY, allowed("python --version").risk)
        rejected("python -c \"import os\"", RejectReason.FORBIDDEN_ARGUMENT)
        rejected("python -m http.server", RejectReason.FORBIDDEN_ARGUMENT)
        rejected("python", RejectReason.FORBIDDEN_ARGUMENT)
        rejected("python notes.txt", RejectReason.FORBIDDEN_ARGUMENT)
        rejected("python ../../evil.py", RejectReason.PATH_TRAVERSAL)
    }

    @Test fun pkgSubcommands() {
        assertEquals(CommandRisk.READ_ONLY, allowed("pkg search python").risk)
        assertEquals(CommandRisk.READ_ONLY, allowed("pkg list-installed").risk)
        assertEquals(CommandRisk.WRITE, allowed("pkg install -y python").risk)
        assertEquals(CommandRisk.WRITE, allowed("pkg upgrade -y").risk)
        rejected("pkg uninstall python", RejectReason.FORBIDDEN_SUBCOMMAND)
        rejected("pkg", RejectReason.FORBIDDEN_SUBCOMMAND)
    }

    @Test fun ollamaSubcommands() {
        assertEquals(CommandRisk.READ_ONLY, allowed("ollama list").risk)
        assertEquals(CommandRisk.WRITE, allowed("ollama pull gemma2:2b").risk)
        assertEquals(CommandRisk.DESTRUCTIVE, allowed("ollama rm gemma2:2b").risk)
        rejected("ollama serve", RejectReason.FORBIDDEN_SUBCOMMAND)
        rejected("ollama run gemma2:2b", RejectReason.FORBIDDEN_SUBCOMMAND)
    }

    @Test fun rmIsDestructiveAndNeverRecursive() {
        assertEquals(CommandRisk.DESTRUCTIVE, allowed("rm old.txt").risk)
        assertEquals(CommandRisk.DESTRUCTIVE, allowed("mv a.txt b.txt").risk)
        rejected("rm -rf /data/data/com.termux/files/home", RejectReason.FORBIDDEN_ARGUMENT)
        rejected("rm -R dir", RejectReason.FORBIDDEN_ARGUMENT)
        rejected("rm -fr dir", RejectReason.FORBIDDEN_ARGUMENT)
        rejected("rm --recursive dir", RejectReason.FORBIDDEN_ARGUMENT)
        rejected("rm ../usr/bin/bash", RejectReason.PATH_TRAVERSAL)
    }

    @Test fun findCannotExecuteOrDelete() {
        assertTrue(CommandValidator.isValid("find . -name '*.py'"))
        rejected("find . -delete", RejectReason.FORBIDDEN_ARGUMENT)
        rejected("find . -exec rm {} +", RejectReason.FORBIDDEN_ARGUMENT)
    }

    @Test fun writeCommandsRejectParentTraversal() {
        assertEquals(CommandRisk.WRITE, allowed("mkdir projects/new").risk)
        rejected("cp file ../../usr/lib/x", RejectReason.PATH_TRAVERSAL)
        // Reading with .. is harmless inside the Android sandbox.
        assertTrue(CommandValidator.isValid("ls .."))
    }

    @Test fun oldSetupCommandIsNoLongerAnAgentCommand() {
        assertFalse(CommandValidator.isValid("pkg upgrade && pkg update -y"))
    }
}
