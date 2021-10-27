package cc.ekblad.toml

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.function.Executable
import kotlin.math.exp
import kotlin.test.assertEquals

interface UnitTest {
    fun <T> Collection<T>.assertAll(assertion: (T) -> Unit): Unit {
        val assertions = stream().map {
            Executable { assertion(it) }
        }
        assertAll(assertions)
    }

    fun assertParsesTo(expected: TomlValue, valueToParse: String) {
        assertEquals(
            TomlValue.Map(mapOf("test" to expected)),
            TomlValue.from("test = $valueToParse")
        )
    }
}