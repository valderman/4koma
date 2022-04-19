package cc.ekblad.toml.serialization

import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import java.time.format.DateTimeFormatter
import kotlin.io.path.outputStream

/**
 * Serializes the receiver [TomlValue.Map] into a valid TOML document and writes it to the given [Appendable].
 */
fun TomlValue.Map.write(output: Appendable) {
    writePath(output, emptyList())
}

/**
 * Serializes the receiver [TomlValue.Map] into a valid TOML document and writes it to the given [OutputStream].
 */
fun TomlValue.Map.write(outputStream: OutputStream) {
    val printStream = PrintStream(outputStream)
    write(printStream as Appendable)
}

/**
 * Serializes the receiver [TomlValue.Map] into a valid TOML document and writes it to the file
 * represented by the given [Path].
 */
fun TomlValue.Map.write(path: Path) {
    path.outputStream().use {
        write(it)
    }
}

private fun TomlValue.writePath(output: Appendable, path: List<String>) {
    when (this) {
        is TomlValue.Map -> writePath(output, path)
        is TomlValue.List -> writeKeyValue(output, path)
        is TomlValue.Primitive -> writeKeyValue(output, path)
    }
}

private fun TomlValue.Map.writePath(output: Appendable, path: List<String>) {
    if (path.isEmpty()) {
        writeTopLevel(output)
    } else {
        properties.forEach {
            it.value.writePath(output, path + it.key)
        }
    }
}

private fun TomlValue.writeKeyValue(output: Appendable, path: List<String>) {
    output.append(encodePath(path.drop(1)), " = ")
    writeValue(output)
    output.appendLine()
}

private fun TomlValue.writeValue(output: Appendable) {
    when (this) {
        is TomlValue.List -> writeValue(output)
        is TomlValue.Map -> writeValue(output)
        is TomlValue.Bool -> output.append(value.toString())
        is TomlValue.Double -> output.append(value.toString())
        is TomlValue.Integer -> output.append(value.toString())
        is TomlValue.LocalDate -> output.append(value.format(DateTimeFormatter.ISO_LOCAL_DATE))
        is TomlValue.LocalDateTime -> output.append(value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
        is TomlValue.LocalTime -> output.append(value.format(DateTimeFormatter.ISO_LOCAL_TIME))
        is TomlValue.OffsetDateTime -> output.append(value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        is TomlValue.String -> writeValue(output)
    }
}

private fun TomlValue.List.writeValue(output: Appendable) {
    output.append("[ ")
    elements.fold(true) { first, value ->
        if (!first) {
            output.append(", ")
        }
        value.writeValue(output)
        false
    }
    output.append(" ]")
}

private fun TomlValue.Map.writeValue(output: Appendable) {
    output.append("{ ")
    properties.entries.fold(true) { first, it ->
        if (!first) {
            output.append(", ")
        }
        output.append(encodeKey(it.key), " = ")
        it.value.writeValue(output)
        false
    }
    output.append(" }")
}

private fun encodePath(path: List<String>) =
    path.joinToString(".", transform = ::encodeKey)

private fun TomlValue.Map.writeTopLevel(output: Appendable) {
    val (primitive, complex) = properties.entries.partition { it.value is TomlValue.Primitive }
    (primitive + complex).fold(true) { first, it ->
        when (val value = it.value) {
            is TomlValue.Map -> {
                if (!first) {
                    output.appendLine()
                }
                output.appendLine("[${encodeKey(it.key)}]")
                it.value.writePath(output, listOf(it.key))
            }
            is TomlValue.List -> {
                value.elements.fold(first) { firstInner, element ->
                    if (!firstInner) {
                        output.appendLine()
                    }
                    output.appendLine("[[${encodeKey(it.key)}]]")
                    element.writePath(output, listOf(it.key))
                    false
                }
            }
            is TomlValue.Primitive -> {
                it.value.writeKeyValue(output, listOf(".", it.key))
            }
        }
        false
    }
}

private fun encodeKey(key: String): String = when {
    !key.contains(quotesRequiredRegex) -> key
    !key.contains('\n') -> "'$key'"
    else -> {
        val escapedKey = key.replace("\n", "\\n").replace("\"", "\\\"")
        "\"$escapedKey\""
    }
}

private val quotesRequiredRegex = Regex("[^a-zA-Z0-9]")

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

private fun TomlValue.String.writeValue(output: Appendable) {
    val invalidChars = value.filter { it in invalidTomlChars }.toSet()
    if (invalidChars.isNotEmpty()) {
        val invalidCharString = invalidChars.joinToString(", ") { "\\u${it.code.toString(16)}" }
        val msg = "string contains characters which are not allowed in a toml document: $invalidCharString"
        throw TomlException.SerializationError(msg, null)
    }
    val eligibleQuoteTypes = QuoteType.values().toList()
        .removeIf(value.contains("''"), QuoteType.Literal, QuoteType.MultilineLiteral)
        .removeIf(value.contains("\"\""), QuoteType.Plain, QuoteType.Multiline)
        .removeIf(value.contains("'"), QuoteType.Literal)
        .removeIf(value.contains("\""), QuoteType.Plain)
        .removeIf(value.contains("\n"), QuoteType.Literal, QuoteType.Plain)
    val quoteType = eligibleQuoteTypes.firstOrNull() ?: QuoteType.Plain
    val text = when (quoteType) {
        QuoteType.Plain -> value.replace("\"", "\\\"").replace("\n", "\\n")
        QuoteType.Multiline -> "\n$value"
        QuoteType.Literal -> value
        QuoteType.MultilineLiteral -> "\n$value"
    }
    output.append(quoteType.quotes, text, quoteType.quotes)
}
