package cc.ekblad.toml.serialization

import cc.ekblad.toml.model.TomlDocument
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.parser.parseTomlDocument
import java.io.InputStream
import java.nio.file.Path
import kotlin.io.path.inputStream

/**
 * Parse the given TOML-formatted string into a TOML map.
 *
 * If the string does not contain a valid TOML document, a [TomlException.ParseError] is thrown.
 */
fun TomlValue.Companion.from(string: String): TomlDocument =
    parseTomlDocument(string)

/**
 * Parse the given TOML-formatted input stream into a TOML map.
 *
 * If the stream does not contain a valid TOML document, a [TomlException.ParseError] is thrown.
 */
fun TomlValue.Companion.from(stream: InputStream): TomlDocument =
    parseTomlDocument(stream.readAllBytes().toString(Charsets.UTF_8))

/**
 * Parse the given TOML-formatted file into a TOML map.
 *
 * If the indicated file does not contain a valid TOML document, a [TomlException.ParseError] is thrown.
 */
fun TomlValue.Companion.from(path: Path): TomlDocument =
    path.inputStream().use { from(it) }
