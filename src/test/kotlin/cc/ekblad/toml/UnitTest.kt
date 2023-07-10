package cc.ekblad.toml

import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.serialization.from
import org.junit.jupiter.api.Assertions.assertAll
import org.junit.jupiter.api.function.Executable
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

interface UnitTest {
    fun <T> Collection<T>.assertAll(assertion: (T) -> Unit) {
        val assertions = stream().map {
            Executable { assertion(it) }
        }
        assertAll(assertions)
    }

    fun assertParsesTo(expected: TomlValue, valueToParse: String) {
        val parsedValue = try {
            TomlValue.from("test = $valueToParse")
        } catch (e: TomlException) {
            fail("Couldn't parse value '$valueToParse'")
        }
        assertEquals(
            TomlValue.Map(mapOf("test" to expected)),
            parsedValue
        )
    }

    fun assertValueParseError(badValue: String) {
        assertFailsWith<TomlException.ParseError>("parser accepted '$badValue'") {
            TomlValue.from("foo = $badValue")
        }
    }

    fun assertDocumentParseError(badDocument: String) {
        assertFailsWith<TomlException.ParseError>("parser accepted bad document:\n$badDocument\n") {
            TomlValue.from(badDocument)
        }
    }

    fun String.trimFirst(): String = when {
        isEmpty() -> this
        first() == '\n' -> drop(1)
        substring(0, 2.coerceAtMost(length)) == "\r\n" -> drop(2)
        else -> this
    }
}
