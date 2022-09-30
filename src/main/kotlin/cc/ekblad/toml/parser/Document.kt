package cc.ekblad.toml.parser

import cc.ekblad.konbini.Parser
import cc.ekblad.konbini.ParserResult
import cc.ekblad.konbini.ParserState
import cc.ekblad.konbini.boolean
import cc.ekblad.konbini.many
import cc.ekblad.konbini.oneOf
import cc.ekblad.konbini.parseToEnd
import cc.ekblad.konbini.parser
import cc.ekblad.konbini.regex
import cc.ekblad.konbini.whitespace
import cc.ekblad.toml.model.TomlDocument
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue

private val eol = regex("$ws($comment)?($newline|$)")

internal val value: Parser<TomlValue> = parser {
    when (next) {
        '"' -> escapableString()
        '\'' -> unescapableString()
        '[' -> inlineList()
        '{' -> inlineTable()
        't', 'f' -> TomlValue.Bool(boolean())
        else -> oneOf(dateTime, number)
    }
}

private fun ParserState.statement(builder: TomlBuilder) {
    whitespace()
    when (next) {
        '[' -> parseTable(builder)
        '#' -> { }
        else -> parseKeyValuePair(builder)
    }
    eol()
}

private val document = parser {
    val tomlBuilder = TomlBuilder.create()
    many { statement(tomlBuilder) }
    tomlBuilder.build()
}

internal fun parseTomlDocument(input: String): TomlDocument =
    when (val result = document.parseToEnd(input, ignoreWhitespace = true)) {
        is ParserResult.Ok -> result.result
        is ParserResult.Error -> throw TomlException.ParseError(result.reason, result.line)
    }
