package cc.ekblad.toml

import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.function.Executable
import kotlin.test.assertEquals

interface UnitTest {
    fun <T> Collection<T>.assertAll(assertion: (T) -> Unit) {
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

    fun String.trimFirst(): String = when {
        isEmpty() -> this
        first() == '\n' -> drop(1)
        substring(0, 2.coerceAtMost(length)) == "\r\n" -> drop(2)
        else -> this
    }
}
