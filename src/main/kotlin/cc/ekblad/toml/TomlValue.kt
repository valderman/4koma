package cc.ekblad.toml

import TomlLexer
import TomlParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.InputStream
import java.nio.file.Path

/**
 * Kotlin representation of a TOML value.
 * A full TOML document is always represented as a TomlValue.Map.
 *
 * You can either traverse this representation manually, or - more conveniently - convert it to a data class of
 * your choice using [TomlValue.convert].
 */
sealed class TomlValue {
    sealed class Primitive : TomlValue()

    data class String(val value: kotlin.String) : Primitive()
    data class Integer(val value: Long) : Primitive()
    data class Double(val value: kotlin.Double) : Primitive()
    data class Bool(val value: Boolean) : Primitive()
    data class OffsetDateTime(val value: java.time.OffsetDateTime) : Primitive()
    data class LocalDateTime(val value: java.time.LocalDateTime) : Primitive()
    data class LocalDate(val value: java.time.LocalDate) : Primitive()
    data class LocalTime(val value: java.time.LocalTime) : Primitive()

    data class Map(val properties: kotlin.collections.Map<kotlin.String, TomlValue>) : TomlValue() {
        constructor(vararg entries: Pair<kotlin.String, TomlValue>) : this(entries.toMap())
    }
    data class List(val elements: kotlin.collections.List<TomlValue>) : TomlValue() {
        constructor(vararg values: TomlValue) : this(values.toList())
    }

    companion object {
        /**
         * Parse the given TOML-formatted string into a TOML map.
         */
        fun from(string: kotlin.String): Map =
            from(CharStreams.fromString(string))

        /**
         * Parse the given TOML-formatted input stream into a TOML map.
         */
        fun from(stream: InputStream): Map =
            from(CharStreams.fromStream(stream, Charsets.UTF_8))

        /**
         * Parse the given TOML-formatted file into a TOML map.
         */
        fun from(path: Path): Map =
            from(CharStreams.fromPath(path, Charsets.UTF_8))

        private fun from(charStream: CharStream): Map {
            val errorListener = TomlErrorListener()
            val lexer = TomlLexer(charStream)
            lexer.removeErrorListeners()
            lexer.addErrorListener(errorListener)

            val tokenStream = CommonTokenStream(lexer)
            val parser = TomlParser(tokenStream)
            parser.removeErrorListeners()
            parser.addErrorListener(errorListener)

            val documentContext = parser.document()
            val builder = TomlBuilder.create()
            builder.extractDocument(documentContext)
            return builder.build()
        }
    }
}
