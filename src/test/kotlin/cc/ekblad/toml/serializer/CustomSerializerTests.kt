package cc.ekblad.toml.serializer

import cc.ekblad.toml.TomlSerializer
import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.serialization.CollectionSyntax
import cc.ekblad.toml.serialization.InlineListMode
import cc.ekblad.toml.serialization.from
import cc.ekblad.toml.tomlSerializer
import kotlin.test.Test
import kotlin.test.assertEquals

class CustomSerializerTests : UnitTest {
    @Test
    fun `can configure indent step`() {
        assertSerializesTo(
            tomlSerializer { indentStep(2) },
            TomlValue.Map(
                "list" to TomlValue.List(
                    TomlValue.List(TomlValue.String("hello")),
                    TomlValue.List(TomlValue.String("world")),
                )
            ),
            """
                list = [
                  [ "hello" ],
                  [ "world" ]
                ]
            """.trimIndent()
        )
    }

    @Test
    fun `can configure table syntax globally`() {
        assertSerializesTo(
            tomlSerializer { preferTableSyntax(CollectionSyntax.Inline) },
            TomlValue.Map(
                "object" to TomlValue.Map(
                    "hello" to TomlValue.String("world"),
                    "foo" to TomlValue.String("bar"),
                )
            ),
            """
                object = { hello = "world", foo = "bar" }
            """.trimIndent()
        )

        assertSerializesTo(
            tomlSerializer { preferTableSyntax(CollectionSyntax.Table) },
            TomlValue.Map(
                "object" to TomlValue.Map(
                    "hello" to TomlValue.String("world"),
                    "foo" to TomlValue.String("bar"),
                )
            ),
            """
                [object]
                hello = "world"
                foo = "bar"
            """.trimIndent()
        )
    }

    @Test
    fun `can configure list syntax globally`() {
        assertSerializesTo(
            tomlSerializer { preferListSyntax(CollectionSyntax.Inline) },
            TomlValue.Map(
                "list" to TomlValue.List(
                    TomlValue.Map("hello" to TomlValue.String("world")),
                    TomlValue.Map("hello" to TomlValue.String("bar")),
                )
            ),
            """
                list = [
                    { hello = "world" },
                    { hello = "bar" }
                ]
            """.trimIndent()
        )

        assertSerializesTo(
            tomlSerializer { preferListSyntax(CollectionSyntax.Table) },
            TomlValue.Map(
                "list" to TomlValue.List(
                    TomlValue.Map("hello" to TomlValue.String("world")),
                    TomlValue.Map("hello" to TomlValue.String("bar")),
                )
            ),
            """
                [[list]]
                hello = "world"
                
                [[list]]
                hello = "bar"
            """.trimIndent()
        )
    }

    @Test
    fun `lists that must be inline are inline regardless of preferListSyntax setting`() {
        assertSerializesTo(
            tomlSerializer { preferListSyntax(CollectionSyntax.Table) },
            TomlValue.Map(
                "list" to TomlValue.List(
                    TomlValue.String("world"),
                    TomlValue.String("bar"),
                )
            ),
            """
                list = [ "world", "bar" ]
            """.trimIndent()
        )
    }

    @Test
    fun `can force inline list mode to single line globally`() {
        assertSerializesTo(
            tomlSerializer {
                inlineListMode(InlineListMode.SingleLine)
                preferListSyntax(CollectionSyntax.Inline)
            },
            TomlValue.Map(
                "list" to TomlValue.List(
                    TomlValue.Map("hello" to TomlValue.String("world")),
                    TomlValue.Map("hello" to TomlValue.String("bar")),
                )
            ),
            """
                list = [ { hello = "world" }, { hello = "bar" } ]
            """.trimIndent()
        )
    }

    @Test
    fun `can force inline list mode to multi line globally`() {
        assertSerializesTo(
            tomlSerializer {
                inlineListMode(InlineListMode.MultiLine)
                preferListSyntax(CollectionSyntax.Inline)
            },
            TomlValue.Map(
                "list" to TomlValue.List(TomlValue.String("hello"), TomlValue.Integer(123))
            ),
            """
                list = [
                    "hello",
                    123
                ]
            """.trimIndent()
        )
    }

    @Test
    fun `forced multi line lists do not override the prohibition on newlines in inline tables`() {
        assertSerializesTo(
            tomlSerializer {
                inlineListMode(InlineListMode.MultiLine)
                preferListSyntax(CollectionSyntax.Inline)
                preferTableSyntax(CollectionSyntax.Inline)
            },
            TomlValue.Map(
                "object" to TomlValue.Map(
                    "list" to TomlValue.List(TomlValue.String("hello"), TomlValue.Integer(123))
                )
            ),
            """
                object = { list = [ "hello", 123 ] }
            """.trimIndent()
        )
    }

    private fun assertSerializesTo(serializer: TomlSerializer, tomlValue: TomlValue.Map, tomlDocument: String) {
        val buf = StringBuffer()
        serializer.write(tomlValue, buf)
        val serializedTomlDocument = buf.toString()
        assertEquals("$tomlDocument\n", serializedTomlDocument)
        assertEquals(tomlValue, TomlValue.from(serializedTomlDocument))
    }
}
