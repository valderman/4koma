package cc.ekblad.toml.parser

import cc.ekblad.konbini.bracket
import cc.ekblad.konbini.regex

/**
 * Parse either a standard table or an array table.
 */
internal val parseTable: TomlBuilder.() -> Unit = {
    if (rest.startsWith("[[")) {
        parseArrayTable()
    } else {
        parseStandardTable()
    }
}

private val table = bracket(regex("$ws\\[$ws"), regex("$ws]"), key)
private val tableArray = bracket(regex("$ws\\[\\[$ws"), regex("$ws]]"), key)

private val parseStandardTable: TomlBuilder.() -> Unit = {
    resetContext()
    val pos = position
    val line = { computeLine(input, pos) }
    val key = table()
    val tableCtx = defineTable(line, key.dropLast(1), true)
    val newTableCtx = tableCtx.subcontext(key.last()) ?: TomlBuilder.Context.new()
    tableCtx.set(line, key.last(), newTableCtx.asMap())
    setContext(newTableCtx)
}

private val parseArrayTable: TomlBuilder.() -> Unit = {
    resetContext()
    val pos = position
    val line = { computeLine(input, pos) }
    val key = tableArray()
    val tableCtx = defineTable(line, key.dropLast(1), true)
    val newTableCtx = tableCtx.addTableArrayEntry(line, key.last())
    setContext(newTableCtx)
}
