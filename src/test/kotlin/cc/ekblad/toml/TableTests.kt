package cc.ekblad.toml

import java.time.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TableTests : UnitTest {
    @Test
    fun `can parse top level properties`() {
        val expr = """
            foo = 123
            bar = 23:02:04
            "" = false
            "あ！" = 1E4
        """.trimIndent()

        val expected = TomlValue.Map(
            "foo" to TomlValue.Integer(123),
            "bar" to TomlValue.LocalTime(LocalTime.of(23, 2, 4)),
            "" to TomlValue.Bool(false),
            "あ！" to TomlValue.Double(1e4),
        )

        assertEquals(expected, TomlValue.from(expr))
    }

    @Test
    fun `can parse top level with dotted keys`() {
        val expr = """
            foo.bar.baz = 123
            foo.quux = 'asd'
        """.trimIndent()

        val expected = TomlValue.Map(
            "foo" to TomlValue.Map(
                "bar" to TomlValue.Map(
                    "baz" to TomlValue.Integer(123)
                ),
                "quux" to TomlValue.String("asd")
            )
        )

        assertEquals(expected, TomlValue.from(expr))
    }

    @Test
    fun `can parse subtables with plain keys`() {
        val expr = """
            top = '''
                top!
            '''
            hello.universe = "no"
            [foo]
            bar = 123
            baz="qwe"
            
            [hello]
            world = true
            earth.gravity=9.82
        """.trimIndent()

        val expected = TomlValue.Map(
            "top" to TomlValue.String("    top!\n"),
            "foo" to TomlValue.Map(
                "bar" to TomlValue.Integer(123),
                "baz" to TomlValue.String("qwe")
            ),
            "hello" to TomlValue.Map(
                "world" to TomlValue.Bool(true),
                "universe" to TomlValue.String("no"),
                "earth" to TomlValue.Map(
                    "gravity" to TomlValue.Double(9.82)
                )
            )
        )

        assertEquals(expected, TomlValue.from(expr))
    }

    @Test
    fun `can parse subtables with dotted keys`() {
        val expr = """
            [foo . "bar.baz"]
              bar = 123
           baz="qwe"
            
            [hello.world]
            say.it = true
            inline = { a = 1,b=2 ,c={d=3},c.e=4}
            last = [{}]
        """.trimIndent()

        val expected = TomlValue.Map(
            "foo" to TomlValue.Map(
                "bar.baz" to TomlValue.Map(
                    "bar" to TomlValue.Integer(123),
                    "baz" to TomlValue.String("qwe")
                )
            ),
            "hello" to TomlValue.Map(
                "world" to TomlValue.Map(
                    "say" to TomlValue.Map(
                        "it" to TomlValue.Bool(true),
                    ),
                    "inline" to TomlValue.Map(
                        "a" to TomlValue.Integer(1),
                        "b" to TomlValue.Integer(2),
                        "c" to TomlValue.Map(
                            "d" to TomlValue.Integer(3),
                            "e" to TomlValue.Integer(4)
                        )
                    ),
                    "last" to TomlValue.List(TomlValue.Map())
                )
            )
        )

        assertEquals(expected, TomlValue.from(expr))
    }
}