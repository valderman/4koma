package cc.ekblad.toml

import kotlin.test.Test
import kotlin.test.assertEquals

class MiscTests : UnitTest {
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
}
