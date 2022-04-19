package cc.ekblad.toml.transcoder

import cc.ekblad.toml.TomlException
import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.transcoding.tomlMapper
import java.math.BigDecimal
import java.math.BigInteger
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class BuiltinEncoderTests : UnitTest {
    private val mapper = tomlMapper { }

    @Test
    fun `can encode integers`() {
        assertEncodesTo(123, TomlValue.Integer(123))
        assertEncodesTo(123L, TomlValue.Integer(123))
        assertEncodesTo(BigInteger.valueOf(123), TomlValue.Integer(123))
    }

    @Test
    fun `can encode floats`() {
        assertEncodesTo(1.23, TomlValue.Double(1.23))
        assertEncodesTo(1.5f, TomlValue.Double(1.5))
        assertEncodesTo(BigDecimal.valueOf(0.123), TomlValue.Double(0.123))
    }

    @Test
    fun `can encode strings`() {
        assertEncodesTo("hello", TomlValue.String("hello"))
    }

    @Test
    fun `can encode booleans`() {
        assertEncodesTo(true, TomlValue.Bool(true))
        assertEncodesTo(false, TomlValue.Bool(false))
    }

    @Test
    fun `can encode time and date`() {
        val clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
        assertEncodesTo(LocalDate.now(clock), TomlValue.LocalDate(LocalDate.now(clock)))
        assertEncodesTo(LocalTime.now(clock), TomlValue.LocalTime(LocalTime.now(clock)))
        assertEncodesTo(LocalDateTime.now(clock), TomlValue.LocalDateTime(LocalDateTime.now(clock)))
        assertEncodesTo(OffsetDateTime.now(clock), TomlValue.OffsetDateTime(OffsetDateTime.now(clock)))
    }

    @Test
    fun `can encode simple lists`() {
        assertEncodesTo(
            listOf(1, 2, 3),
            TomlValue.List(TomlValue.Integer(1), TomlValue.Integer(2), TomlValue.Integer(3))
        )
    }

    @Test
    fun `can encode nested lists`() {
        assertEncodesTo(
            listOf(listOf(1), listOf(2, 3)),
            TomlValue.List(
                TomlValue.List(TomlValue.Integer(1)),
                TomlValue.List(TomlValue.Integer(2), TomlValue.Integer(3))
            )
        )
    }

    @Test
    fun `can encode heterogeneous lists`() {
        assertEncodesTo(
            listOf(listOf(1), 2, 3),
            TomlValue.List(
                TomlValue.List(TomlValue.Integer(1)),
                TomlValue.Integer(2),
                TomlValue.Integer(3)
            )
        )
    }

    @Test
    fun `null values in lists are ignored`() {
        assertEncodesTo(
            listOf(1, null, 2),
            TomlValue.List(TomlValue.Integer(1), TomlValue.Integer(2))
        )
    }

    @Test
    fun `can encode empty lists`() {
        assertEncodesTo(
            emptyList<Nothing>(),
            TomlValue.List()
        )
    }

    @Test
    fun `can encode maps`() {
        assertEncodesTo(
            mapOf(
                "foo" to mapOf(
                    "bar" to 123,
                    "baz" to 0.5
                ),
                "bar" to emptyList<LocalDateTime>(),
                "baz" to setOf("hello", "world")
            ),
            TomlValue.Map(
                "foo" to TomlValue.Map(
                    "bar" to TomlValue.Integer(123),
                    "baz" to TomlValue.Double(0.5),
                ),
                "bar" to TomlValue.List(),
                "baz" to TomlValue.List(TomlValue.String("hello"), TomlValue.String("world"))
            )
        )
    }

    @Test
    // Kotlin's reflection apparently can't cope with nested classes with spaces in their path ¯\_(ツ)_/¯
    fun can_encode_data_classes() {
        data class Foo(val foo: Foo?, val bar: String, val baz: List<Int>, val foos: Collection<Foo>)
        assertEncodesTo(
            Foo(
                Foo(null, "inner", listOf(1, 2, 3), emptyList()),
                "outer",
                listOf(4, 2),
                listOf(
                    Foo(
                        null,
                        "outer list",
                        emptyList(),
                        mutableSetOf(
                            Foo(null, "nope", listOf(1), emptySet())
                        )
                    ),
                    Foo(null, "empty", emptyList(), emptyList())
                )
            ),
            TomlValue.Map(
                "foo" to TomlValue.Map(
                    "bar" to TomlValue.String("inner"),
                    "baz" to TomlValue.List(
                        TomlValue.Integer(1),
                        TomlValue.Integer(2),
                        TomlValue.Integer(3)
                    ),
                    "foos" to TomlValue.List()
                ),
                "bar" to TomlValue.String("outer"),
                "baz" to TomlValue.List(TomlValue.Integer(4), TomlValue.Integer(2)),
                "foos" to TomlValue.List(
                    TomlValue.Map(
                        "bar" to TomlValue.String("outer list"),
                        "baz" to TomlValue.List(),
                        "foos" to TomlValue.List(
                            TomlValue.Map(
                                "bar" to TomlValue.String("nope"),
                                "baz" to TomlValue.List(TomlValue.Integer(1)),
                                "foos" to TomlValue.List()
                            )
                        )
                    ),
                    TomlValue.Map(
                        "bar" to TomlValue.String("empty"),
                        "baz" to TomlValue.List(),
                        "foos" to TomlValue.List()
                    ),
                ),
            )
        )
    }

    @Test
    fun `can encode TomlValues`() {
        fun assertEncodesToSelf(value: TomlValue) {
            assertEncodesTo(value, value)
        }
        val clock = Clock.fixed(Instant.now(), ZoneId.of("UTC"))
        assertEncodesToSelf(TomlValue.List(TomlValue.Integer(100)))
        assertEncodesToSelf(TomlValue.Map("foo" to TomlValue.Integer(100)))
        assertEncodesToSelf(TomlValue.Integer(100))
        assertEncodesToSelf(TomlValue.Double(10.0))
        assertEncodesToSelf(TomlValue.String("hello"))
        assertEncodesToSelf(TomlValue.Bool(true))
        assertEncodesToSelf(TomlValue.LocalDate(LocalDate.now(clock)))
        assertEncodesToSelf(TomlValue.LocalTime(LocalTime.now(clock)))
        assertEncodesToSelf(TomlValue.LocalDateTime(LocalDateTime.now(clock)))
        assertEncodesToSelf(TomlValue.OffsetDateTime(OffsetDateTime.now(clock)))
    }

    @Test
    fun `null values in maps are ignored`() {
        assertEncodesTo(
            mapOf("foo" to null, "bar" to "not null"),
            TomlValue.Map("bar" to TomlValue.String("not null"))
        )
    }

    @Test
    fun `cant_encode_non-data_classes`() {
        class Foo(val bar: String)
        val fooValue = Foo("hello")
        val error = assertFailsWith<TomlException.EncodingError> {
            mapper.encode(fooValue)
        }
        assertEquals(error.sourceValue, fooValue)
        assertContains(error.message, fooValue.toString())
        assertNull(error.cause)
    }

    private fun assertEncodesTo(value: Any, tomlValue: TomlValue) {
        assertEquals(tomlValue, mapper.encode(value))
    }
}
