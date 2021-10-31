package cc.ekblad.toml

import org.junit.jupiter.api.Test

// TODO: escape sequences for basic and basic ML strings
class StringTests : RandomTest {
    private val alphabet =
        "abcdefghijklmnopqrstuvwxyzåäöABCDEFGHIJKLMNOPQRSTUBWXYZÅÄÖ \t!#¤%&/()=.,[]{};:<>|ひらがなカタカナ漢字火事"

    @Test
    fun `can parse basic string`() {
        val basicAlphabet = "$alphabet'"
        random.values(100) { nextSequence(basicAlphabet) }.assertAll {
            assertParsesTo(TomlValue.String(it), "\"$it\"")
        }
    }

    @Test
    fun `can parse basic multiline string`() {
        val basicMultilineAlphabet = "$alphabet'\n"
        random.values(100) { nextSequence(basicMultilineAlphabet) }.assertAll {
            assertParsesTo(TomlValue.String(it.trimFirst()), "\"\"\"$it\"\"\"")
            assertParsesTo(TomlValue.String(it), "\"\"\"\n$it\"\"\"")
            assertParsesTo(TomlValue.String(it), "\"\"\"\r\n$it\"\"\"")
            assertParsesTo(TomlValue.String("\n$it"), "\"\"\"\n\n$it\"\"\"")
            assertParsesTo(TomlValue.String("\r\n$it"), "\"\"\"\r\n\r\n$it\"\"\"")
        }
    }

    @Test
    fun `can parse basic and basic multiline string with escape codes`() {
        listOf(
            "\\b" to "\b",
            "\\f" to "\u000C",
            "\\n" to "\n",
            "\\r" to "\r",
            "\\t" to "\t",
            "\\\"" to "\"",
            "\\\\" to "\\",
            "\\u00e5" to "å",
            "\\U0001f63f" to "😿"
        ).assertAll { (string, expected) ->
            assertParsesTo(TomlValue.String(expected), "\"$string\"")
            assertParsesTo(TomlValue.String(expected), "\"\"\"$string\"\"\"")
        }
    }

    @Test
    fun `can parse literal string`() {
        val literalAlphabet = "$alphabet\\\""
        random.values(100) { nextSequence(literalAlphabet) }.assertAll {
            assertParsesTo(TomlValue.String(it), "'$it'")
        }
        assertParsesTo(TomlValue.String("\\n"), "'\\n'")
        assertParsesTo(TomlValue.String("\\t"), "'\\t'")
        assertParsesTo(TomlValue.String("\\uffff"), "'\\uffff'")
    }

    @Test
    fun `can parse literal multiline string`() {
        val literalMultilineAlphabet = "$alphabet\\\"\n"
        random.values(100) { nextSequence(literalMultilineAlphabet) }.assertAll {
            assertParsesTo(TomlValue.String(it.trimFirst()), "'''$it'''")
            assertParsesTo(TomlValue.String(it), "'''\n$it'''")
            assertParsesTo(TomlValue.String(it), "'''\r\n$it'''")
            assertParsesTo(TomlValue.String("\n$it"), "'''\n\n$it'''")
            assertParsesTo(TomlValue.String("\r\n$it"), "'''\r\n\r\n$it'''")
        }
        assertParsesTo(TomlValue.String("\\n"), "'''\\n'''")
        assertParsesTo(TomlValue.String("\\t"), "'''\\t'''")
        assertParsesTo(TomlValue.String("\\uffff"), "'''\\uffff'''")
    }
}
