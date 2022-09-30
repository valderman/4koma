package cc.ekblad.toml.serialization

import cc.ekblad.konbini.Parser
import cc.ekblad.konbini.ParserResult
import cc.ekblad.konbini.ParserState
import cc.ekblad.konbini.boolean
import cc.ekblad.konbini.bracket
import cc.ekblad.konbini.chain
import cc.ekblad.konbini.chain1
import cc.ekblad.konbini.many
import cc.ekblad.konbini.map
import cc.ekblad.konbini.oneOf
import cc.ekblad.konbini.parseToEnd
import cc.ekblad.konbini.parser
import cc.ekblad.konbini.regex
import cc.ekblad.konbini.tryParse
import cc.ekblad.konbini.whitespace
import cc.ekblad.toml.model.TomlDocument
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

private val invalidChars = listOf(
    '\u0000'..'\u0008',
    '\u000B'..'\u000C',
    '\u000E'..'\u001F',
    listOf('\u007F', '�'),
).flatten().joinToString("")
private val esc = "\\\\([\\\\\"bnfrt]|u([0-9a-fA-F]{4})|U([0-9a-fA-F]{8}))"
private val ws = "[\\t ]*"
private val digits = "[0-9](_?[0-9])*"
private val date = "[0-9]{4}-[0-9]{2}-[0-9]{2}"
private val time = "[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?"
private val newline = "\\r?\\n"
private val comment = "#[^\\n\\r$invalidChars]*"

private val eq = regex("$ws=$ws")
private val eol = regex("$ws($comment)?($newline|$)")
private val commaSeparator = regex("($ws($comment)?($newline|\$)?)*,($ws(#[^\\n]*)?($newline|$)?)*")

private val decimalInt: Parser<Long> = parser {
    val result = regex("[+-]?$digits")
    result.replace("_", "").toLongOrNull()
        ?: fail("Value '$result' out of range for 64-bit integer.")
}
private val hexadecimalInt = parser {
    val result = regex("0x[0-9a-fA-F](_?[0-9a-fA-F])*")
    result.replace("_", "").drop(2).toLongOrNull(16)
        ?: fail("Value '$result' out of range for 64-bit integer.")
}
private val octalInt = parser {
    val result = regex("0o[0-7](_?[0-7])*")
    result.replace("_", "").drop(2).toLongOrNull(8)
        ?: fail("Value '$result' out of range for 64-bit integer.")
}
private val binaryInt = parser {
    val result = regex("0b[01](_?[01])*")
    result.replace("_", "").drop(2).toLongOrNull(2)
        ?: fail("Value '$result' out of range for 64-bit integer.")
}

private val integer = oneOf(hexadecimalInt, binaryInt, octalInt, decimalInt).map { TomlValue.Integer(it) }

private val decimal = oneOf(
    regex("[+-]?$digits((\\.$digits)?[eE][+-]?$digits|\\.$digits)").map { it.replace("_", "").toDouble() },
    regex("[+-]?nan").map { Double.NaN },
    regex("[+]?inf").map { Double.POSITIVE_INFINITY },
    regex("-inf").map { Double.NEGATIVE_INFINITY },
).map { TomlValue.Double(it) }

private val number = oneOf(decimal, integer)

private val basicStringRegex = regex("\"([^\\\\\"\\n$invalidChars]|$esc)*\"")
private val basicString = parser {
    val pos = position
    val str = basicStringRegex()
    str.substring(1, str.lastIndex).convertEscapeCodes(pos)
}
private val basicMultilineStringRegex = regex("\"{3,5}([^\\\\$invalidChars]|\"{1,2}[^\"]|$esc)*?\"{3,5}")
private val basicMultilineString = parser {
    val pos = position
    val str = basicMultilineStringRegex()
    str.substring(3, str.lastIndex - 2).trimFirstNewline().convertEscapeCodes(pos)
}
private val literalString = regex("'[^'\\n$invalidChars]*'").map { it.substring(1, it.lastIndex) }
private val literalMultilineString = regex("'{3,5}([^'$invalidChars]|'{1,2}[^'])*?'{3,5}").map {
    it.substring(3, it.lastIndex - 2).trimFirstNewline()
}
// TODO: allow quotes in ML strings
private val string = parser {
    val str = oneOf(
        { basicMultilineString().convertEscapeCodes(position) },
        { basicString().convertEscapeCodes(position) },
        literalMultilineString,
        literalString
    )
    TomlValue.String(str)
}

private val localDateRegex = regex(date)
private val localDate = parser {
    val value = localDateRegex()
    try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        fail("Invalid date: '$value'.")
    }
}.map { TomlValue.LocalDate(it) }

