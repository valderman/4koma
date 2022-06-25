package cc.ekblad.toml.serializer

import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.serialization.from
import cc.ekblad.toml.serialization.write
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class SerializerTests : UnitTest {
    @Test
    fun `can serialize single key value pair`() {
        assertSerializesTo(
            TomlValue.Map("foo" to TomlValue.String("bar")),
            "foo = \"bar\""
        )
    }

    @Test
    fun `can't serialize string with invalid chars`() {
        val error = assertFailsWith<TomlException.SerializationError> {
            TomlValue.Map("foo" to TomlValue.String("Bad char: \u0000")).write(StringBuffer())
        }
        assertContains(error.message, "\\u0")
        assertNull(error.cause)
    }

    @Test
    fun `can serialize top level list`() {
        assertSerializesTo(
            TomlValue.Map(
                "list" to TomlValue.List(TomlValue.String("foo"), TomlValue.String("bar"))
            ),
            "list = [ \"foo\", \"bar\" ]"
        )
    }

    @Test
    fun `can serialize top level list containing both map and non-map elements`() {
        assertSerializesTo(
            TomlValue.Map(
                "list" to TomlValue.List(
                    TomlValue.String("foo"),
                    TomlValue.Map("bar" to TomlValue.String("baz"))
                )
            ),
            "list = [ \"foo\", { bar = \"baz\" } ]"
        )
    }

    @Test
    fun `can serialize nested list`() {
        assertSerializesTo(
            TomlValue.Map(
                "list" to TomlValue.List(TomlValue.List(TomlValue.String("foo"), TomlValue.String("bar")))
            ),
            "list = [ [ \"foo\", \"bar\" ] ]"
        )
    }

    @Test
    fun `can serialize simple object`() {
        assertSerializesTo(
            TomlValue.Map(
                "foo" to TomlValue.Map(
                    "simpleString" to TomlValue.String("hello"),
                    "literalString" to TomlValue.String("hello\"world"),
                    "multilineString" to TomlValue.String("hello\nworld"),
                    "multilineLiteralString" to TomlValue.String("hello\n\"\"world\"\""),
                    "weird string" to TomlValue.String("'''hello\n\"\"world\"\"x"),
                )
            ),
            """
                [foo]
                simpleString = "hello"
                literalString = 'hello"world'
                multilineString = ""${'"'}
                hello
                world""${'"'}
                multilineLiteralString = '''
                hello
                ""world""'''
                'weird string' = "'''hello\n\"\"world\"\"x"
            """.trimIndent()
        )
    }

    @Test
    fun `can serialize multiple objects`() {
        assertSerializesTo(
            TomlValue.Map(
                "foo" to TomlValue.Map(
                    "bar" to TomlValue.Bool(true),
                    "baz" to TomlValue.Integer(0xffff),
                ),
                "number two" to TomlValue.Map(
                    "bar again" to TomlValue.LocalDate(LocalDate.of(2021, 12, 13)),
                    "\"baz\"\nagain" to TomlValue.LocalDateTime(
                        LocalDateTime.of(2021, 12, 13, 0, 38, 11, 0),
                    )
                ),
            ),
            """
                [foo]
                bar = true
                baz = 65535

                ['number two']
                'bar again' = 2021-12-13
                "\"baz\"\nagain" = 2021-12-13T00:38:11
            """.trimIndent()
        )
    }

    @Test
    fun `can serialize nested objects`() {
        assertSerializesTo(
            TomlValue.Map(
                "foo" to TomlValue.Map(
                    "bar" to TomlValue.Map(
                        "baz" to TomlValue.String("baztu")
                    ),
                    "baz" to TomlValue.Integer(0xffff),
                    "bark" to TomlValue.Map(
                        "one" to TomlValue.Map("two" to TomlValue.LocalTime(LocalTime.of(0, 27)))
                    ),
                ),
            ),
            """
                [foo]
                bar.baz = "baztu"
                baz = 65535
                bark.one.two = 00:27:00
            """.trimIndent()
        )
    }

    @Test
    fun `can serialize table arrays`() {
        assertSerializesTo(
            TomlValue.Map(
                "foo" to TomlValue.List(
                    TomlValue.Map(
                        "baz" to TomlValue.String("baztu"),
                        "in both" to TomlValue.Integer(-5),
                    ),
                    TomlValue.Map(
                        "one" to TomlValue.Map("two" to TomlValue.LocalTime(LocalTime.of(0, 27))),
                        "in both" to TomlValue.Integer(42)
                    ),
                ),
            ),
            """
                [[foo]]
                baz = "baztu"
                'in both' = -5

                [[foo]]
                one.two = 00:27:00
                'in both' = 42
            """.trimIndent()
        )
    }

    @Test
    fun `can serialize inline arrays and tables`() {
        assertSerializesTo(
            TomlValue.Map(
                "foo" to TomlValue.Map(
                    "bar" to TomlValue.List(
                        TomlValue.Integer(1),
                        TomlValue.String("asd"),
                        TomlValue.List(TomlValue.Bool(false), TomlValue.Double(1.2)),
                        TomlValue.Map(
                            "a" to TomlValue.Map("inner" to TomlValue.String("very inner")),
                            "b" to TomlValue.List(TomlValue.Integer(345)),
                            "c" to TomlValue.List(),
                            "d" to TomlValue.String("hello"),
                        )
                    )
                ),
            ),
            """
                [foo]
                bar = [ 1, "asd", [ false, 1.2 ], { a = { inner = "very inner" }, b = [ 345 ], c = [  ], d = "hello" } ]
            """.trimIndent()
        )
    }

    @Test
    fun `can serialize mixed tables, table arrays, and key-value pairs`() {
        assertSerializesTo(
            TomlValue.Map(
                "key" to TomlValue.String("value"),
                "foo" to TomlValue.Map(
                    "bar" to TomlValue.Map(
                        "baz" to TomlValue.String("baztu")
                    ),
                    "baz" to TomlValue.Integer(0xffff),
                    "bark" to TomlValue.Map(
                        "one" to TomlValue.Map("two" to TomlValue.LocalTime(LocalTime.of(0, 27)))
                    ),
                ),
                "yarr" to TomlValue.List(
                    TomlValue.Map(
                        "baz" to TomlValue.String("baztu"),
                        "in both" to TomlValue.Integer(-5),
                    ),
                    TomlValue.Map(
                        "one" to TomlValue.Map("two" to TomlValue.LocalTime(LocalTime.of(0, 27))),
                        "in both" to TomlValue.Integer(42)
                    ),
                ),
                "key2" to TomlValue.OffsetDateTime(
                    OffsetDateTime.of(2021, 12, 13, 0, 38, 11, 0, ZoneOffset.UTC)
                ),
            ),
            """
                key = "value"
                key2 = 2021-12-13T00:38:11Z

                [foo]
                bar.baz = "baztu"
                baz = 65535
                bark.one.two = 00:27:00

                [[yarr]]
                baz = "baztu"
                'in both' = -5

                [[yarr]]
                one.two = 00:27:00
                'in both' = 42
            """.trimIndent()
        )
    }

    private fun assertSerializesTo(tomlValue: TomlValue.Map, tomlDocument: String) {
        val buf = StringBuffer()
        tomlValue.write(buf)
        val serializedTomlDocument = buf.toString()
        assertEquals("$tomlDocument\n", serializedTomlDocument)
        assertEquals(tomlValue, TomlValue.from(serializedTomlDocument))
    }
}
