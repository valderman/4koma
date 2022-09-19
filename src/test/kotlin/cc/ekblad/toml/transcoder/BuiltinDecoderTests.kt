package cc.ekblad.toml.transcoder

import cc.ekblad.toml.StringTest
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.serialization.from
import cc.ekblad.toml.tomlMapper
import cc.ekblad.toml.util.InternalAPI
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.SortedMap
import kotlin.reflect.full.createType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalStdlibApi::class, InternalAPI::class)
class BuiltinDecoderTests : StringTest {
    private val mapper = tomlMapper { }
    private data class Person(
        val name: String,
        val age: Int,
        val height: Double,
        val mom: Person?,
        val dad: Person?,
    )

    enum class PublicEnum { Foo, Bar }
    private enum class PrivateEnum { Foo, Bar }
    private enum class EnumWithArgs(val someValue: String) { Foo("hello"), Bar("goodbye") }

    @Test
    fun `throws on decode to invalid type`() {
        listOf(
            typeOf<Int>() to TomlValue.String("hello"),
            typeOf<String>() to TomlValue.Integer(1),
            typeOf<String>() to TomlValue.Bool(false),
            typeOf<String>() to TomlValue.Double(1.0),
            typeOf<String>() to TomlValue.LocalTime(LocalTime.of(1, 1)),
            typeOf<String>() to TomlValue.LocalDate(LocalDate.of(2001, 1, 1)),
            typeOf<String>() to TomlValue.LocalDateTime(LocalDateTime.of(2001, 1, 1, 1, 1)),
            typeOf<String>() to TomlValue.OffsetDateTime(OffsetDateTime.of(2001, 1, 1, 1, 1, 1, 1, ZoneOffset.UTC)),
            typeOf<List<String>>() to TomlValue.String("hello"),
        ).assertAll { (kType, tomlValue) ->
            assertFailsWith<TomlException.DecodingError.NoSuchDecoder> { mapper.decode(kType, tomlValue) }
        }
        assertFailsWith<TomlException.DecodingError.IllegalListTargetType> {
            mapper.decode(typeOf<String>(), TomlValue.List())
        }
        assertFailsWith<TomlException.DecodingError.IllegalMapTargetType> {
            mapper.decode(typeOf<List<String>>(), TomlValue.Map("hello" to TomlValue.String("hello")))
        }
        assertFailsWith<TomlException.DecodingError.IllegalMapKeyType> {
            mapper.decode(typeOf<Map<Int, String>>(), TomlValue.Map("hello" to TomlValue.String("hello")))
        }
    }

    @Test
    fun `throws on missing non-nullable key when decoding to data class`() {
        val error = assertFailsWith<TomlException.DecodingError.MissingNonNullableValue> {
            mapper.decode<Person>(TomlValue.Map())
        }
        assertNotNull(error.reason)
        assertContains(error.reason, "No value found for non-nullable parameter")
        assertEquals(error.parameter, Person::class.primaryConstructor?.parameters?.first())
        assertContains(error.message, error.reason)
    }

    @Test
    fun `can decode strings to enums`() {
        assertEquals(PublicEnum.Foo, mapper.decode(TomlValue.String("Foo")))
        assertEquals(PublicEnum.Bar, mapper.decode(TomlValue.String("Bar")))
        assertEquals(PrivateEnum.Foo, mapper.decode(TomlValue.String("Foo")))
        assertEquals(PrivateEnum.Bar, mapper.decode(TomlValue.String("Bar")))
        assertEquals(EnumWithArgs.Foo, mapper.decode(TomlValue.String("Foo")))
        assertEquals(EnumWithArgs.Bar, mapper.decode(TomlValue.String("Bar")))
    }

    @Test
    fun `decoding string to nonexistent enum constructor throws`() {
        val error = assertFailsWith<TomlException.DecodingError.InvalidEnumValue> {
            mapper.decode<PublicEnum>(TomlValue.String("Not Foo"))
        }
        assertNotNull(error.reason)
        assertNull(error.cause)
        assertContains(error.reason, "not a constructor of enum class")
        assertEquals(TomlValue.String("Not Foo"), error.sourceValue)
        assertEquals(typeOf<PublicEnum>(), error.targetType)
    }

    @Test
    fun `enum decoding is case sensitive`() {
        assertFailsWith<TomlException.DecodingError.InvalidEnumValue> {
            mapper.decode<PublicEnum>(TomlValue.String("foo"))
        }
    }

