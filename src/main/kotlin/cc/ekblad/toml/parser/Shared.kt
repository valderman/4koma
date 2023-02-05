package cc.ekblad.toml.parser

/**
 * Characters that must not appear anywhere in a TOML document.
 */
internal val invalidTomlChars = listOf(
    '\u0000'..'\u0008',
    '\u000B'..'\u000C',
    '\u000E'..'\u001F',
    listOf('\u007F', '�'),
).flatten().joinToString("")

internal const val ws = "[\\t ]*"
internal const val newline = "\\r?\\n"
internal val comment = "#[^\\n\\r$invalidTomlChars]*"

internal fun computeLine(input: String, position: Int): Int =
    input.substring(0, position).count { it == '\n' }
