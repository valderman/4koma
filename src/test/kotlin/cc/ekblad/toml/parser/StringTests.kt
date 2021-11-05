package cc.ekblad.toml.parser

import cc.ekblad.toml.StringTest
import cc.ekblad.toml.TomlValue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

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
    fun `mismatched multi-quote strings are invalid`() {
        assertDocumentParseError("a = '''6 apostrophes: ''''''")
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

    @Test
    fun `can parse tricky multiline quotes`() {
        val toml = """
           # Make sure that quotes inside multiline strings are allowed, including right
           # after the opening '''/""${'"'} and before the closing '''/""${'"'}
           
           lit_one = ''''one quote''''
           lit_two = '''''two quotes'''''
           lit_one_space = ''' 'one quote' '''
           lit_two_space = ''' ''two quotes'' '''
           
           one = ""${'"'}${'"'}one quote""${'"'}${'"'}
           two = ""${'"'}${'"'}${'"'}two quotes""${'"'}${'"'}${'"'}
           one_space = ""${'"'} "one quote" ""${'"'}
           two_space = ""${'"'} ""two quotes"" ""${'"'}
           
           mismatch1 = ""${'"'}aaa'''bbb""${'"'}
           mismatch2 = '''aaa""${'"'}bbb'''
        """.trimIndent()

        val expected = TomlValue.Map(
            "lit_one" to TomlValue.String("'one quote'"),
            "lit_two" to TomlValue.String("''two quotes''"),
            "lit_one_space" to TomlValue.String(" 'one quote' "),
            "lit_two_space" to TomlValue.String(" ''two quotes'' "),
            "one" to TomlValue.String("\"one quote\""),
            "two" to TomlValue.String("\"\"two quotes\"\""),
            "one_space" to TomlValue.String(" \"one quote\" "),
            "two_space" to TomlValue.String(" \"\"two quotes\"\" "),
            "mismatch1" to TomlValue.String("aaa'''bbb"),
            "mismatch2" to TomlValue.String("aaa\"\"\"bbb"),
        )

        assertEquals(
            expected,
            TomlValue.from(toml)
        )
    }

    @Test
    fun `parse error on ASCII control chars`() {
        asciiControlChars.assertAll {
            assertDocumentParseError("foo = 'bad: $it'")
            assertDocumentParseError("foo = \"bad: $it\"")
            assertDocumentParseError("foo = '''bad: $it'''")
            assertDocumentParseError("foo = \"\"\"bad: $it\"\"\"")
        }
    }

    @Test
    fun `parse error on invalid escape code`() {
        assertDocumentParseError(
            """
                invalid-codepoint = "This string contains a non scalar unicode codepoint \uD801"
            """.trimIndent()
        )
    }
}
