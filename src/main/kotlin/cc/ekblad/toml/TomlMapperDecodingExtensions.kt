package cc.ekblad.toml

import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.serialization.from
import java.io.InputStream
import java.nio.file.Path

/**
 * Parses the given TOML-formatted string into a TOML document and decodes it into the given type [T].
 *
 * If the string does not contain a valid TOML document, a [TomlException.ParseError] is thrown.
 * If the resulting TOML document can not be decoded into the type [T], a [TomlException.DecodingError] is thrown.
 */
inline fun <reified T> TomlMapper.decode(string: String): T =
    decode(TomlValue.from(string))

/**
 * Parses the given TOML-formatted input stream into a TOML document and decodes it into the given type [T].
 *
 * If the string does not contain a valid TOML document, a [TomlException.ParseError] is thrown.
 * If the resulting TOML document can not be decoded into the type [T], a [TomlException.DecodingError] is thrown.
 */
inline fun <reified T> TomlMapper.decode(stream: InputStream): T =
    decode(TomlValue.from(stream))

/**
 * Parses the given TOML-formatted file into a TOML document and decodes it into the given type [T].
 *
 * If the string does not contain a valid TOML document, a [TomlException.ParseError] is thrown.
 * If the resulting TOML document can not be decoded into the type [T], a [TomlException.DecodingError] is thrown.
 */
inline fun <reified T> TomlMapper.decode(path: Path): T =
    decode(TomlValue.from(path))

/**
 * Like [decode], but fills in any values missing from the input string with values from the given [defaultValue].
 */
inline fun <reified T> TomlMapper.decodeWithDefaults(defaultValue: T, string: String): T =
    decodeWithDefaults(defaultValue, TomlValue.from(string))

/**
 * Like [decode], but fills in any values missing from the input stream with values from the given [defaultValue].
 */
inline fun <reified T> TomlMapper.decodeWithDefaults(defaultValue: T, stream: InputStream): T =
    decodeWithDefaults(defaultValue, TomlValue.from(stream))

/**
 * Like [decode], but fills in any values missing from the input file with values from the given [defaultValue].
 */
inline fun <reified T> TomlMapper.decodeWithDefaults(defaultValue: T, path: Path): T =
    decodeWithDefaults(defaultValue, TomlValue.from(path))
