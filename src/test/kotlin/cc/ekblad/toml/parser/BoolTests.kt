package cc.ekblad.toml.parser

import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.UnitTest
import kotlin.test.Test

class BoolTests : UnitTest {
    @Test
    fun `can parse booleans`() {
        assertParsesTo(TomlValue.Bool(true), "true")
        assertParsesTo(TomlValue.Bool(false), "false")
    }
}
