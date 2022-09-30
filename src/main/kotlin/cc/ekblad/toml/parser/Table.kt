package cc.ekblad.toml.parser

import cc.ekblad.konbini.ParserState
import cc.ekblad.konbini.bracket
import cc.ekblad.konbini.regex

/**
 * Parse either a standard table or an array table.
 */
internal fun ParserState.parseTable(builder: TomlBuilder) {
    if (rest.startsWith("[[")) {
        parseArrayTable(builder)
    } else {
        parseStandardTable(builder)
    }
}

private val table = bracket(regex("$ws\\[$ws"), regex("$ws]"), key)
private val tableArray = bracket(regex("$ws\\[\\[$ws"), regex("$ws]]"), key)

private fun ParserState.parseStandardTable(builder: TomlBuilder) {
    builder.resetContext()
    val pos = position
    val line = { computeLine(input, pos) }
    val key = table()
    val tableCtx = builder.defineTable(line, key.dropLast(1), true)
    val newTableCtx = tableCtx.subcontext(key.last()) ?: TomlBuilder.Context.new()
    tableCtx.set(line, key.last(), newTableCtx.asMap())
    builder.setContext(newTableCtx)
}

private fun ParserState.parseArrayTable(builder: TomlBuilder) {
    builder.resetContext()
    val pos = position
    val line = { computeLine(input, pos) }
    val key = tableArray()
    val tableCtx = builder.defineTable(line, key.dropLast(1), true)
    val newTableCtx = with(builder) { tableCtx.addTableArrayEntry(line, key.last()) }
    builder.setContext(newTableCtx)
}
