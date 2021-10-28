package cc.ekblad.toml

import kotlin.test.Test
import kotlin.test.assertEquals

class TableArrayTests : UnitTest {
    @Test
    fun `can parse simple table array`() {
        val expr = """
            [[foo]]
            bar = 1
            
            [[foo]]
            bar = 2
            baz = 'qwe'
            
            [[bar]]
            bar = 3
            
            [[foo]]
            [[foo]]
            bar = 4
        """.trimIndent()

        val expected = TomlValue.Map(
            "foo" to TomlValue.List(
                TomlValue.Map("bar" to TomlValue.Integer(1)),
                TomlValue.Map("bar" to TomlValue.Integer(2), "baz" to TomlValue.String("qwe")),
                TomlValue.Map(),
                TomlValue.Map("bar" to TomlValue.Integer(4)),
            ),
            "bar" to TomlValue.List(
                TomlValue.Map("bar" to TomlValue.Integer(3)),
            )
        )

        assertEquals(expected, TomlValue.from(expr))
    }
}
