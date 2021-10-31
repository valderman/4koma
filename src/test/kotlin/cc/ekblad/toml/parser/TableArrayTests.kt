package cc.ekblad.toml.parser

import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.UnitTest
import java.time.LocalDate
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

    @Test
    fun `can parse dotted key table array`() {
        val expr = """
            [[foo.bar]]
            baz = 1
        """.trimIndent()

        val expected = TomlValue.Map(
            "foo" to TomlValue.Map(
                "bar" to TomlValue.List(
                    TomlValue.Map("baz" to TomlValue.Integer(1))
                )
            )
        )

        assertEquals(expected, TomlValue.from(expr))
    }

    @Test
    fun `can define complex document with table arrays`() {
        val expr = """
            [[fruits]]
            name = "apple"

            [fruits.physical]  # subtable
            color = "red"
            shape = "round"

            [[fruits.varieties]]  # nested array of tables
            name = "red delicious"

            [[fruits.varieties]]
            name = "granny smith"


            [[fruits]]
            name = "banana"

            [[fruits.varieties]]
            name = "plantain"
        """.trimIndent()

        val expected = TomlValue.Map(
            "fruits" to TomlValue.List(
                TomlValue.Map(
                    "name" to TomlValue.String("apple"),
                    "physical" to TomlValue.Map(
                        "color" to TomlValue.String("red"),
                        "shape" to TomlValue.String("round")
                    ),
                    "varieties" to TomlValue.List(
                        TomlValue.Map("name" to TomlValue.String("red delicious")),
                        TomlValue.Map("name" to TomlValue.String("granny smith"))
                    )
                ),
                TomlValue.Map(
                    "name" to TomlValue.String("banana"),
                    "varieties" to TomlValue.List(
                        TomlValue.Map("name" to TomlValue.String("plantain"))
                    )
                )
            )
        )
        assertEquals(expected, TomlValue.from(expr))
    }

    @Test
    fun `can add sub-table array of already defined table`() {
        val expr = """
            [foo]
            baz = 2
            
            [[foo.bar]]
            baz = 1
        """.trimIndent()

        val expected = TomlValue.Map(
            "foo" to TomlValue.Map(
                "baz" to TomlValue.Integer(2),
                "bar" to TomlValue.List(
                    TomlValue.Map("baz" to TomlValue.Integer(1))
                )
            )
        )

        assertEquals(expected, TomlValue.from(expr))
    }

    @Test
    fun `can extend last item of array table`() {
        val expr = """
            [[foo]]
            baz = "asd"
            
            [[foo]]
            baz = 2011-11-11
            
            [foo.bar]
            baz = "qwe"
        """.trimIndent()

        val expected = TomlValue.Map(
            "foo" to TomlValue.List(
                TomlValue.Map(
                    "baz" to TomlValue.String("asd"),
                ),
                TomlValue.Map(
                    "bar" to TomlValue.Map(
                        "baz" to TomlValue.String("qwe")
                    ),
                    "baz" to TomlValue.LocalDate(LocalDate.of(2011, 11, 11)),
                )
            )
        )

        assertEquals(expected, TomlValue.from(expr))
    }

    @Test
    fun `can't overwrite table array`() {
        assertDocumentParseError(
            """
                [[fruits]]
                name = "apple"

                [[fruits.varieties]]
                name = "red delicious"

                # INVALID: This table conflicts with the previous array of tables
                [fruits.varieties]
                name = "granny smith"
            """.trimIndent()
        )
    }

    @Test
    fun `throws on extending non-table array`() {
        assertDocumentParseError(
            """
                foo = []
                [[foo]]
            """.trimIndent()
        )

        assertDocumentParseError(
            """
                [foo]
                [[foo]]
            """.trimIndent()
        )

        assertDocumentParseError(
            """
                [[foo]]
                [foo]
            """.trimIndent()
        )

        assertDocumentParseError(
            """
                foo = 123
                [[foo]]
            """.trimIndent()
        )

        assertDocumentParseError(
            """
                foo.bar = 1
                [[foo]]
            """.trimIndent()
        )

        assertDocumentParseError(
            """
                [[foo.bar]]
                [foo]
                bar = 1
            """.trimIndent()
        )

        assertDocumentParseError(
            """
                [fruits.physical]
                color = "red"
                shape = "round"

                [[fruits.physical]]
                color = "green"
            """.trimIndent()
        )

        assertDocumentParseError(
            """
                [fruit.physical]  # subtable, but to which parent element should it belong?
                color = "red"
                shape = "round"
                
                [[fruit]]  # parser must throw an error upon discovering that "fruit" is
                           # an array rather than a table
                name = "apple"
            """.trimIndent()
        )
    }
}
