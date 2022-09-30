package cc.ekblad.toml.parser

import cc.ekblad.konbini.ParserState
import cc.ekblad.konbini.parser
import cc.ekblad.konbini.regex
import cc.ekblad.toml.model.TomlValue

private val eq = regex("$ws=$ws")

internal val keyValuePair = parser {
    val k = key()
    eq()
    val v = value()
    k to v
}

internal fun ParserState.parseKeyValuePair(builder: TomlBuilder) {
    val pos = position
    val line = { computeLine(input, pos) }
    val (key, value) = keyValuePair()
    val tableCtx = builder.defineTable(line, key.dropLast(1), false)
    tableCtx.set(line, key.last(), value.toMutableTomlValue())
}

/**
 * Maps and lists passed to this function will be treated as inline.
 */
private fun TomlValue.toMutableTomlValue(): MutableTomlValue = when (this) {
    is TomlValue.Primitive -> MutableTomlValue.Primitive(this)
    is TomlValue.List -> MutableTomlValue.InlineList(this)
    is TomlValue.Map -> MutableTomlValue.InlineMap(this)
}
