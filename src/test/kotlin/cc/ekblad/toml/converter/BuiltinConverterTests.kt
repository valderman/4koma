package cc.ekblad.toml.converter

import cc.ekblad.toml.StringTest
import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.convert
import java.math.BigDecimal
import java.math.BigInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BuiltinConverterTests : StringTest {
    data class Person(
        val name: String,
        val age: Int,
        val height: Double,
        val mom: Person?,
        val dad: Person?,
    )

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

    @Test
    fun `can convert lists`() {
        assertEquals(
            listOf(1L, 2L),
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