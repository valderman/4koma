package cc.ekblad.toml.parser

import cc.ekblad.konbini.bracket
import cc.ekblad.konbini.many
import cc.ekblad.konbini.regex
import cc.ekblad.konbini.tryParse
import cc.ekblad.toml.model.TomlValue

private val openingSquareBracket = regex("\\[($ws($comment)?($newline|$)?)*")
private val closingSquareBracket = regex("($ws($comment)?($newline|$)?)*]")
private val commaSeparator = regex("($ws($comment)?($newline|\$)?)*,($ws(#[^\\n]*)?($newline|$)?)*")

internal val inlineList = bracket(
    openingSquareBracket,
    closingSquareBracket
) {
    val elements = many { value().also { commaSeparator() } }
    val maybeTail = tryParse(value)
    if (maybeTail != null) {
        @Suppress("UNCHECKED_CAST")
        (elements as MutableList<Any>).add(maybeTail)
    }
    TomlValue.List(elements)
}
