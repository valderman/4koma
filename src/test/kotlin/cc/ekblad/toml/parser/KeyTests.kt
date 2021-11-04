package cc.ekblad.toml.parser

import cc.ekblad.toml.StringTest
import cc.ekblad.toml.TomlValue
import kotlin.test.Test
import kotlin.test.assertEquals

class KeyTests : StringTest {
    @Test
    fun `can parse keys overlapping with keywords`() {
        listOf(
            "nan", "inf", "-nan", "-inf", "-10", "123", "ff", "0b1",
            "1e19", "true", "false", "2011-11-11", "0x1", "0o1",
        ).assertAll { key ->
            val actual = TomlValue.from("$key = true")
            val expected = TomlValue.Map(key to TomlValue.Bool(true))
            assertEquals(expected, actual)
        }
    }

    @Test
    fun `can parse dotted quoted keys`() {
        val actual = TomlValue.from("'a'.\"b\" = 'c'")
        val expected = TomlValue.Map(
            "a" to TomlValue.Map("b" to TomlValue.String("c"))
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `can parse float-looking keys`() {
        val actual = TomlValue.from("3.14 = 'pi'")
        val expected = TomlValue.Map(
            "3" to TomlValue.Map("14" to TomlValue.String("pi"))
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `can parse key containing quotes`() {
        assertEquals(
            TomlValue.Map("'hello'" to TomlValue.String("world")),
            TomlValue.from("\"'hello'\" = 'world'")
        )
        assertEquals(
            TomlValue.Map("\"hello\"" to TomlValue.String("world")),
            TomlValue.from("'\"hello\"' = 'world'")
        )
    }

    @Test
    fun `can parse keys containing escape codes`() {
        escapeCodeSamples.assertAll { (string, expected) ->
            assertEquals(
                TomlValue.Map(expected to TomlValue.String("world")),
                TomlValue.from("\"$string\" = 'world'")
            )
        }
    }

    @Test
    fun `escape codes are not converted in single quoted keys`() {
        assertEquals(
            TomlValue.Map("\\nhello\\t" to TomlValue.String("world")),
            TomlValue.from("'\\nhello\\t' = 'world'")
        )
    }

    @Test
    fun `can parse blank quoted keys`() {
        assertEquals(
            TomlValue.Map("" to TomlValue.String("world")),
            TomlValue.from("\"\" = 'world'")
        )
        assertEquals(
            TomlValue.Map("" to TomlValue.String("world")),
            TomlValue.from("'' = 'world'")
        )
    }

    @Test
    fun `throws on triple-quoted keys`() {
        assertDocumentParseError("'''foo''' = q")
        assertDocumentParseError("\"\"\"foo\"\"\" = q")
    }

    @Test
    fun `throws on unspecified key`() {
        assertDocumentParseError("= 123")
    }

    @Test
    fun `throws on key containing ASCII control char`() {
        asciiControlChars.assertAll {
            assertDocumentParseError("x${it}x = true")
            assertDocumentParseError("'x${it}x' = true")
            assertDocumentParseError("\"x${it}x\" = true")
        }
    }

    @Test
    fun `throws on key containing plus sign`() {
        assertDocumentParseError("+12 = 12")
    }
}
