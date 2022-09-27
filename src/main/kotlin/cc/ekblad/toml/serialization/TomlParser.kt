package cc.ekblad.toml.serialization

import cc.ekblad.konbini.Parser
import cc.ekblad.konbini.ParserState
import cc.ekblad.konbini.atomically
import cc.ekblad.konbini.bracket
import cc.ekblad.konbini.chain
import cc.ekblad.konbini.chain1
import cc.ekblad.konbini.many
import cc.ekblad.konbini.map
import cc.ekblad.konbini.oneOf
import cc.ekblad.konbini.parseToEnd
import cc.ekblad.konbini.parser
import cc.ekblad.konbini.regex
import cc.ekblad.konbini.then
import cc.ekblad.konbini.tryParse
import cc.ekblad.toml.model.TomlValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

private val ws = "[\\t ]*"
private val digits = "[0-9](_?[0-9])*"
private val date = "[0-9]{4}-[0-9]{2}-[0-9]{2}"
private val time = "[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?"

private val eq = regex("$ws=$ws")
private val eol = regex("$ws(#[^\\n]*)?(\\n|$)")
private val commaSeparator = regex("($ws(#[^\\n]*)?(\\n|\$)?)*,($ws(#[^\\n]*)?(\\n|$)?)*")

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

private val integer = oneOf(decimalInt, hexadecimalInt, binaryInt, octalInt).map { TomlValue.Integer(it) }

private val boolean = regex("true|false").map { TomlValue.Bool(it.toBooleanStrict()) }

private val intPart = regex("[+-]?$digits")
private val exponent = regex("[eE][+-]?$digits")
private val fraction = regex("\\.$digits")
private val normalDecimal = atomically {
    // TODO: this could be implemented more efficiently as a single regex
    val intPart = intPart()
    val fracPart = tryParse {
        fraction()
    }
    val expPart = if (fracPart == null) {
        exponent()
    } else {
        tryParse(exponent) ?: ""
    }
    (intPart + (fracPart ?: "") + expPart).toDouble()
}
private val decimal = oneOf(
    normalDecimal,
    regex("[+-]?nan").map { Double.NaN },
    regex("[+]?inf").map { Double.POSITIVE_INFINITY },
    regex("-inf").map { Double.NEGATIVE_INFINITY },
).map { TomlValue.Double(it) }

private val basicString = regex("\"([^\\\"\\n]|\\[\\\"])*\"").map { it.substring(1, it.lastIndex) }
private val basicMultilineString = regex("\"{3}([^\\\\]|\\\\)*?\"{3,5}").map { it.substring(3, it.lastIndex - 2) }
private val literalString = regex("'[^'\\n]*'").map { it.substring(1, it.lastIndex) }
private val literalMultilineString = regex("'''[^']*?'{3,5}").map { it.substring(3, it.lastIndex - 2) }
// TODO: allow quotes in ML strings
private val string = oneOf(basicMultilineString, basicString, literalMultilineString, literalString).map {
    TomlValue.String(it)
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
        LocalDateTime.parse(value)
    } catch (e: DateTimeParseException) {
        fail("Invalid date and/or time: '$value'.")
    }
}.map { TomlValue.LocalDateTime(it) }

private val offsetDateTimeRegex = regex("$date[tT ]$time([zZ]|([+-][0-9]{2}:[0-9]{2}))")
private val offsetDateTime = parser {
    val value = offsetDateTimeRegex()
    try {
        OffsetDateTime.parse(value)
    } catch (e: DateTimeParseException) {
        fail("Invalid date and/or time: '$value'.")
    }
}.map { TomlValue.OffsetDateTime(it) }

private val openingSquareBracket = regex("\\[($ws(#[^\\n]*)?(\\n|$)?)*")
private val closingSquareBracket = regex("($ws(#[^\\n]*)?(\\n|$)?)*]")
private val array = bracket(
    openingSquareBracket,
    closingSquareBracket,
    many { value().also { commaSeparator() } }
        .then { tryParse(value) }
        .map { (xs, last) ->
            if (last != null) {
                (xs as MutableList<Any>).add(last)
            }
            TomlValue.List(xs)
        }
)

private val keyValuePair = parser {
    val k = key()
    eq()
    val v = value()
    TomlStmt.KeyValuePair(k, v)
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
    if (keyIndex >= key.lastIndex) {
        dict.putIfAbsent(key[keyIndex], value)?.also {
            fail("Key '${key.take(keyIndex + 1).joinToString()}' already defined.")
        }
    } else {
        val newDict = TomlValue.Map(mutableMapOf())
        when (val oldDict = dict.putIfAbsent(key[keyIndex], newDict)) {
            null -> insertNested(newDict, key, keyIndex + 1, value)
            is TomlValue.Map -> insertNested(oldDict, key, keyIndex + 1, value)
            else -> fail("Key '${key.take(keyIndex + 1).joinToString()}' already defined with non-map value.")
        }
    }
}

private val value: Parser<TomlValue> = oneOf(
    string,
    offsetDateTime,
    localDateTime,
    localDate,
    localTime,
    decimal,
    integer,
    boolean,
    array,
    inlineTable
)

private val bareKey = regex("[A-Za-z0-9_-]+")
private val quotedKey = oneOf(basicString, literalString)
private val key = chain1(oneOf(bareKey, quotedKey), regex("$ws\\.$ws")).map { it.terms }

private val topLevelKeyValuePair = parser {
    keyValuePair().also { eol() }
}

private val table = bracket(regex("$ws\\[$ws"), regex("$ws]"), key)
private val tableArray = bracket(regex("$ws\\[\\[$ws"), regex("$ws]]"), key)
private val nothing = parser { }

private sealed interface TomlStmt {
    data class KeyValuePair(val key: List<String>, val value: TomlValue) : TomlStmt
    data class Table(val key: List<String>) : TomlStmt
    data class TableArray(val key: List<String>) : TomlStmt
}

private val statement = parser {
    oneOf(
        topLevelKeyValuePair,
        table,
        tableArray,
        nothing
    ).also { eol() }
}

private val document = parser {
    many(statement)
}

fun main() {
    val str = """
        {foo.bar = {hej="bar"}, foo.baz = 123, foo.bar.barbar = 1}
    """.trimIndent()
    println(value.parseToEnd(str, ignoreWhitespace = true))
}
