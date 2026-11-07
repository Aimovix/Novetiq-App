package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class TranslationsTest {

    @Test fun englishAndGermanHaveTheSameKeys() {
        val en = Strings.keys(Strings.ENGLISH)
        val de = Strings.keys(Strings.GERMAN)
        assertEquals("Missing in German: ${en - de}", emptySet<String>(), en - de)
        assertEquals("Missing in English: ${de - en}", emptySet<String>(), de - en)
    }

    @Test fun everyKeyUsedInTheUiExists() {
        val sourceDir = listOf(File("src/main/java"), File("app/src/main/java")).first { it.exists() }
        val pattern = Regex("""trans\("([a-z0-9_]+)"\)|Strings\.get\("([a-z0-9_]+)"|\bt\("([a-z0-9_]+)"\)""")
        val used = sourceDir.walkTopDown().filter { it.extension == "kt" }.flatMap { file ->
            pattern.findAll(file.readText()).map { m -> m.groupValues.drop(1).first { it.isNotEmpty() } }
        }.toSet()
        assertTrue(used.size > 50)
        val missing = used - Strings.keys(Strings.ENGLISH)
        assertEquals("Keys used in code but not translated: $missing", emptySet<String>(), missing)
    }

    @Test fun defaultLanguageFollowsDevice() {
        assertEquals(Strings.GERMAN, Strings.defaultLanguageFor(Locale.GERMANY))
        assertEquals(Strings.ENGLISH, Strings.defaultLanguageFor(Locale.US))
    }

    @Test fun formatPlaceholdersWork() {
        assertEquals("Schritt 2 von 3", Strings.get("step_x_of_y", Strings.GERMAN).format(2, 3))
        assertEquals("unknown_key", Strings.get("unknown_key", Strings.GERMAN))
    }
}
