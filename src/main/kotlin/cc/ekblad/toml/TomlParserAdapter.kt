package cc.ekblad.toml

import TomlParser
import org.antlr.v4.runtime.ParserRuleContext
import java.lang.NumberFormatException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

internal fun TomlBuilder.extractDocument(ctx: TomlParser.DocumentContext) {
    ctx.expression().forEach { extractExpression(it) }
}

private fun TomlBuilder.extractExpression(ctx: TomlParser.ExpressionContext) {
    ctx.key_value()?.let { extractKeyValue(it) }
        ?: ctx.table()?.standard_table()?.let { extractTable(it) }
        ?: ctx.table()?.array_table()?.let { extractArrayTable(it) }
        ?: ctx.comment().text.throwOnBadChar(ctx, '\r', '\n')
}

private fun TomlBuilder.extractKeyValue(ctx: TomlParser.Key_valueContext) {
    val keyFragments = ctx.key().extractKey()
    val tableContext = defineTable(ctx.start.line, keyFragments.dropLast(1), false)
    tableContext.set(ctx.start.line, keyFragments.last(), extractValue(ctx.value()))
}

private fun TomlBuilder.extractTable(ctx: TomlParser.Standard_tableContext) {
    val keyFragments = ctx.key().extractKey()
    val key = keyFragments.last()
    resetContext()
    val tableContext = defineTable(ctx.start.line, keyFragments.dropLast(1), true)
    val newTableContext = tableContext.subcontext(key) ?: TomlBuilder.Context.new()
    tableContext.set(ctx.start.line, key, newTableContext.asMap())
    setContext(newTableContext)
}

private fun TomlBuilder.extractArrayTable(ctx: TomlParser.Array_tableContext) {
    throwOnWhitespaceBetweenBrackets(ctx)
    val keyFragments = ctx.key().extractKey()
    resetContext()
    val tableContext = defineTable(ctx.start.line, keyFragments.dropLast(1), true)
    val newTableContext = tableContext.addTableArrayEntry(ctx.start.line, keyFragments.last())
    setContext(newTableContext)
}

/** Spec demands a parse error if there is space between the opening or closing brackets of a table array
 * header, but we can't get the lexer/parser to reject that for us so we have to do it here instead.
 */
private fun throwOnWhitespaceBetweenBrackets(ctx: TomlParser.Array_tableContext) {
    val keyLength = ctx.key().stop.stopIndex - ctx.key().start.charPositionInLine
    val headerLength = ctx.stop.stopIndex - ctx.start.charPositionInLine
    val doubleBracketChars = headerLength - keyLength
    if (doubleBracketChars > 4) {
        throw TomlException.ParseError(
            "whitespace between table array-defining double brackets is illegal",
            ctx.start.line
        )
    }
}

private fun extractValue(value: TomlParser.ValueContext): MutableTomlValue =
    value.string()?.let { MutableTomlValue.Primitive(TomlValue.String(it.extractString())) }
        ?: value.integer()?.let { MutableTomlValue.Primitive(TomlValue.Integer(it.extractInteger())) }
        ?: value.floating_point()?.let { MutableTomlValue.Primitive(TomlValue.Double(it.extractDouble())) }
        ?: value.bool_()?.let { MutableTomlValue.Primitive(TomlValue.Bool(it.extractBool())) }
        ?: value.date_time()?.let { MutableTomlValue.Primitive(it.extractDateTime()) }
        ?: value.array_()?.let { MutableTomlValue.InlineList(it.extractList()) }
        ?: value.inline_table().let { MutableTomlValue.InlineMap(it.extractInlineTable()) }

private fun TomlParser.IntegerContext.extractInteger(): Long {
    val text = text.replace("_", "")
    try {
        return when {
            DEC_INT() != null -> text.toLong(10)
            HEX_INT() != null -> text.substring(2).toLong(16)
            BIN_INT() != null -> text.substring(2).toLong(2)
            OCT_INT() != null -> text.substring(2).toLong(8)
            else -> error("unreachable")
        }
    } catch (e: NumberFormatException) {
        throw TomlException.ParseError("integer '$text' is out of range", start.line, e)
    }
}

private fun TomlParser.Floating_pointContext.extractDouble(): Double =
    FLOAT()?.let { it.text.replace("_", "").toDouble() }
        ?: INF()?.let { text.parseInfinity() }
        ?: NAN().let { Double.NaN }

private fun String.parseInfinity(): Double = when (first()) {
    '-' -> Double.NEGATIVE_INFINITY
    else -> Double.POSITIVE_INFINITY
}

private fun TomlParser.Bool_Context.extractBool(): Boolean =
    BOOLEAN().text.toBooleanStrict()

