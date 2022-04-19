package cc.ekblad.toml.parser

import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.model.TomlValue
import kotlin.test.Test

class BoolTests : UnitTest {
    @Test
    fun `can parse booleans`() {
        assertParsesTo(TomlValue.Bool(true), "true")
        assertParsesTo(TomlValue.Bool(false), "false")
    }
}
