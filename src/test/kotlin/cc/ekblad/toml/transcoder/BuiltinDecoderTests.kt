package cc.ekblad.toml.transcoder

import cc.ekblad.toml.StringTest
import cc.ekblad.toml.TomlException
import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.serialization.from
import cc.ekblad.toml.transcoding.tomlMapper
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.SortedMap
import kotlin.reflect.full.createType
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BuiltinDecoderTests : StringTest {
    private val mapper = tomlMapper { }
    private data class Person(
        val name: String,
        val age: Int,
        val height: Double,
        val mom: Person?,
        val dad: Person?,
    )

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `throws on decode to invalid type`() {
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
            assertFailsWith<TomlException.DecodingError> { mapper.decode(kType, tomlValue) }
        }
    }

    @Test
    fun `throws on missing non-nullable key when decoding to data class`() {
        val error = assertFailsWith<TomlException.DecodingError> { mapper.decode<Person>(TomlValue.Map()) }
        assertNotNull(error.reason)
        assertNull(error.cause)
        assertContains(error.reason!!, "no value found for non-nullable parameter")
        assertContains(error.message, error.reason!!)
    }

    @Test
    fun `can decode objects to data classes`() {
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
            mapper.decode(TomlValue.from(toml))
        )
    }

    @Test
    fun `can decode objects to local data classes`() {
        data class Foo(val bar: String)
        val expected = Foo("hello")
        val toml = """bar = "hello""""
        assertEquals(expected, mapper.decode(TomlValue.from(toml)))
    }

    @Test
    fun `can decode objects to non-data classes`() {
        class Foo(val bar: String)
        val expected = Foo("hello")
        val toml = """bar = "hello""""
        assertEquals(expected.bar, mapper.decode<Foo>(TomlValue.from(toml)).bar)
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `decoding error contains target type and the value that couldn't be decoded`() {
        data class Foo(val bar: String)
        val toml = "bar = 123"
        val exception = assertFailsWith<TomlException.DecodingError> { mapper.decode<Foo>(TomlValue.from(toml)) }
        assertEquals(TomlValue.Integer(123), exception.sourceValue)
        assertEquals(typeOf<String>(), exception.targetType)
        assertContains(exception.message, TomlValue.Integer(123).toString())
        assertContains(exception.message, "String")
    }

    @Test
    fun `can decode objects to public data classes`() {
        val expected = PublicFoo("hello")
        val toml = """bar = "hello""""
        assertEquals(expected, mapper.decode(TomlValue.from(toml)))
    }
    data class PublicFoo(val bar: String)

    @Test
    fun `can decode objects to simple maps`() {
        val expected = mapOf("foo" to 1L, "bar" to 2L)
        val toml = """
            foo = 1
            bar = 2
        """.trimIndent()
        assertEquals(
            expected,
            mapper.decode(TomlValue.from(toml))
        )
    }

    @Test
    fun `can decode objects to data classes with TomlValues`() {
        data class Foo(val bars: List<TomlValue>)
        val expected = Foo(listOf(TomlValue.String("hello"), TomlValue.Integer(123)))
        val toml = """bars = ["hello", 123]"""
        assertEquals(expected, mapper.decode(TomlValue.from(toml)))
    }

    @Test
    fun `can decode TomlValues`() {
        assertEquals(TomlValue.Integer(123), mapper.decode(TomlValue.Integer(123)))
        assertEquals(TomlValue.String("123"), mapper.decode(TomlValue.String("123")))
    }

    @Test
    fun `can't use decode to cast from one type of TomlValue to another`() {
        val toml = """bar = "hello""""
        assertFailsWith<TomlException.DecodingError> {
            mapper.decode<Map<String, TomlValue.Integer>>(TomlValue.from(toml))
        }
    }

    @Test
    fun `can decode objects to nested maps`() {
        val expected = mapOf("foo" to 1L, "bar" to mapOf("baz" to "hello"))
        val toml = """
            foo = 1
            bar = { baz = "hello" }
        """.trimIndent()
        assertEquals(
            expected,
            mapper.decode(TomlValue.from(toml))
        )
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `can decode maps to special maps`() {
        listOf(
            typeOf<Map<String, String>>() to mapOf("hello" to "hello"),
            typeOf<MutableMap<String, String>>() to mutableMapOf("hello" to "hello"),
            typeOf<SortedMap<String, String>>() to sortedMapOf("hello" to "hello")
        ).assertAll { (kType, it) ->
            assertEquals(
                it,
                mapper.decode(kType, TomlValue.Map("hello" to TomlValue.String("hello")))
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `can decode lists`() {
        listOf(
            typeOf<Any>() to listOf(1L, 2L),
            typeOf<List<Long>>() to listOf(1L, 2L),
            typeOf<MutableList<Int>>() to mutableListOf(1, 2),
            typeOf<Collection<Double>>() to listOf(1.0, 2.0),
            typeOf<Iterable<BigInteger>>() to listOf(BigInteger.valueOf(1), BigInteger.valueOf(2)).asIterable()
        ).assertAll { (kType, it) ->
            assertEquals(
                it,
                mapper.decode(
                    kType,
                    TomlValue.List(
                        TomlValue.Integer(1),
                        TomlValue.Integer(2),
                    )
                )
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `can decode lists to type-erased generics`() {
        listOf(
            typeOf<List<*>>() to listOf(1L, 2L),
            typeOf<MutableList<*>>() to mutableListOf(1L, 2L),
            typeOf<Collection<*>>() to listOf(1L, 2L),
            typeOf<Iterable<*>>() to listOf(1L, 2L).asIterable()
        ).assertAll { (kType, it) ->
            assertEquals(
                it,
                mapper.decode(
                    kType,
                    TomlValue.List(
                        TomlValue.Integer(1),
                        TomlValue.Integer(2),
                    )
                )
            )
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun `can decode maps to type-erased generics`() {
        listOf(
            typeOf<Map<*, *>>() to mapOf("hello" to "hello"),
            typeOf<MutableMap<*, *>>() to mutableMapOf("hello" to "hello"),
            typeOf<SortedMap<*, *>>() to sortedMapOf("hello" to "hello")
        ).assertAll { (kType, it) ->
            assertEquals(
                it,
                mapper.decode(kType, TomlValue.Map("hello" to TomlValue.String("hello")))
            )
        }
    }

    @Test
    fun `can decode lists with elements that need decoding`() {
        assertEquals(
            listOf(1, 2),
            mapper.decode(
                TomlValue.List(
                    TomlValue.Integer(1),
                    TomlValue.Integer(2),
                )
            )
        )
    }

    @Test
    fun `can decode nested lists`() {
        assertEquals(
            listOf(listOf("asd", "fgh"), emptyList(), listOf("jkl")),
            mapper.decode(
                TomlValue.List(
                    TomlValue.List(
                        TomlValue.String("asd"),
                        TomlValue.String("fgh"),
                    ),
                    TomlValue.List(),
                    TomlValue.List(
                        TomlValue.String("jkl"),
                    ),
                )
            )
        )
    }

    @Test
    fun `all types decode to the correct defaults when decoding to Any`() {
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
            assertEquals(mapper.decode(toml), kotlin)
        }
    }

    @Test
    fun `can decode to Set`() {
        assertEquals(emptySet<String>(), mapper.decode(TomlValue.List()))
        assertEquals(mutableSetOf<String>(), mapper.decode(TomlValue.List()))
        assertEquals(emptySet<Any>(), mapper.decode<Set<*>>(TomlValue.List()))
        assertEquals(mutableSetOf<Any>(), mapper.decode<MutableSet<*>>(TomlValue.List()))
    }

    @Test
    fun `can decode booleans`() {
        assertEquals(mapper.decode(TomlValue.Bool(true)), true)
        assertEquals(mapper.decode(TomlValue.Bool(false)), false)
    }

    @Test
    fun `can decode strings`() {
        random.values(100) { nextSequence(alphabet) }.assertAll {
            assertEquals(mapper.decode(TomlValue.String(it)), it)
        }
    }

    @Test
    fun `can decode localdates`() {
        random.values(100) { nextLocalDate() }.assertAll {
            assertEquals(mapper.decode(TomlValue.LocalDate(it)), it)
        }
    }

    @Test
    fun `can decode localtimes`() {
        random.values(100) { nextLocalTime() }.assertAll {
            assertEquals(mapper.decode(TomlValue.LocalTime(it)), it)
        }
    }

    @Test
    fun `can decode localdatetimes`() {
        random.values(100) { nextLocalDateTime() }.assertAll {
            assertEquals(mapper.decode(TomlValue.LocalDateTime(it)), it)
        }
    }

    @Test
    fun `can decode offsetdatetimes`() {
        random.values(100) { nextOffsetDateTime() }.assertAll {
            assertEquals(mapper.decode(TomlValue.OffsetDateTime(it)), it)
        }
    }

    @Test
    fun `can decode integers`() {
        random.values(100) { nextLong().withMaxDigits(5) }.assertAll {
            assertEquals(mapper.decode(TomlValue.Integer(it)), it.toInt())
            assertEquals(mapper.decode(TomlValue.Integer(it)), it)
            assertEquals(mapper.decode(TomlValue.Integer(it)), it.toDouble())
            assertEquals(mapper.decode(TomlValue.Integer(it)), it.toFloat())
            assertEquals(mapper.decode(TomlValue.Integer(it)), BigInteger.valueOf(it))
            assertEquals(mapper.decode(TomlValue.Integer(it)), BigDecimal(it))
        }
    }

    @Test
    fun `can decode to self`() {
        val values = listOf(
            TomlValue.Integer(123),
            TomlValue.Double(1.23),
            TomlValue.Bool(true),
            TomlValue.String("hello"),
            TomlValue.LocalDate(LocalDate.of(2020, 1, 2)),
            TomlValue.LocalTime(LocalTime.of(23, 12, 43)),
            TomlValue.LocalDateTime(LocalDateTime.of(2020, 1, 2, 23, 12, 43)),
            TomlValue.OffsetDateTime(OffsetDateTime.of(2020, 1, 2, 23, 12, 43, 0, ZoneOffset.UTC)),
            TomlValue.Map("hello" to TomlValue.Bool(false)),
            TomlValue.List(TomlValue.Bool(true))
        )
        values.assertAll {
            assertEquals(it, mapper.decode(typeOf<TomlValue>(), it))
            assertEquals(it, mapper.decode(it::class.createType(emptyList()), it))
        }
    }

    @Test
    fun `can decode doubles`() {
        random.values(100) { nextFloat() }.assertAll {
            assertEquals(mapper.decode(TomlValue.Double(it.toDouble())), it.toDouble())
            assertEquals(mapper.decode(TomlValue.Double(it.toDouble())), it)
            assertTrue(
                TomlValue.Double(it.toDouble())
                    .let { mapper.decode<BigDecimal>(it) }
                    .minus(BigDecimal(it.toDouble()))
                    .abs() < BigDecimal(0.00001)
            )
        }
    }
}
