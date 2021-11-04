@file:Suppress("UNCHECKED_CAST")

package cc.ekblad.toml

import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.SortedMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.createType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.typeOf

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> TomlValue.convert(): T =
    convert(typeOf<T>())

fun <T : Any> TomlValue.convert(target: KType): T = when (this) {
    is TomlValue.List -> toList(target)
    is TomlValue.Map -> toObject(target)
    is TomlValue.Bool -> toBoolean(target)
    is TomlValue.Double -> toDouble(target)
    is TomlValue.Integer -> toInteger(target)
    is TomlValue.String -> toString(target)
    is TomlValue.LocalDate -> toLocalDate(target)
    is TomlValue.LocalTime -> toLocalTime(target)
    is TomlValue.LocalDateTime -> toLocalDateTime(target)
    is TomlValue.OffsetDateTime -> toOffsetDateTime(target)
}

private fun <T : Any> TomlValue.List.toList(target: KType): T =
    when (target.classifier) {
        List::class -> convertList(elements, target.arguments.single().type!!) as T
        Collection::class -> convertList(elements, target.arguments.single().type!!) as T
        Iterable::class -> convertList(elements, target.arguments.single().type!!).asIterable() as T
        MutableList::class -> convertList(elements, target.arguments.single().type!!).toMutableList() as T
        Array::class -> convertList(elements, target.arguments.single().type!!).toTypedArray() as T
        Any::class -> convertList(elements, target.arguments.single().type!!) as T
        else -> throw TomlException.ConversionError(this, target)
    }

private fun convertList(value: List<TomlValue>, elementType: KType): List<Any> =
    value.map { it.convert(elementType) }

private fun <T : Any> TomlValue.Map.toObject(target: KType): T = when {
    target.classifier == Map::class -> toMap(target.arguments[1].type!!) as T
    target.classifier == MutableMap::class -> toMap(target.arguments[1].type!!).toMutableMap() as T
    target.classifier == SortedMap::class -> toMap(target.arguments[1].type!!).toSortedMap() as T
    target.classifier == Any::class -> toMap(Any::class.createType()) as T
    (target.classifier as KClass<*>).isData -> toDataClass(target)
    else -> throw TomlException.ConversionError(
        "objects can only be converted into maps or data classes",
        this,
        target
    )
}

private fun TomlValue.Map.toMap(elementType: KType): Map<String, Any> =
    properties.mapValues { it.value.convert(elementType) }

private fun <T : Any> TomlValue.Map.toDataClass(target: KType): T {
    val kClass = target.classifier as KClass<*>
    val constructor = kClass.primaryConstructor!!
    val parameters = constructor.parameters.map {
        val parameterValue = properties[it.name]
        if (!it.type.isMarkedNullable && parameterValue == null) {
            throw TomlException.ConversionError("no value found for non-nullable parameter '${it.name}'", this, target)
        }
        parameterValue?.convert<Any>(it.type)
    }.toTypedArray()
    return constructor.call(*parameters) as T
}

private fun <T : Any> TomlValue.Bool.toBoolean(target: KType): T =
    when (target.classifier) {
        Boolean::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.Double.toDouble(target: KType): T =
    when (target.classifier) {
        Double::class -> value
        Float::class -> value.toFloat()
        BigDecimal::class -> value.toBigDecimal()
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.Integer.toInteger(target: KType): T =
    when (target.classifier) {
        Long::class -> value
        Int::class -> value.toInt()
        Double::class -> value.toDouble()
        Float::class -> value.toFloat()
        BigInteger::class -> value.toBigInteger()
        BigDecimal::class -> value.toBigDecimal()
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.String.toString(target: KType): T =
    when (target.classifier) {
        String::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.LocalDate.toLocalDate(target: KType): T =
    when (target.classifier) {
        LocalDate::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.LocalTime.toLocalTime(target: KType): T =
    when (target.classifier) {
        LocalTime::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.LocalDateTime.toLocalDateTime(target: KType): T =
    when (target.classifier) {
        LocalDateTime::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.OffsetDateTime.toOffsetDateTime(target: KType): T =
    when (target.classifier) {
        OffsetDateTime::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T
