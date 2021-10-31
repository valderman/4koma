package cc.ekblad.toml.parser

import cc.ekblad.toml.StringTest
import cc.ekblad.toml.TomlValue
import org.junit.jupiter.api.Test

class StringTests : StringTest {
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
        escapeCodeSamples.assertAll { (string, expected) ->
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
