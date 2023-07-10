package cc.ekblad.toml.parser

import cc.ekblad.konbini.map
import cc.ekblad.konbini.parser
import cc.ekblad.konbini.regex
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue

/**
 * Basic or basic multiline string.
 */
internal val escapableString = parser {
    if (rest.startsWith("\"\"\"")) {
        basicMultilineString()
    } else {
        basicString()
    }.let { TomlValue.String(it) }
}

/**
 * Literal or literal multiline string.
 */
internal val unescapableString = parser {
    if (rest.startsWith("'''")) {
        literalMultilineString()
    } else {
        literalString()
    }.let { TomlValue.String(it) }
}

/**
 * Single-line string with escape codes.
 */
internal val basicString = parser {
    val pos = position
    val str = basicStringRegex()
    str.substring(1, str.lastIndex).convertEscapeCodes(pos)
}

/**
 * Single-line string without escape codes.
 */
internal val literalString = regex("'[^'\\n$invalidTomlChars]*'").map { it.substring(1, it.lastIndex) }

private const val esc = "\\\\([\\\\\"bnfrt]|u([0-9a-fA-F]{4})|U([0-9a-fA-F]{8}))"
private val basicStringRegex = regex("\"([^\\\\\"\\n$invalidTomlChars]|$esc)*\"")
private val basicMultilineStringRegex = regex("\"{3,5}([^\\\\$invalidTomlChars]|\"{1,2}[^\"]|$esc)*?\"{3,5}")
private val escapeRegex = Regex(esc)

private val basicMultilineString = parser {
    val pos = position
    val str = basicMultilineStringRegex()
    str.substring(3, str.lastIndex - 2).trimFirstNewline().convertEscapeCodes(pos)
}
private val literalMultilineString = regex("'{3,5}([^'$invalidTomlChars]|'{1,2}[^'])*?'{3,5}").map {
    it.substring(3, it.lastIndex - 2).trimFirstNewline()
}

private fun String.trimFirstNewline(): String = when {
    startsWith('\n') -> drop(1)
    startsWith("\r\n") -> drop(2)
    else -> this
}

private fun String.convertEscapeCodes(line: Int): String =
    escapeRegex.replace(this) { replaceEscapeMatch(line, it) }

private fun replaceEscapeMatch(line: Int, match: MatchResult): String = when (match.value[1]) {
    '"' -> "\""
    '\\' -> "\\"
    'b' -> "\b"
    'f' -> "\u000C"
    'n' -> "\n"
    'r' -> "\r"
    't' -> "\t"
    'u' -> String(Character.toChars(match.groupValues[2].toInt(16).throwOnNonScalar(line)))
    else /* 'U' */ -> String(Character.toChars(match.groupValues[3].toInt(16).throwOnNonScalar(line)))
}

/**
 * Unicode surrogate characters are not valid escape codes.
 */
private fun Int.throwOnNonScalar(line: Int): Int = apply {
    if (this in 0xD800..0xDFFF) {
        throw TomlException.ParseError("surrogate character '$this' is not a valid escape code", line)
    }
}