private fun TomlParser.Date_timeContext.extractDateTime(): TomlValue.Primitive = try {
    OFFSET_DATE_TIME()?.let { TomlValue.OffsetDateTime(OffsetDateTime.parse(it.text.replace(' ', 'T'))) }
        ?: LOCAL_DATE_TIME()?.let { TomlValue.LocalDateTime(LocalDateTime.parse(it.text.replace(' ', 'T'))) }
        ?: LOCAL_DATE()?.let { TomlValue.LocalDate(LocalDate.parse(it.text)) }
        ?: LOCAL_TIME().let { TomlValue.LocalTime(LocalTime.parse(it.text)) }
} catch (e: DateTimeParseException) {
    throw TomlException.ParseError("date/time '$text' has invalid format", start.line, e)
}

private fun TomlParser.Array_Context.extractList(): TomlValue.List {
    val list = mutableListOf<TomlValue>()
    var ctx = array_values()
    while (ctx != null) {
        list.add(extractValue(ctx.value()).freeze())
        ctx = ctx.array_values()
    }
    return TomlValue.List(list)
}

private fun TomlParser.Inline_tableContext.extractInlineTable(): TomlValue.Map =
    TomlBuilder.create().apply {
        var ctx = inline_table_keyvals().inline_table_keyvals_non_empty()
        while (ctx != null) {
            val keyFragments = ctx.key().extractKey()
            val tableContext = defineTable(ctx.start.line, keyFragments.dropLast(1), false)
            tableContext.set(ctx.start.line, keyFragments.last(), extractValue(ctx.value()))
            ctx = ctx.inline_table_keyvals_non_empty()
        }
    }.build()

private fun TomlParser.StringContext.extractString(): String =
    BASIC_STRING()?.text?.stripQuotes(1)?.throwOnBadChar(this)?.convertEscapeCodes(start.line)
        ?: ML_BASIC_STRING()?.text?.stripQuotes(3)?.trimFirstNewline()?.throwOnBadChar(this)?.convertEscapeCodes(start.line)
        ?: LITERAL_STRING()?.text?.stripQuotes(1)?.throwOnBadChar(this, '\r', '\n')
        ?: ML_LITERAL_STRING().text.stripQuotes(3).trimFirstNewline().throwOnBadChar(this)

private fun TomlParser.KeyContext.extractKey(): List<String> =
    simple_key()?.extractSimpleKey()
        ?: dotted_key().extractDottedKey()

private fun TomlParser.Dotted_keyContext.extractDottedKey(): List<String> =
    simple_key().flatMap { it.extractSimpleKey() }

private fun TomlParser.Simple_keyContext.extractSimpleKey(): List<String> =
    quoted_key()?.extractQuotedKey()
        ?: unquoted_key().extractUnquotedKey()

private fun TomlParser.Quoted_keyContext.extractQuotedKey(): List<String> =
    BASIC_STRING()?.let {
        listOf(
            it.text.stripQuotes(1)
                .throwOnBadChar(this, '\r', '\n')
                .convertEscapeCodes(start.line)
        )
    } ?: LITERAL_STRING().let {
        listOf(it.text.stripQuotes(1).throwOnBadChar(this, '\r', '\n'))
    }

/**
 * Because of the parser hack required to support keys that can overlap with values, we need to deal with the fact
 * that some "simple" keys may actually be dotted keys, and that the parser lets '+' signs through.
 */
private fun TomlParser.Unquoted_keyContext.extractUnquotedKey(): List<String> {
    val fragments = text.split('.')
    if (fragments.any { it.contains('+') }) {
        throw TomlException.ParseError("illegal character '+' encountered in key", start.line)
    }
    return fragments
}

private fun String.throwOnBadChar(ctx: ParserRuleContext, vararg extraBadChars: Char): String {
    val encounteredInvalidChars = (invalidChars + extraBadChars.toList()).filter { it in this }
    if (encounteredInvalidChars.isNotEmpty()) {
        val badChars = encounteredInvalidChars.joinToString(", ") { it.code.toString() }
        throw TomlException.ParseError(
            "disallowed character(s) encountered: $badChars",
            ctx.start.line
        )
    }
    return this
}

private fun String.stripQuotes(quoteSize: Int): String =
    substring(quoteSize, length - quoteSize)

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
    'U' -> String(Character.toChars(match.groupValues[3].toInt(16).throwOnNonScalar(line)))
    else -> error("unreachable")
}

/**
 * Unicode surrogate characters are not valid escape codes.
 */
private fun Int.throwOnNonScalar(line: Int): Int = apply {
    if (this in 0xD800..0xDFFF) {
        throw TomlException.ParseError("surrogate character '$this' is not a valid escape code", line)
    }
}

private val escapeRegex = Regex("\\\\([\\\\\"bnfrt]|u([0-9a-fA-F]{4})|U([0-9a-fA-F]{8}))")

private val invalidChars = listOf(
    '\u0000'..'\u0008',
    '\u000B'..'\u000C',
    '\u000E'..'\u001F',
    listOf('\u007F', '�'),
).flatten()
