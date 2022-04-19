package cc.ekblad.toml.transcoder

import cc.ekblad.toml.TomlException
import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.transcoding.get
import cc.ekblad.toml.transcoding.tomlMapper
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TraversalTests {
    private val mapper = tomlMapper { }

    @Test
    fun `can get simple value with decode to Any`() {
        val toml = TomlValue.Map("foo" to TomlValue.Map("bar" to TomlValue.Integer(123)))
        assertEquals(123L, toml.get<Any>(mapper, "foo", "bar"))
    }

    @Test
    fun `can get toml value`() {
        val toml = TomlValue.Map("foo" to TomlValue.Map("bar" to TomlValue.Integer(123)))
        assertEquals(TomlValue.Integer(123), toml["foo", "bar"])
    }

    @Test
    fun `can get simple value with non-Any decoding`() {
        val toml = TomlValue.Map("foo" to TomlValue.Map("bar" to TomlValue.Integer(123)))
        assertEquals(123.0, toml.get(mapper, "foo", "bar"))
    }

    @Test
    fun `can get data class`() {
        data class Bar(val baz: Int)
        val toml = TomlValue.Map(
            "foo" to TomlValue.Map(
                "bar" to TomlValue.Map("baz" to TomlValue.Integer(123))
            )
        )
        assertEquals(Bar(123), toml.get(mapper, "foo", "bar"))
    }

    @Test
    fun `trying to get nonexistent path yields null`() {
        val toml = TomlValue.Map(
            "foo" to TomlValue.Map(
                "bar" to TomlValue.Map("baz" to TomlValue.Integer(123))
            )
        )
        assertEquals(null, toml.get<Any>(mapper, "foo", "bar", "nope"))
        assertEquals(null, toml.get<Any>(mapper, "foo", "barbar", "baz"))
        assertEquals(null, toml.get<Any>(mapper, "foo", "baz"))
    }

    @Test
    fun `can get list`() {
        val toml = TomlValue.Map(
            "foo" to TomlValue.Map(
                "bar" to TomlValue.List(TomlValue.String("baz"), TomlValue.String("quux"))
            )
        )
        assertEquals(listOf("baz", "quux"), toml.get(mapper, "foo", "bar"))
    }

    @Test
    fun `can't get list as non-list`() {
        val toml = TomlValue.Map(
            "foo" to TomlValue.List(
                TomlValue.List(
                    TomlValue.String("baz"),
                    TomlValue.String("quux")
                )
            )
        )
        assertFailsWith<TomlException.DecodingError> { toml.get<String>(mapper, "foo") }
    }

    @Test
    fun `trying to get a list with ill-typed elements yields a decoding error`() {
        val toml = TomlValue.Map(
            "foo" to TomlValue.Map(
                "bar" to TomlValue.List(TomlValue.String("baz"), TomlValue.Integer(1))
            )
        )
        assertFailsWith<TomlException.DecodingError> { toml.get<List<Int>>(mapper, "foo", "bar") }
    }

    @Test
    fun `can get properties of objects in list`() {
        val toml = TomlValue.Map(
            "foo" to TomlValue.List(
                TomlValue.Map(
                    "a" to TomlValue.String("baz"),
                    "b" to TomlValue.String("quux")
                ),
                TomlValue.Map(
                    "a" to TomlValue.String("hello"),
                    "c" to TomlValue.String("world")
                ),
            )
        )
        assertEquals(listOf("baz", "hello"), toml.get(mapper, "foo", "a"))
        assertEquals(listOf("quux"), toml.get(mapper, "foo", "b"))
        assertEquals(listOf("world"), toml.get(mapper, "foo", "c"))
    }

    @Test
    fun `non-object values are ignored when accessing properties of list elements`() {
        val toml = TomlValue.Map(
            "foo" to TomlValue.List(
                TomlValue.String("c-c-c-combo breaker!"),
                TomlValue.Map(
                    "a" to TomlValue.String("hello"),
                    "c" to TomlValue.String("world")
                ),
            )
        )
        assertEquals(listOf("hello"), toml.get(mapper, "foo", "a"))
        assertEquals(emptyList<Any>(), toml.get(mapper, "foo", "b"))
        assertEquals(listOf("world"), toml.get(mapper, "foo", "c"))
    }

    @Test
    fun `can nested lists either flattened or non-flattened`() {
        val toml = TomlValue.Map(
            "foo" to TomlValue.List(
                TomlValue.List(
                    TomlValue.Map(
                        "a" to TomlValue.String("first"),
                        "b" to TomlValue.String("second")
                    )
                ),
                TomlValue.List(
                    TomlValue.Map(
                        "a" to TomlValue.String("baz"),
                        "b" to TomlValue.String("quux")
                    ),
                    TomlValue.Map(
                        "a" to TomlValue.String("hello"),
                        "c" to TomlValue.String("world")
                    ),
                )
            )
        )

        // Grouped
        assertEquals(listOf(listOf("first"), listOf("baz", "hello")), toml.get(mapper, "foo", "a"))
        assertEquals(listOf(listOf("second"), listOf("quux")), toml.get(mapper, "foo", "b"))
        assertEquals(listOf(emptyList(), listOf("world")), toml.get(mapper, "foo", "c"))

        // Flattened
        assertEquals(listOf("first", "baz", "hello"), toml.get(mapper, "foo", "a"))
        assertEquals(listOf("second", "quux"), toml.get(mapper, "foo", "b"))
        assertEquals(listOf("world"), toml.get(mapper, "foo", "c"))
    }
}
