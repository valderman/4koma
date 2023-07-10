package cc.ekblad.toml.parser

import cc.ekblad.konbini.ParserState
import cc.ekblad.konbini.bracket
import cc.ekblad.konbini.chain
import cc.ekblad.konbini.parser
import cc.ekblad.konbini.regex
import cc.ekblad.toml.model.TomlValue

internal val inlineTable = parser {
    val items = bracket(openCurly, closeCurly) {
        chain(parser { keyValuePair() }, singleLineCommaSeparator)
    }
    val map = TomlValue.Map(mutableMapOf())
    items.terms.forEach { (key, value) ->
        insertNested(map, key, 0, value)
    }
    map
}

private val openCurly = regex("\\{$ws")
private val closeCurly = regex("$ws}")
private val singleLineCommaSeparator = regex("$ws,$ws")

private tailrec fun ParserState.insertNested(map: TomlValue.Map, key: List<String>, keyIndex: Int, value: TomlValue) {
    val dict = map.properties as MutableMap<String, TomlValue>
    val alreadyDefined = { fail("Key '${key.take(keyIndex + 1).joinToString()}' already defined.") }
    if (keyIndex >= key.lastIndex) {
        dict.putIfAbsent(key[keyIndex], value)?.also { alreadyDefined() }
    } else {
        val newDict = TomlValue.Map(mutableMapOf())
        when (dict.putIfAbsent(key[keyIndex], newDict)) {
            null -> insertNested(newDict, key, keyIndex + 1, value)
            else -> alreadyDefined()
        }
    }
}
