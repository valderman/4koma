package cc.ekblad.toml

import kotlin.test.Test

class BoolTests : UnitTest {
    @Test
    fun `can parse booleans`() {
        assertParsesTo(TomlValue.Bool(true), "true")
        assertParsesTo(TomlValue.Bool(false), "false")
    }
}