private val localTimeRegex = regex(time)
private val localTime = parser {
    val value = localTimeRegex()
    try {
        LocalTime.parse(value)
    } catch (e: DateTimeParseException) {
        fail("Invalid time: '$value'.")
    }
}.map { TomlValue.LocalTime(it) }

private val localDateTimeRegex = regex("$date[tT ]$time")
private val localDateTime = parser {
    val value = localDateTimeRegex()
    try {
        LocalDateTime.parse(value.replace(' ', 'T'))
    } catch (e: DateTimeParseException) {
        fail("Invalid date and/or time: '$value'.")
    }
}.map { TomlValue.LocalDateTime(it) }

private val offsetDateTimeRegex = regex("$date[tT ]$time([zZ]|([+-][0-9]{2}:[0-9]{2}))")
private val offsetDateTime = parser {
    val value = offsetDateTimeRegex()
    try {
        OffsetDateTime.parse(value.replace(' ', 'T'))
    } catch (e: DateTimeParseException) {
        fail("Invalid date and/or time: '$value'.")
    }
}.map { TomlValue.OffsetDateTime(it) }
private val dateTime = oneOf(offsetDateTime, localDateTime, localTime, localDate)

private val openingSquareBracket = regex("\\[($ws($comment)?($newline|$)?)*")
private val closingSquareBracket = regex("($ws($comment)?($newline|$)?)*]")
private val array = bracket(
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

private val keyValuePair = parser {
    val k = key()
    eq()
    val v = value()
    k to v
}
private val openCurly = regex("\\{$ws")
private val closeCurly = regex("$ws}")
private val singleLineCommaSeparator = regex("$ws,$ws")
private val inlineTable = parser {
    val items = bracket(openCurly, closeCurly) {
        chain(parser { keyValuePair() }, singleLineCommaSeparator)
    }
    val map = TomlValue.Map(mutableMapOf())
    items.terms.forEach { (key, value) ->
        insertNested(map, key, 0, value)
    }
    map
}

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

private val escapableString = parser {
    if (rest.startsWith("\"\"\"")) {
        basicMultilineString()
    } else {
        basicString()
    }.let { TomlValue.String(it) }
}

private val unescapableString = parser {
    if (rest.startsWith("'''")) {
        literalMultilineString()
    } else {
        literalString()
    }.let { TomlValue.String(it) }
}

private val value: Parser<TomlValue> = parser {
    when (next) {
        '"' -> escapableString()
        '\'' -> unescapableString()
        '[' -> array()
        '{' -> inlineTable()
        't', 'f' -> TomlValue.Bool(boolean())
        else -> oneOf(dateTime, number)
    }
}

private val bareKey = regex("[A-Za-z0-9_-]+")
private val quotedKey = oneOf(basicString, literalString)
private val key = chain1(oneOf(bareKey, quotedKey), regex("$ws\\.$ws")).map { it.terms }

/**
 * Maps and lists passed to this function will be treated as inline.
 */
private fun TomlValue.toMutableTomlValue(): MutableTomlValue = when (this) {
    is TomlValue.Primitive -> MutableTomlValue.Primitive(this)
    is TomlValue.List -> MutableTomlValue.InlineList(this)
    is TomlValue.Map -> MutableTomlValue.InlineMap(this)
}

private fun ParserState.parseKeyValuePair(builder: TomlBuilder) {
    val pos = position
    val line = { computeLine(input, pos) }
    val (key, value) = keyValuePair()
    val tableCtx = builder.defineTable(line, key.dropLast(1), false)
    tableCtx.set(line, key.last(), value.toMutableTomlValue())
}

private val table = bracket(regex("$ws\\[$ws"), regex("$ws]"), key)
private val tableArray = bracket(regex("$ws\\[\\[$ws"), regex("$ws]]"), key)

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

private fun ParserState.parseTable(builder: TomlBuilder) {
    if (rest.startsWith("[[")) {
        parseArrayTable(builder)
    } else {
        parseStandardTable(builder)
    }
}

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

internal fun parseTomlDocument(input: String): TomlDocument =
    when (val result = document.parseToEnd(input, ignoreWhitespace = true)) {
        is ParserResult.Ok -> result.result
        is ParserResult.Error -> throw TomlException.ParseError(result.reason, result.line)
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

private fun String.trimFirstNewline(): String = when {
    startsWith('\n') -> drop(1)
    startsWith("\r\n") -> drop(2)
    else -> this
}

/**
 * Unicode surrogate characters are not valid escape codes.
 */
private fun Int.throwOnNonScalar(line: Int): Int = apply {
    if (this in 0xD800..0xDFFF) {
        throw TomlException.ParseError("surrogate character '$this' is not a valid escape code", line)
    }
}

private val escapeRegex = Regex(esc)

private fun computeLine(input: String, position: Int): Int =
    input.substring(0, position).count { it == '\n' }
