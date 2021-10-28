package cc.ekblad.toml

import TomlLexer
import TomlParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.InputStream
import java.nio.file.Path

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
    data class List(val value: kotlin.collections.List<TomlValue>) : TomlValue() {
        constructor(vararg values: TomlValue) : this(values.toList())
    }

    companion object {
        fun from(string: kotlin.String): TomlValue =
            from(CharStreams.fromString(string))

        fun from(stream: InputStream): TomlValue =
            from(CharStreams.fromStream(stream))

        fun from(path: Path): TomlValue =
            from(CharStreams.fromPath(path))

        fun from(charStream: CharStream): TomlValue {
            val lexer = TomlLexer(charStream)
            val tokenStream = CommonTokenStream(lexer)
            val parser = TomlParser(tokenStream)
            parser.removeErrorListeners()
            parser.addErrorListener(TomlErrorListener())
            val documentContext = parser.document()
            val builder = TomlBuilder.create()
            builder.extractDocument(documentContext)
            return builder.build()
        }
    }
}