package cc.ekblad.toml.parser

import cc.ekblad.toml.RandomTest
import cc.ekblad.toml.TomlException
import cc.ekblad.toml.TomlValue
import kotlin.test.Test
import kotlin.test.assertFailsWith

class IntTests : RandomTest {
    @Test
    fun `can parse decimal integers`() {
        random.values(100) { nextLong().withMaxDigits(10) }.assertAll {
            assertParsesTo(TomlValue.Integer(it), it.toString())
        }
    }

    @Test
    fun `can parse decimal integers with underscore separators`() {
        random.values(100) {
            values(nextInt(1, 5)) { nextLong(1, 1000) }
        }.assertAll {
            val actual = it.joinToString("").toLong()
            val string = it.joinToString("_")
            assertParsesTo(TomlValue.Integer(actual), string)
        }
    }

    @Test
    fun `can parse explicitly positive integers`() {
        random.values(10) { nextLong(0, 100) }.assertAll {
            assertParsesTo(TomlValue.Integer(it), "+$it")
        }
    }

    @Test
    fun `can parse hexadecimal integers`() {
        random.values(100) { nextLong(0, Long.MAX_VALUE).withMaxDigits(10) }.assertAll {
            assertParsesTo(TomlValue.Integer(it), "0x${it.toString(16)}")
        }
    }

    @Test
    fun `can parse octal integers`() {
        random.values(100) { nextLong(0, Long.MAX_VALUE).withMaxDigits(10) }.assertAll {
            assertParsesTo(TomlValue.Integer(it), "0o${it.toString(8)}")
        }
    }

    @Test
    fun `can parse binary integers`() {
        random.values(100) { nextLong(0, Long.MAX_VALUE).withMaxDigits(10) }.assertAll {
            assertParsesTo(TomlValue.Integer(it), "0b${it.toString(2)}")
        }
    }

    @Test
    fun `throws on bad int`() {
        listOf("10_", "_8", "ffff", "0b2", "0o8", "0xFFFFFFFFFFFFFFFFF").assertAll {
            assertFailsWith<TomlException.ParseError> { TomlValue.from("foo = $it") }
        }
    }
}
