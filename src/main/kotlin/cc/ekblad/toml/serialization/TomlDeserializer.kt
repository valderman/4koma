package cc.ekblad.toml.serialization

import cc.ekblad.toml.model.TomlDocument
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.parser.TomlLexer
import cc.ekblad.toml.parser.TomlParser
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import java.io.InputStream
import java.nio.file.Path

/**
 * Parse the given TOML-formatted string into a TOML map.
 *
 * If the string does not contain a valid TOML document, a [TomlException.ParseError] is thrown.
 */
fun TomlValue.Companion.from(string: String): TomlDocument =
    from(CharStreams.fromString(string))

/**
 * Parse the given TOML-formatted input stream into a TOML map.
 *
 * If the stream does not contain a valid TOML document, a [TomlException.ParseError] is thrown.
 */
fun TomlValue.Companion.from(stream: InputStream): TomlDocument =
    from(CharStreams.fromStream(stream, Charsets.UTF_8))

/**
 * Parse the given TOML-formatted file into a TOML map.
 *
 * If the indicated file does not contain a valid TOML document, a [TomlException.ParseError] is thrown.
 */
fun TomlValue.Companion.from(path: Path): TomlDocument =
    from(CharStreams.fromPath(path, Charsets.UTF_8))

private fun TomlValue.Companion.from(charStream: CharStream): TomlDocument {
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
