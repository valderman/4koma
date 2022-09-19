package cc.ekblad.toml

import cc.ekblad.toml.model.TomlDocument
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.serialization.write
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Path
import kotlin.io.path.absolute
import kotlin.io.path.deleteIfExists
import kotlin.io.path.moveTo

/**
 * Encodes the given [value] into a valid TOML document.
 * If [value] does not serialize to a valid TOML document (i.e. a map of zero or more keys),
 * an [TomlException.SerializationError] is thrown.
 */
fun TomlMapper.encodeToDocument(value: Any): TomlDocument {
    val tomlValue = encode(value)
    return tomlValue as? TomlDocument
        ?: throw TomlException.SerializationError.NotAMap(value, tomlValue)
}

/**
 * Encodes the given [value] into a valid TOML document and serializes it into a string.
 * If [value] does not serialize to a valid TOML document (i.e. a map of zero or more keys),
 * an [TomlException.SerializationError] is thrown.
 */
fun TomlMapper.encodeToString(value: Any): String {
    val stringBuffer = StringBuffer()
    encodeToDocument(value).write(stringBuffer)
    return stringBuffer.toString()
}

/**
 * Serializes given [value] into a valid TOML document and writes it to the given [Appendable].
 * If [value] does not serialize to a valid TOML document (i.e. a map of zero or more keys),
 * an [TomlException.SerializationError] is thrown.
 */
fun TomlMapper.encodeTo(output: Appendable, value: Any) {
    encodeToDocument(value).write(output)
}

/**
 * Serializes given [value] into a valid TOML document and writes it to the given [OutputStream].
 * If [value] does not serialize to a valid TOML document (i.e. a map of zero or more keys),
 * an [TomlException.SerializationError] is thrown.
 */
fun TomlMapper.encodeTo(outputStream: OutputStream, value: Any) {
    encodeToDocument(value).write(outputStream)
}

/**
 * Serializes given [value] into a valid TOML document and writes it to the file indicated by the given [Path].
 * If [value] does not serialize to a valid TOML document (i.e. a map of zero or more keys),
 * an [TomlException.SerializationError] is thrown.
 */
fun TomlMapper.encodeTo(path: Path, value: Any) {
    encodeToDocument(value).write(path)
}

/**
 * Serializes given [value] into a valid TOML document and writes it to a temporary file in the same directory as
 * the file indicated by [path]. The file will then be synced to disk, and finally moved to its final destination,
 * atomically replacing any pre-existing file in that location.
 *
 * Use this if you intend to update a potentially pre-existing config file with new values.
 *
 * If [value] does not serialize to a valid TOML document (i.e. a map of zero or more keys),
 * an [TomlException.SerializationError] is thrown.
 */
fun TomlMapper.atomicallyEncodeTo(path: Path, value: Any) {
    val absolutePath = path.absolute()
    val tempFile = kotlin.io.path.createTempFile(directory = absolutePath.parent)
    try {
        val fileOutputStream = FileOutputStream(tempFile.toFile())
        encodeTo(fileOutputStream, value)
        fileOutputStream.channel.force(true)
        fileOutputStream.fd.sync()
        fileOutputStream.channel.close()
        tempFile.moveTo(path, overwrite = true)
    } finally {
        tempFile.deleteIfExists()
    }
}
