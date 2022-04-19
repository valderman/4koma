package cc.ekblad.toml.utilities

import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.model.merge
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class TomlValueMergeTests : UnitTest {
    @Test
    fun `maps are recursively merged`() {
        val now = LocalDateTime.now()
        val lhs = TomlValue.Map(
            "foo" to TomlValue.Map(
                "bar" to TomlValue.Map("baz" to TomlValue.String("asd")),
                "qwe" to TomlValue.LocalDateTime(now),
                "jkl" to TomlValue.Integer(100),
            )
        )
        val rhs = TomlValue.Map(
            "foo" to TomlValue.Map(
                "bar" to TomlValue.Map(
                    "baz" to TomlValue.String("qwe"),
                    "xcv" to TomlValue.Integer(321)
                ),
                "jkl" to TomlValue.Bool(false)
            )
        )
        val expected = TomlValue.Map(
            "foo" to TomlValue.Map(
                "bar" to TomlValue.Map(
                    "baz" to TomlValue.String("qwe"),
                    "xcv" to TomlValue.Integer(321)
                ),
                "qwe" to TomlValue.LocalDateTime(now),
                "jkl" to TomlValue.Bool(false)
            )
        )
        assertEquals(expected, lhs.merge(rhs))
    }

    @Test
    fun `can merge two disjoint maps`() {
        val lhs = TomlValue.Map("foo" to TomlValue.String("bar"))
        val rhs = TomlValue.Map("bar" to TomlValue.Integer(123))
        val expected = TomlValue.Map(
            "foo" to TomlValue.String("bar"),
            "bar" to TomlValue.Integer(123)
        )
        assertEquals(expected, lhs.merge(rhs))
    }

    @Test
    fun `can merge map with non-map`() {
        val map = TomlValue.Map("foo" to TomlValue.String("bar"))
        val nonMap = TomlValue.String("not a map")
        assertEquals(map, nonMap.merge(map))
        assertEquals(nonMap, map.merge(nonMap))
    }

    @Test
    fun `can merge non-map with non-map`() {
        val a = TomlValue.Integer(123)
        val b = TomlValue.String("not 123")
        assertEquals(a, b.merge(a))
        assertEquals(b, a.merge(b))
    }

    @Test
    fun `can merge two partially overlapping maps`() {
        val lhs = TomlValue.Map("foo" to TomlValue.String("bar"), "bar" to TomlValue.Bool(true))
        val rhs = TomlValue.Map("bar" to TomlValue.Integer(123), "baz" to TomlValue.Double(1.23))
        val expected = TomlValue.Map(
            "foo" to TomlValue.String("bar"),
            "bar" to TomlValue.Integer(123),
            "baz" to TomlValue.Double(1.23)
        )
        assertEquals(expected, lhs.merge(rhs))
    }
}
