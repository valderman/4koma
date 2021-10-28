package cc.ekblad.toml

import kotlin.math.abs
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertTrue

// TODO: underscore separators
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

    private fun assertParsesWithin(expected: Double, valueToParse: String) {
        val value = ((TomlValue.from("test = $valueToParse") as TomlValue.Map).properties["test"] as TomlValue.Double).value
        assertTrue(abs(value - expected) < 0.0001)
    }
}
