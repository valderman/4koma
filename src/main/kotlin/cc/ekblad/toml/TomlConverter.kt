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
import kotlin.reflect.full.primaryConstructor

inline fun <reified T : Any> TomlValue.convert(): T =
    convert(T::class)

fun <T : Any> TomlValue.convert(target: KClass<T>): T = when (this) {
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

private fun <T : Any> TomlValue.List.toList(target: KClass<T>): T =
    when (target) {
        List::class -> convertList(value) as T
        Collection::class -> convertList(value) as T
        Iterable::class -> convertList(value).asIterable() as T
        MutableList::class -> convertList(value).toMutableList() as T
        Array::class -> convertList(value).toTypedArray() as T
        Any::class -> convertList(value) as T
        else -> throw TomlException.ConversionError(this, target)
    }

private fun convertList(value: List<TomlValue>): List<Any> =
    value.map { it.convert(Any::class) }

private fun <T : Any> TomlValue.Map.toObject(target: KClass<T>): T = when {
    target == Map::class -> toMap() as T
    target == MutableMap::class -> toMap().toMutableMap() as T
    target == SortedMap::class -> toMap().toSortedMap() as T
    target == Any::class -> toMap() as T
    target.isData -> toDataClass(target)
    else -> throw TomlException.ConversionError(
        "objects can only be converted into maps or data classes",
        this,
        target
    )
}

private fun TomlValue.Map.toMap(): Map<String, Any> =
    properties.mapValues { it.value.convert(Any::class) }

private fun <T : Any> TomlValue.Map.toDataClass(target: KClass<T>): T {
    val constructor = target.primaryConstructor!!
    val parameters = constructor.parameters.map {
        val parameterValue = properties[it.name]
        if (!it.type.isMarkedNullable && parameterValue == null) {
            throw TomlException.ConversionError("no value found for non-nullable parameter '${it.name}'", this, target)
        }
        val kClass = it.type.classifier as KClass<*>
        parameterValue?.convert(kClass)
    }.toTypedArray()
    return constructor.call(*parameters)
}

private fun <T : Any> TomlValue.Bool.toBoolean(target: KClass<T>): T =
    when (target) {
        Boolean::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.Double.toDouble(target: KClass<T>): T =
    when (target) {
        Double::class -> value
        Float::class -> value.toFloat()
        BigDecimal::class -> value.toBigDecimal()
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.Integer.toInteger(target: KClass<T>): T =
    when (target) {
        Long::class -> value
        Int::class -> value.toInt()
        Double::class -> value.toDouble()
        Float::class -> value.toFloat()
        BigInteger::class -> value.toBigInteger()
        BigDecimal::class -> value.toBigDecimal()
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.String.toString(target: KClass<T>): T  =
    when (target) {
        String::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.LocalDate.toLocalDate(target: KClass<T>): T  =
    when (target) {
        LocalDate::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.LocalTime.toLocalTime(target: KClass<T>): T =
    when (target) {
        LocalTime::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.LocalDateTime.toLocalDateTime(target: KClass<T>): T  =
    when (target) {
        LocalDateTime::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T

private fun <T : Any> TomlValue.OffsetDateTime.toOffsetDateTime(target: KClass<T>): T =
    when (target) {
        OffsetDateTime::class -> value
        Any::class -> value
        else -> throw TomlException.ConversionError(this, target)
    } as T