    @Test
    fun `enum decoding compares the entire strings`() {
        assertFailsWith<TomlException.DecodingError.InvalidEnumValue> {
            mapper.decode<PublicEnum>(TomlValue.String("Fooo"))
        }
        assertFailsWith<TomlException.DecodingError.InvalidEnumValue> {
            mapper.decode<PublicEnum>(TomlValue.String("oFoo"))
        }
        assertFailsWith<TomlException.DecodingError.InvalidEnumValue> {
            mapper.decode<PublicEnum>(TomlValue.String("oFooo"))
        }
        assertFailsWith<TomlException.DecodingError.InvalidEnumValue> {
            mapper.decode<PublicEnum>(TomlValue.String("oo"))
        }
        assertFailsWith<TomlException.DecodingError.InvalidEnumValue> {
            mapper.decode<PublicEnum>(TomlValue.String("Fo"))
        }
        assertFailsWith<TomlException.DecodingError.InvalidEnumValue> {
            mapper.decode<PublicEnum>(TomlValue.String("o"))
        }
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
        assertFailsWith<TomlException.DecodingError.NoSuchDecoder> {
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

    @Test
    fun `can decode atomic values with default`() {
        assertEquals(
            "correct",
            mapper.decodeWithDefaults("wrong", TomlValue.String("correct"))
        )
        assertEquals(
            listOf("correct"),
            mapper.decodeWithDefaults(listOf("wrong"), TomlValue.List(TomlValue.String("correct")))
        )
        assertEquals(
            listOf("correct"),
            mapper.decodeWithDefaults(emptyList(), TomlValue.List(TomlValue.String("correct")))
        )
        assertEquals(
            emptyList(),
            mapper.decodeWithDefaults(listOf("wrong"), TomlValue.List())
        )
    }

    @Test
    fun `can decode null values with default`() {
        assertEquals(
            "correct",
            mapper.decodeWithDefaults<String?>(null, TomlValue.String("correct"))
        )

        val nullMapper = tomlMapper {
            decoder<TomlValue.String, String> { _ -> null }
        }
        assertEquals(
            null,
            nullMapper.decodeWithDefaults<String?>("wrong", TomlValue.String("ignored"))
        )
    }

    @Test
    fun `defaults don't come into play for fully specified TOML`() {
        data class Foo(val a: Int, val b: String)
        val toml = TomlValue.Map(
            "a" to TomlValue.Integer(123),
            "b" to TomlValue.String("hej")
        )
        assertEquals(
            Foo(123, "hej"),
            mapper.decodeWithDefaults(Foo(0, ""), toml)
        )
    }

    @Test
    fun `defaults work properly in flat maps`() {
        data class Foo(val a: Int, val b: String)
        val toml = TomlValue.Map(
            "a" to TomlValue.Integer(123),
        )
        assertEquals(
            Foo(123, "defaulted"),
            mapper.decodeWithDefaults(Foo(0, "defaulted"), toml)
        )
        assertEquals(
            Foo(0, "defaulted"),
            mapper.decodeWithDefaults(Foo(0, "defaulted"), TomlValue.Map())
        )
    }

    @Test
    fun `defaults work properly in nested maps`() {
        data class Foo(val a: Int, val b: String)
        data class Bar(val x: Foo, val y: Int)
        val toml = TomlValue.Map(
            "x" to TomlValue.Map(
                "a" to TomlValue.Integer(123)
            ),
            "y" to TomlValue.Integer(321),
        )
        assertEquals(
            Bar(Foo(123, "defaulted"), 321),
            mapper.decodeWithDefaults(Bar(Foo(0, "defaulted"), 0), toml)
        )
    }

    @Test
    fun `defaults work properly in nested maps when a whole sub-map is missing or empty`() {
        data class Foo(val a: Int, val b: String)
        data class Bar(val x: Foo, val y: Int)
        val tomlWithoutFoo = TomlValue.Map(
            "y" to TomlValue.Integer(321),
        )
        val tomlWithEmptyFoo = TomlValue.Map(
            "x" to TomlValue.Map(),
            "y" to TomlValue.Integer(321),
        )
        assertEquals(
            Bar(Foo(0, "defaulted"), 321),
            mapper.decodeWithDefaults(Bar(Foo(0, "defaulted"), 0), tomlWithoutFoo)
        )
        assertEquals(
            Bar(Foo(0, "defaulted"), 321),
            mapper.decodeWithDefaults(Bar(Foo(0, "defaulted"), 0), tomlWithEmptyFoo)
        )
    }

    @Test
    fun `can decode to null in data class`() {
        data class Foo(val i: Int?)
        val nullMapper = tomlMapper {
            decoder<TomlValue.Integer, Int> { _, _ -> null }
        }

        assertEquals(
            nullMapper.decode(
                TomlValue.Map(
                    "i" to TomlValue.Integer(123)
                )
            ),
            Foo(null)
        )
    }

    @Test
    fun `cannot decode to null for non-nullable field in data class`() {
        data class Foo(val i: Int)
        val nullMapper = tomlMapper {
            decoder<TomlValue.Integer, Int> { _, _ -> null }
        }

        assertFails {
            nullMapper.decode<Foo>(
                TomlValue.Map(
                    "i" to TomlValue.Integer(123)
                )
            )
        }
    }

    @Test
    fun `optional arguments work properly in flat maps`() {
        data class Foo(val a: Int = 1, val b: String = "default")
        val toml = TomlValue.Map(
            "a" to TomlValue.Integer(123),
        )
        assertEquals(
            Foo(123, "default"),
            mapper.decode(toml)
        )
        assertEquals(
            Foo(1, "default"),
            mapper.decode(TomlValue.Map())
        )
    }

    @Test
    fun `optional arguments work properly in nested maps`() {
        data class Foo(val a: Int = 0, val b: String = "defaulted")
        data class Bar(val x: Foo = Foo(), val y: Int = 0)
        val toml = TomlValue.Map(
            "x" to TomlValue.Map(
                "a" to TomlValue.Integer(123)
            ),
            "y" to TomlValue.Integer(321),
        )
        assertEquals(
            Bar(Foo(123, "defaulted"), 321),
            mapper.decode(toml)
        )
    }

    @Test
    fun `defaults take priority over optional arguments`() {
        data class Foo(val a: Int = 1, val b: String = "default")
        val toml = TomlValue.Map(
            "a" to TomlValue.Integer(123),
        )
        assertEquals(
            Foo(123, "defaulted"),
            mapper.decodeWithDefaults(Foo(0, "defaulted"), toml)
        )
        assertEquals(
            Foo(0, "defaulted"),
            mapper.decodeWithDefaults(Foo(0, "defaulted"), TomlValue.Map())
        )
    }

    @Test
    fun `missing and null values are differentiated between`() {
        data class Foo(val a: Int? = 1)
        val toml = TomlValue.Map(
            "a" to TomlValue.Integer(123),
        )
        val nullMapper = tomlMapper {
            decoder<TomlValue.Integer, Int> { _, _ -> null }
        }
        assertEquals(
            Foo(null),
            nullMapper.decode(toml)
        )
        assertEquals(
            Foo(1),
            nullMapper.decode(TomlValue.Map())
        )
    }

    @Test
    fun can_decode_to_parameterized_data_class() {
        data class Foo<T>(val x: T, val y: Foo<T>?)
        assertEquals(
            Foo(1, Foo(2, Foo(3, null))),
            mapper.decode(
                TomlValue.Map(
                    "x" to TomlValue.Integer(1),
                    "y" to TomlValue.Map(
                        "x" to TomlValue.Integer(2),
                        "y" to TomlValue.Map(
                            "x" to TomlValue.Integer(3)
                        )
                    )
                )
            )
        )
    }

    @Test
    fun can_decode_to_data_class_with_multiple_type_parameters() {
        data class Foo<T, U>(val x: U, val y: T, val z: List<Foo<U, T>>)
        assertEquals(
            Foo(
                "one",
                1,
                listOf(
                    Foo(2, "two", emptyList()),
                    Foo(3, "three", listOf(Foo("four", 4, emptyList())))
                )
            ),
            mapper.decode(
                TomlValue.Map(
                    "x" to TomlValue.String("one"),
                    "y" to TomlValue.Integer(1),
                    "z" to TomlValue.List(
                        TomlValue.Map(
                            "x" to TomlValue.Integer(2),
                            "y" to TomlValue.String("two"),
                            "z" to TomlValue.List()
                        ),
                        TomlValue.Map(
                            "x" to TomlValue.Integer(3),
                            "y" to TomlValue.String("three"),
                            "z" to TomlValue.List(
                                TomlValue.Map(
                                    "x" to TomlValue.String("four"),
                                    "y" to TomlValue.Integer(4),
                                    "z" to TomlValue.List()
                                )
                            )
                        )
                    )
                )
            )
        )
    }

    @Test
    fun `can decode to lazy value`() {
        data class Foo<T>(val x: Lazy<T>)
        data class Bar(val int: Int?, val list: List<String>)
        assertEquals(
            "hello",
            mapper.decode<Foo<String>>(TomlValue.Map("x" to TomlValue.String("hello"))).x.value
        )
        assertEquals(
            PublicEnum.Bar,
            mapper.decode<Foo<PublicEnum>>(TomlValue.Map("x" to TomlValue.String("Bar"))).x.value
        )
        assertEquals(
            Bar(null, listOf("hello", "hi")),
            mapper.decode<Foo<Bar>>(
                TomlValue.Map(
                    "x" to TomlValue.Map(
                        "list" to TomlValue.List(TomlValue.String("hello"), TomlValue.String("hi"))
                    )
                )
            ).x.value
        )
    }

    @Test
    fun `decoding to lazy values is strict`() {
        data class Foo(val x: Lazy<Int>)
        assertFailsWith<TomlException.DecodingError.NoSuchDecoder> {
            mapper.decode<Foo>(TomlValue.Map("x" to TomlValue.String("hello")))
        }
    }
}
