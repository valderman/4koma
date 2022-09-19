package cc.ekblad.toml.serialization

import cc.ekblad.toml.model.TomlDocument
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import kotlin.io.path.outputStream

/**
 * Serializes the receiver [TomlDocument] into a valid TOML document using the default serializer
 * and writes it to the given [Appendable].
 */
fun TomlDocument.write(output: Appendable) {
    TomlSerializerState(TomlSerializerConfig.default, output).writePath(this, emptyList())
}

/**
 * Serializes the receiver [TomlDocument] into a valid TOML document using the default serializer
 * and writes it to the given [OutputStream].
 */
fun TomlDocument.write(outputStream: OutputStream) {
    write(PrintStream(outputStream) as Appendable)
}

/**
 * Serializes the receiver [TomlDocument] into a valid TOML document using the default serializer
 * and writes it to the file represented by the given [Path].
 */
fun TomlDocument.write(path: Path) {
    path.outputStream().use { write(it) }
}

internal fun TomlSerializerState.writePath(value: TomlValue.Map, path: List<String>) {
    if (path.isEmpty()) {
        writeTopLevel(value)
    } else {
        value.properties.forEach {
            writePath(it.value, path + it.key)
        }
    }
}

private fun TomlSerializerState.writeTopLevel(map: TomlValue.Map) {
    val (inline, table) = map.properties.entries.partition { (_, element) -> shouldBeInline(element) }

    inline.forEach { (key, value) ->
        writeKeyValue(value, listOf(".", key))
    }

    if (inline.isNotEmpty() && table.isNotEmpty()) {
        appendLine()
    }

    table.fold(true) { first, it ->
        when (val value = it.value) {
            is TomlValue.List -> {
                value.elements.fold(first) { firstInner, element ->
                    if (!firstInner) {
                        appendLine()
                    }
                    appendLine("[[${encodeKey(it.key)}]]")
                    writePath(element, listOf(it.key))
                    false
                }
            }
            else -> {
                if (!first) {
                    appendLine()
                }
                appendLine("[${encodeKey(it.key)}]")
                writePath(value, listOf(it.key))
            }
        }
        false
    }
}

private fun TomlSerializerState.writePath(value: TomlValue, path: List<String>) {
    when (value) {
        is TomlValue.Map -> writePath(value, path)
        is TomlValue.List -> writeKeyValue(value, path)
        is TomlValue.Primitive -> writeKeyValue(value, path)
    }
}

private fun TomlSerializerState.writeKeyValue(value: TomlValue, path: List<String>) {
    append(encodePath(path.drop(1)), " = ")
    writeValue(value)
    appendLine()
}

private fun TomlSerializerState.writeValue(value: TomlValue) {
    when (value) {
        is TomlValue.List -> writeValue(value)
        is TomlValue.Map -> writeValue(value)
        is TomlValue.Bool -> append(value.value.toString())
        is TomlValue.Double -> append(value.value.toString())
        is TomlValue.Integer -> append(value.value.toString())
        is TomlValue.LocalDate -> append(value.value.format(DateTimeFormatter.ISO_LOCAL_DATE))
        is TomlValue.LocalDateTime -> append(value.value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        is TomlValue.LocalTime -> append(value.value.format(DateTimeFormatter.ISO_LOCAL_TIME))
        is TomlValue.OffsetDateTime -> append(value.value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        is TomlValue.String -> writeValue(value)
    }
}

private fun TomlSerializerState.writeValue(value: TomlValue.List) {
    list(value) {
        value.elements.fold(true) { first, element ->
            if (!first) {
                appendListSeparator()
            }
            writeValue(element)
            false
        }
    }
}

private fun TomlSerializerState.writeValue(value: TomlValue.Map) {
    table {
        value.properties.entries.fold(true) { first, it ->
            if (!first) {
                append(", ")
            }
            append(encodeKey(it.key), " = ")
            writeValue(it.value)
            false
        }
    }
}

private fun encodePath(path: List<String>) =
    path.joinToString(".", transform = ::encodeKey)

private fun encodeKey(key: String): String = when {
    !key.contains(quotesRequiredRegex) -> key
    !key.contains('\n') -> "'$key'"
    else -> {
        val escapedKey = key.replace("\n", "\\n").replace("\"", "\\\"")
        "\"$escapedKey\""
    }
}

private val quotesRequiredRegex = Regex("[^a-zA-Z0-9_-]")

private enum class QuoteType(val quotes: String) {
    Plain("\""),
    Literal("'"),
    Multiline("\"\"\""),
    MultilineLiteral("'''")
}

private fun <T> List<T>.removeIf(condition: Boolean, vararg remove: T) = if (condition) {
    filter { it !in remove }
} else {
    this
}

private fun TomlSerializerState.writeValue(value: TomlValue.String) {
    val invalidChars = value.value.filter { it in invalidTomlChars }.toSet()
    if (invalidChars.isNotEmpty()) {
        val invalidCharString = invalidChars.joinToString(", ") { "\\u${it.code.toString(16)}" }
        throw TomlException.SerializationError(
            "string contains characters which are not allowed in a toml document: $invalidCharString",
            null
        )
    }
    val eligibleQuoteTypes = QuoteType.values().toList()
        .removeIf(value.value.contains("''"), QuoteType.Literal, QuoteType.MultilineLiteral)
        .removeIf(value.value.contains("\"\""), QuoteType.Plain, QuoteType.Multiline)
        .removeIf(value.value.contains("'"), QuoteType.Literal)
        .removeIf(value.value.contains("\""), QuoteType.Plain)
        .removeIf(value.value.contains("\n"), QuoteType.Literal, QuoteType.Plain)
    val quoteType = eligibleQuoteTypes.firstOrNull() ?: QuoteType.Plain
    val text = when (quoteType) {
        QuoteType.Plain -> value.value.replace("\"", "\\\"").replace("\n", "\\n")
        QuoteType.Multiline -> "\n${value.value}"
        QuoteType.Literal -> value.value
        QuoteType.MultilineLiteral -> "\n${value.value}"
    }
    append(quoteType.quotes, text, quoteType.quotes)
}
