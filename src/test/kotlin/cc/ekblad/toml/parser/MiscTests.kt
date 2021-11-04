package cc.ekblad.toml.parser

import cc.ekblad.toml.StringTest
import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.UnitTest
import kotlin.test.Test

class MiscTests : StringTest {
    @Test
    fun `can parse trailing whitespace`() {
        assertParsesTo(TomlValue.Bool(true), "true    ")
        assertParsesTo(TomlValue.Bool(false), "false\n\n")
    }

    @Test
    fun `throws on trailing garbage`() {
        assertDocumentParseError(
            """
                [foo]
                bar = 123
                
                HELLO I AM GARBAGE
            """.trimIndent()
        )
    }

    @Test
    fun `throws on unspecified value`() {
        assertDocumentParseError("foo =")
    }

    @Test
    fun `throws on comment containing ASCII control char`() {
        asciiControlChars.assertAll {
            assertDocumentParseError("# Bad comment: $it (${it.code})")
        }
    }

    @Test
    fun `throws on key containing plus sign`() {
        assertDocumentParseError("+12 = 12")
    }
}
