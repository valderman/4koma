package cc.ekblad.toml

import cc.ekblad.toml.RandomTest.Companion.random
import kotlin.test.Test

class IntTests : RandomTest {
    @Test
    fun `can parse decimal integers`() {
        random.values(100) { nextLong() }.assertAll {
            assertParsesTo(TomlValue.Integer(it), it.toString())
        }
    }

    @Test
    fun `can parse explicitly positive integers`() {
        random.values(10) { nextLong(0, 100) }.assertAll {
            assertParsesTo(TomlValue.Integer(it), "+${it}")
        }
    }

    @Test
    fun `can parse hexadecimal integers`() {
        random.values(100) { nextLong(0, Long.MAX_VALUE) }.assertAll {
            assertParsesTo(TomlValue.Integer(it), "0x${it.toString(16)}")
        }
    }

    @Test
    fun `can parse octal integers`() {
        random.values(100) { nextLong(0, Long.MAX_VALUE) }.assertAll {
            assertParsesTo(TomlValue.Integer(it), "0o${it.toString(8)}")
        }
    }

    @Test
    fun `can parse binary integers`() {
        random.values(100) { nextLong(0, Long.MAX_VALUE) }.assertAll {
            assertParsesTo(TomlValue.Integer(it), "0b${it.toString(2)}")
        }
    }
}
