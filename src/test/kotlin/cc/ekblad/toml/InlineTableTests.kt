package cc.ekblad.toml

import kotlin.test.Test

class InlineTableTests : UnitTest {
    @Test
    fun `inline table can contain multiline string`() {
        assertParsesTo(
            TomlValue.Map("foo" to TomlValue.String("a\nb\nc")),
            "{foo = \"\"\"a\nb\nc\"\"\"}"
        )
    }

    @Test
    fun `throws on bad inline table`() {
        listOf(
            "{,}", "{a=1,}", "{,a=1}", "{a=1,\nb=2}", "{a=1,,b=2}", "{a={}, a.b=1}", "{a.b=1, a={}}"
        ).assertAll(::assertValueParseError)
    }
}
