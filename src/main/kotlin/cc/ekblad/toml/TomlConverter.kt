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
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible
import kotlin.reflect.typeOf

/**
 * Converts the receiver TOML value to the type indicated by @param T.
 *
 * TOML types can be converted to Kotlin types as follows:
 * * List: List, MutableList, Collection or Iterable
 * * Map: Map, MutableMap, SortedMap, or any data class with fields corresponding to the keys of the TOML document.
 * * Bool: Boolean
 * * Double: Double, Float or BigDecimal
 * * Integer: Int, Long, Float, Double, BigDecimal or BigInteger
 * * String: String
 * * LocalDate: LocalDate
 * * LocalTime: LocalTime
 * * LocalDateTime: LocalDateTime
 * * OffsetDateTime: OffsetDateTime
 */
@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> TomlValue.convert(): T =
    convert(typeOf<T>())

fun <T : Any> TomlValue.convert(target: KType): T = when (target.classifier) {
    TomlValue::class -> this as T
    else -> toNonTomlValue(target)
}

private fun <T : Any> TomlValue.toNonTomlValue(target: KType): T = when (this) {
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

private val anyKType: KType = Any::class.createType()
private val stringKType: KType = String::class.createType()

private fun <T : Any> TomlValue.List.toList(target: KType): T =
    when (target.classifier) {
        // List also covers the MutableList case
        List::class -> convertList(elements, target.arguments.single().type ?: anyKType) as T
        Collection::class -> convertList(elements, target.arguments.single().type ?: anyKType) as T
        Iterable::class -> convertList(elements, target.arguments.single().type ?: anyKType).asIterable() as T
        Any::class -> convertList(elements, anyKType) as T
        else -> throw TomlException.ConversionError(this, target)
    }

private fun convertList(value: List<TomlValue>, elementType: KType): List<Any> =
    value.map { it.convert(elementType) }

private fun <T : Any> TomlValue.Map.toObject(target: KType): T = when {
    // Map also covers the MutableMap case
    target.classifier == Map::class -> toMap(target) as T
    target.classifier == SortedMap::class -> toMap(target).toSortedMap() as T
    target.classifier == Any::class -> toMap(Any::class.createType()) as T
    (target.classifier as KClass<*>).primaryConstructor != null -> toDataClass(target)
    else -> throw TomlException.ConversionError(
        "objects can only be converted into maps or data classes",
        this,
        target
    )
}

private fun TomlValue.Map.toMap(targetMapType: KType): Map<String, Any> {
    if (targetMapType.arguments.firstOrNull()?.type !in setOf(null, anyKType, stringKType)) {
        throw TomlException.ConversionError(
            "when converting an object into a map, that map must have keys of type String or Any",
            this,
            targetMapType
        )
    }
    val elementType = targetMapType.arguments.getOrNull(1)?.type ?: anyKType
    return properties.mapValues { it.value.convert(elementType) }
}

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

    if (kClass.visibility == KVisibility.PRIVATE) {
        constructor.isAccessible = true
    }
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
