package cc.ekblad.toml

import TomlParser
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime

internal fun TomlBuilder.extractDocument(ctx: TomlParser.DocumentContext) {
    ctx.expression().forEach { extractExpression(it) }
}

private fun TomlBuilder.extractExpression(ctx: TomlParser.ExpressionContext) {
    ctx.key_value()?.let {
        val value = extractValue(it.value())
        set(extractKey(it.key()), value)
    }
        ?: ctx.table()?.let { extractTable(it) }
        ?: ctx.comment()
        ?: error("unreachable")
}

private fun TomlBuilder.extractTable(ctx: TomlParser.TableContext) {
    ctx.standard_table()?.let { tableContext = extractKey(it.key()) }
        ?: ctx.array_table()?.let { TODO() }
        ?: error("not a table context: ${ctx::class}")
}

private fun TomlBuilder.extractValue(value: TomlParser.ValueContext): MutableTomlValue =
    value.string()?.let { MutableTomlValue.Primitive(TomlValue.String(extractString(it))) }
        ?: value.integer()?.let { MutableTomlValue.Primitive(TomlValue.Integer(extractInteger(it))) }
        ?: value.floating_point()?.let { MutableTomlValue.Primitive(TomlValue.Double(extractDouble(it))) }
        ?: value.bool_()?.let { MutableTomlValue.Primitive(TomlValue.Bool(extractBool(it))) }
        ?: value.date_time()?.let { MutableTomlValue.Primitive(extractDateTime(it)) }
        ?: value.array_()?.let { MutableTomlValue.List(extractList(it)) }
        ?: value.inline_table()?.let { MutableTomlValue.Map(extractMap(it)) }
        ?: error("unreachable")

private fun extractInteger(ctx: TomlParser.IntegerContext): Long {
    val text = ctx.text.replace("_", "")
    return when {
        ctx.DEC_INT() != null -> text.toLong(10)
        ctx.HEX_INT() != null -> text.substring(2).toLong(16)
        ctx.BIN_INT() != null -> text.substring(2).toLong(2)
        ctx.OCT_INT() != null -> text.substring(2).toLong(8)
        else -> error("unreachable")
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

private fun extractDateTime(ctx: TomlParser.Date_timeContext): TomlValue.Primitive =
    ctx.OFFSET_DATE_TIME()?.text?.let { TomlValue.OffsetDateTime(OffsetDateTime.parse(it)) }
        ?: ctx.LOCAL_DATE_TIME()?.text?.let { TomlValue.LocalDateTime(LocalDateTime.parse(it)) }
        ?: ctx.LOCAL_DATE()?.text?.let { TomlValue.LocalDate(LocalDate.parse(it)) }
        ?: ctx.LOCAL_TIME()?.text?.let { TomlValue.LocalTime(LocalTime.parse(it)) }
        ?: error("unreachable")

private fun TomlBuilder.extractList(ctx0: TomlParser.Array_Context): MutableList<MutableTomlValue> {
    val list = mutableListOf<MutableTomlValue>()
    var ctx = ctx0.array_values()
    while (ctx != null) {
        list.add(extractValue(ctx.value()))
        ctx = ctx.array_values()
    }
    return list
}

private fun TomlBuilder.extractMap(ctx0: TomlParser.Inline_tableContext): MutableMap<String, MutableTomlValue> {
    val map = mutableMapOf<String, MutableTomlValue>()
    var ctx = ctx0.inline_table_keyvals().inline_table_keyvals_non_empty()
    while (ctx != null) {
        val key = extractKey(ctx.key())
        set(map, key.first(), key.drop(1), extractValue(ctx.value()))
        ctx = ctx.inline_table_keyvals_non_empty()
    }
    return map
}

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
