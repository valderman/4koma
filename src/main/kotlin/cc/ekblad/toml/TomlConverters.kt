package cc.ekblad.toml

import TomlParser
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
    ctx.key_value()?.let { set(ctx.start.line, extractKey(it.key()), extractValue(it.value())) }
        ?: ctx.table()?.let { extractTable(it) }
        ?: ctx.comment()
        ?: error("unreachable")
}

private fun TomlBuilder.extractTable(ctx: TomlParser.TableContext) {
    ctx.standard_table()?.let { defineTable(ctx.start.line, extractKey(it.key())) }
        ?: ctx.array_table()?.let { addTableArrayEntry(ctx.start.line, extractKey(it.key())) }
        ?: error("not a table context: ${ctx::class}")
}

private fun extractValue(value: TomlParser.ValueContext): MutableTomlValue =
    value.string()?.let { MutableTomlValue.Primitive(TomlValue.String(extractString(it))) }
        ?: value.integer()?.let { MutableTomlValue.Primitive(TomlValue.Integer(extractInteger(it))) }
        ?: value.floating_point()?.let { MutableTomlValue.Primitive(TomlValue.Double(extractDouble(it))) }
        ?: value.bool_()?.let { MutableTomlValue.Primitive(TomlValue.Bool(extractBool(it))) }
        ?: value.date_time()?.let { MutableTomlValue.Primitive(extractDateTime(it)) }
        ?: value.array_()?.let { MutableTomlValue.InlineList(extractList(it)) }
        ?: value.inline_table()?.let { MutableTomlValue.InlineMap(extractInlineTable(it)) }
        ?: error("unreachable")

private fun extractInteger(ctx: TomlParser.IntegerContext): Long {
    val text = ctx.text.replace("_", "")
    try {
        return when {
            ctx.DEC_INT() != null -> text.toLong(10)
            ctx.HEX_INT() != null -> text.substring(2).toLong(16)
            ctx.BIN_INT() != null -> text.substring(2).toLong(2)
            ctx.OCT_INT() != null -> text.substring(2).toLong(8)
            else -> error("unreachable")
        }
    } catch (e: NumberFormatException) {
        throw TomlException("integer '${ctx.text}' is out of range", ctx.start.line, e)
    }
}

private fun extractDouble(ctx: TomlParser.Floating_pointContext): Double =
    ctx.FLOAT()?.text?.replace("_", "")?.toDouble()
        ?: ctx.INF()?.text?.parseInfinity()
        ?: ctx.NAN()?.let { Double.NaN }
        ?: error("unreachable")

private fun String.parseInfinity(): Double = when (first()) {
    '-' -> Double.NEGATIVE_INFINITY
    else -> Double.POSITIVE_INFINITY
}

private fun extractBool(ctx: TomlParser.Bool_Context): Boolean =
    ctx.BOOLEAN().text.toBooleanStrict()

private fun extractDateTime(ctx: TomlParser.Date_timeContext): TomlValue.Primitive = try {
    ctx.OFFSET_DATE_TIME()?.text?.let { TomlValue.OffsetDateTime(OffsetDateTime.parse(it.replace(' ', 'T'))) }
        ?: ctx.LOCAL_DATE_TIME()?.text?.let { TomlValue.LocalDateTime(LocalDateTime.parse(it.replace(' ', 'T'))) }
        ?: ctx.LOCAL_DATE()?.text?.let { TomlValue.LocalDate(LocalDate.parse(it)) }
        ?: ctx.LOCAL_TIME()?.text?.let { TomlValue.LocalTime(LocalTime.parse(it)) }
        ?: error("unreachable")
} catch (e: DateTimeParseException) {
    throw TomlException("date/time '${ctx.text}' has invalid format", ctx.start.line, e)
}

private fun extractList(ctx0: TomlParser.Array_Context): TomlValue.List {
    val list = mutableListOf<TomlValue>()
    var ctx = ctx0.array_values()
    while (ctx != null) {
        list.add(extractValue(ctx.value()).freeze())
        ctx = ctx.array_values()
    }
    return TomlValue.List(list)
}

private fun extractInlineTable(ctx0: TomlParser.Inline_tableContext): TomlValue.Map =
    TomlBuilder.create().apply {
        var ctx = ctx0.inline_table_keyvals().inline_table_keyvals_non_empty()
        while (ctx != null) {
            val key = extractKey(ctx.key())
            set(ctx.start.line, key, extractValue(ctx.value()))
            ctx = ctx.inline_table_keyvals_non_empty()
        }
    }.build()

// TODO: escape sequences
private fun extractString(ctx: TomlParser.StringContext): String =
    ctx.BASIC_STRING()?.text?.let { it.substring(1, it.length - 1) }
        ?: ctx.ML_BASIC_STRING()?.text?.let { it.substring(3, it.length - 3).trimFirst() }
        ?: ctx.LITERAL_STRING()?.text?.let { it.substring(1, it.length - 1) }
        ?: ctx.ML_LITERAL_STRING()?.text?.let { it.substring(3, it.length - 3).trimFirst() }
        ?: error("unreachable")

private fun String.trimFirst(): String = when {
    isEmpty() -> this
    first() == '\n' -> drop(1)
    substring(0, 2.coerceAtMost(length)) == "\r\n" -> drop(2)
    else -> this
}

private fun extractKey(ctx: TomlParser.KeyContext): List<String> =
    ctx.simple_key()?.let { listOf(extractSimpleKey(it)) }
        ?: ctx.dotted_key()?.let { ctx.dotted_key().simple_key().map { extractSimpleKey(it) } }
        ?: error("unreachable")

private fun extractSimpleKey(ctx: TomlParser.Simple_keyContext): String =
    ctx.quoted_key()?.text?.trim('"', '\'')
        ?: ctx.unquoted_key()?.UNQUOTED_KEY()?.text
        ?: error("unreachable")
