package cc.ekblad.toml.converter

import cc.ekblad.toml.StringTest
import cc.ekblad.toml.TomlException
import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.convert
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.SortedMap
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltinConverterTests : StringTest {
    private data class Person(
        val name: String,
        val age: Int,
        val height: Double,
        val mom: Person?,
        val dad: Person?,
    )

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `throws on conversion to invalid type`() {
        listOf(
            typeOf<Int>() to TomlValue.String("hello"),
            typeOf<String>() to TomlValue.Integer(1),
            typeOf<String>() to TomlValue.Bool(false),
            typeOf<String>() to TomlValue.Double(1.0),
            typeOf<String>() to TomlValue.List(),
            typeOf<String>() to TomlValue.LocalTime(LocalTime.of(1, 1)),
            typeOf<String>() to TomlValue.LocalDate(LocalDate.of(2001, 1, 1)),
            typeOf<String>() to TomlValue.LocalDateTime(LocalDateTime.of(2001, 1, 1, 1, 1)),
            typeOf<String>() to TomlValue.OffsetDateTime(OffsetDateTime.of(2001, 1, 1, 1, 1, 1, 1, ZoneOffset.UTC)),
            typeOf<List<String>>() to TomlValue.String("hello"),
            typeOf<List<String>>() to TomlValue.Map("hello" to TomlValue.String("hello")),
            typeOf<Map<Int, String>>() to TomlValue.Map("hello" to TomlValue.String("hello")),
        ).assertAll { (kType, tomlValue) ->
            assertThrows<TomlException.ConversionError> { tomlValue.convert(kType) }
        }
    }

    @Test
    fun `throws on missing non-nullable key when converting to data class`() {
        assertThrows<TomlException.ConversionError> { TomlValue.Map().convert<Person>() }
    }

    @Test
    fun `can convert objects to data classes`() {
        val expected = Person(
            "Luke",
            17,
            1.77,
            null,
            Person("Anakin", 39, 2.11, null, null)
        )
        val toml = """
            name = 'Luke'
            age = 17
            height = 1.77
            
            [dad]
            name = "Anakin"
            age = 39
            height = 2.11
        """.trimIndent()

        assertEquals(
            expected,
            TomlValue.from(toml).convert()
        )
    }

    @Test
    fun `can convert objects to local data classes`() {
        data class Foo(val bar: String)
        val expected = Foo("hello")
        val toml = """bar = "hello""""
        assertEquals(expected, TomlValue.from(toml).convert())
    }

    @Test
    fun `can convert objects to non-data classes`() {
        class Foo(val bar: String)
        val expected = Foo("hello")
        val toml = """bar = "hello""""
        assertEquals(expected.bar, TomlValue.from(toml).convert<Foo>().bar)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `conversion error contains target type and the value that couldn't be converted`() {
        data class Foo(val bar: String)
        val toml = "bar = 123"
        val exception = assertThrows<TomlException.ConversionError> { TomlValue.from(toml).convert<Foo>() }
        assertEquals(TomlValue.Integer(123), exception.sourceValue)
        assertEquals(typeOf<String>(), exception.targetType)
        assertContains(exception.message, TomlValue.Integer(123).toString())
        assertContains(exception.message, "String")
    }

    @Test
    fun `can convert objects to public data classes`() {
        val expected = PublicFoo("hello")
        val toml = """bar = "hello""""
        assertEquals(expected, TomlValue.from(toml).convert())
    }
    data class PublicFoo(val bar: String)

    @Test
    fun `can convert objects to simple maps`() {
        val expected = mapOf("foo" to 1L, "bar" to 2L)
        val toml = """
            foo = 1
            bar = 2
        """.trimIndent()
        assertEquals(
            expected,
            TomlValue.from(toml).convert()
        )
    }

    @Test
    fun `can convert objects to data classes with TomlValues`() {
        data class Foo(val bars: List<TomlValue>)
        val expected = Foo(listOf(TomlValue.String("hello"), TomlValue.Integer(123)))
        val toml = """bars = ["hello", 123]"""
        assertEquals(expected, TomlValue.from(toml).convert())
    }

    @Test
    fun `can convert TomlValues`() {
        assertEquals(TomlValue.Integer(123), TomlValue.Integer(123).convert())
        assertEquals(TomlValue.String("123"), TomlValue.String("123").convert())
    }

    @Test
    fun `can't use convert to cast from one type of TomlValue to another`() {
        val toml = """bar = "hello""""
        assertThrows<TomlException.ConversionError> {
            TomlValue.from(toml).convert<Map<String, TomlValue.Integer>>()
        }
    }

    @Test
    fun `can convert objects to nested maps`() {
        val expected = mapOf("foo" to 1L, "bar" to mapOf("baz" to "hello"))
        val toml = """
            foo = 1
            bar = { baz = "hello" }
        """.trimIndent()
        assertEquals(
            expected,
            TomlValue.from(toml).convert()
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `can convert maps to special maps`() {
        listOf(
            typeOf<Map<String, String>>() to mapOf("hello" to "hello"),
            typeOf<MutableMap<String, String>>() to mutableMapOf("hello" to "hello"),
            typeOf<SortedMap<String, String>>() to sortedMapOf("hello" to "hello")
        ).assertAll { (kType, it) ->
            assertEquals(
                it,
                TomlValue.Map("hello" to TomlValue.String("hello")).convert(kType)
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `can convert lists`() {
        listOf(
            typeOf<Any>() to listOf(1L, 2L),
            typeOf<List<Long>>() to listOf(1L, 2L),
            typeOf<MutableList<Int>>() to mutableListOf(1, 2),
            typeOf<Collection<Double>>() to listOf(1.0, 2.0),
            typeOf<Iterable<BigInteger>>() to listOf(BigInteger.valueOf(1), BigInteger.valueOf(2)).asIterable()
        ).assertAll { (kType, it) ->
            assertEquals(
                it,
                TomlValue.List(
                    TomlValue.Integer(1),
                    TomlValue.Integer(2),
                ).convert(kType)
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `can convert lists to type-erased generics`() {
        listOf(
            typeOf<List<*>>() to listOf(1L, 2L),
            typeOf<MutableList<*>>() to mutableListOf(1L, 2L),
            typeOf<Collection<*>>() to listOf(1L, 2L),
            typeOf<Iterable<*>>() to listOf(1L, 2L).asIterable()
        ).assertAll { (kType, it) ->
            assertEquals(
                it,
                TomlValue.List(
                    TomlValue.Integer(1),
                    TomlValue.Integer(2),
                ).convert(kType)
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `can convert maps to type-erased generics`() {
        listOf(
            typeOf<Map<*, *>>() to mapOf("hello" to "hello"),
            typeOf<MutableMap<*, *>>() to mutableMapOf("hello" to "hello"),
            typeOf<SortedMap<*, *>>() to sortedMapOf("hello" to "hello")
        ).assertAll { (kType, it) ->
            assertEquals(
                it,
                TomlValue.Map("hello" to TomlValue.String("hello")).convert(kType)
            )
        }
    }

    @Test
    fun `can convert lists with elements that need conversion`() {
        assertEquals(
            listOf(1, 2),
            TomlValue.List(
                TomlValue.Integer(1),
                TomlValue.Integer(2),
            ).convert()
        )
    }

    @Test
    fun `can convert nested lists`() {
        assertEquals(
            listOf(listOf("asd", "fgh"), emptyList(), listOf("jkl")),
            TomlValue.List(
                TomlValue.List(
                    TomlValue.String("asd"),
                    TomlValue.String("fgh"),
                ),
                TomlValue.List(),
                TomlValue.List(
                    TomlValue.String("jkl"),
                ),
            ).convert()
        )
    }

    @Test
    fun `all types convert to the correct defaults when converting to Any`() {
        listOf(
            TomlValue.Bool(true) to true,
            TomlValue.Integer(1) to 1L,
            TomlValue.Double(1.0) to 1.0,
            TomlValue.String("hello") to "hello",
            TomlValue.LocalTime(LocalTime.of(10, 10)) to LocalTime.of(10, 10),
            TomlValue.LocalDate(LocalDate.of(2011, 11, 11)) to LocalDate.of(2011, 11, 11),
            TomlValue.LocalDateTime(
                LocalDateTime.of(
                    LocalDate.of(2011, 11, 11),
                    LocalTime.of(11, 11)
                )
            ) to LocalDateTime.of(LocalDate.of(2011, 11, 11), LocalTime.of(11, 11)),
            TomlValue.OffsetDateTime(
                OffsetDateTime.of(
                    LocalDateTime.of(
                        LocalDate.of(2011, 11, 11),
                        LocalTime.of(11, 11)
                    ),
                    ZoneOffset.UTC
                )
            ) to OffsetDateTime.of(LocalDateTime.of(LocalDate.of(2011, 11, 11), LocalTime.of(11, 11)), ZoneOffset.UTC),
            TomlValue.List(TomlValue.Bool(true)) to listOf(true),
            TomlValue.Map("hello" to TomlValue.String("hello")) to mapOf("hello" to "hello"),
        ).assertAll { (toml, kotlin) ->
            assertEquals(toml.convert(), kotlin)
        }
    }

    @Test
    fun `can convert booleans`() {
        assertEquals(TomlValue.Bool(true).convert(), true)
        assertEquals(TomlValue.Bool(false).convert(), false)
    }

    @Test
    fun `can convert strings`() {
        random.values(100) { nextSequence(alphabet) }.assertAll {
            assertEquals(TomlValue.String(it).convert(), it)
        }
    }

    @Test
    fun `can convert localdates`() {
        random.values(100) { nextLocalDate() }.assertAll {
            assertEquals(TomlValue.LocalDate(it).convert(), it)
        }
    }

    @Test
    fun `can convert localtimes`() {
        random.values(100) { nextLocalTime() }.assertAll {
            assertEquals(TomlValue.LocalTime(it).convert(), it)
        }
    }

    @Test
    fun `can convert localdatetimes`() {
        random.values(100) { nextLocalDateTime() }.assertAll {
            assertEquals(TomlValue.LocalDateTime(it).convert(), it)
        }
    }

    @Test
    fun `can convert offsetdatetimes`() {
        random.values(100) { nextOffsetDateTime() }.assertAll {
            assertEquals(TomlValue.OffsetDateTime(it).convert(), it)
        }
    }

    @Test
    fun `can convert integers`() {
        random.values(100) { nextLong().withMaxDigits(5) }.assertAll {
            assertEquals(TomlValue.Integer(it).convert(), it.toInt())
            assertEquals(TomlValue.Integer(it).convert(), it)
            assertEquals(TomlValue.Integer(it).convert(), it.toDouble())
            assertEquals(TomlValue.Integer(it).convert(), it.toFloat())
            assertEquals(TomlValue.Integer(it).convert(), BigInteger.valueOf(it))
            assertEquals(TomlValue.Integer(it).convert(), BigDecimal(it))
        }
    }

    @Test
    fun `can convert doubles`() {
        random.values(100) { nextFloat() }.assertAll {
            assertEquals(TomlValue.Double(it.toDouble()).convert(), it.toDouble())
            assertEquals(TomlValue.Double(it.toDouble()).convert(), it)
            assertTrue(
                TomlValue.Double(it.toDouble())
                    .convert<BigDecimal>()
                    .minus(BigDecimal(it.toDouble()))
                    .abs() < BigDecimal(0.00001)
            )
        }
    }
}
