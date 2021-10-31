package cc.ekblad.toml.parser

import cc.ekblad.toml.RandomTest
import cc.ekblad.toml.TomlException
import cc.ekblad.toml.TomlValue
import org.junit.jupiter.api.assertThrows
import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

class FloatTests : RandomTest {
    @Test
    fun `can parse fractional notation`() {
        random.values(100) {
            Pair(
                nextInt(-1000, 1000),
                nextInt(0, 1000)
            )
        }.assertAll { (int, frac) ->
            val doubleFrac = if (int < 0) { -(frac/1000.0) } else { frac / 1000.0 }
            val valueToParse = String.format("%d.%03d", int, frac)
            assertParsesTo(TomlValue.Double(int.toDouble() + doubleFrac), valueToParse)
        }
    }

    @Test
    fun `can parse nan`() {
        listOf("nan", "+nan", "-nan").assertAll {
            assertParsesTo(TomlValue.Double(Double.NaN), it)
        }
    }

    @Test
    fun `can parse infinity`() {
        assertParsesTo(TomlValue.Double(Double.POSITIVE_INFINITY), "inf")
        assertParsesTo(TomlValue.Double(Double.POSITIVE_INFINITY), "+inf")
        assertParsesTo(TomlValue.Double(Double.NEGATIVE_INFINITY), "-inf")
    }

    @Test
    fun `can parse exponent notation`() {
        random.values(100) {
            Pair(
                nextInt(-1000, 1000),
                nextInt(-10, 11)
            )
        }.assertAll { (nom, exp) ->
            val expected = nom * 10.0.pow(exp)
            assertParsesWithin(expected, "${nom}e$exp")
            assertParsesWithin(expected, "${nom}E$exp")
        }
    }

    @Test
    fun `can parse fractional exponent notation`() {
        random.values(100) {
            Pair(
                nextDouble(-1000.0, 1000.0),
                nextInt(-10, 11)
            )
        }.assertAll { (nom, exp) ->
            val expected = nom * 10.0.pow(exp)
            assertParsesWithin(expected, "${nom}e$exp")
            assertParsesWithin(expected, "${nom}E$exp")
        }
    }

    @Test
    fun `can parse floats with underscore separators`() {
        listOf(
            "1_2e3" to 12e3,
            "1_2e3_4" to 12e34,
            "1_1.2_2e3_4" to 11.22e34,
            "1.9_1" to 1.91,
            "1e0_9" to 1e9
        ).assertAll { (string, expected) ->
            assertParsesWithin(expected, string)
        }
    }

    @Test
    fun `throws on bad float`() {
        listOf("1e3.4", "e1", "1e", ".4", "3.", "1.9_", "1._9", "1e_9").assertAll {
            assertThrows<TomlException.ParseError> { TomlValue.from("foo = $it") }
        }
    }

    private fun assertParsesWithin(expected: Double, valueToParse: String) {
        val value = ((TomlValue.from("test = $valueToParse") as TomlValue.Map).properties["test"] as TomlValue.Double).value
        assertTrue(abs(value - expected) < 0.0001)
    }
}
