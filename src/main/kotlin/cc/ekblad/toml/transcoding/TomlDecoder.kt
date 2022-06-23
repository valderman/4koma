@file:Suppress("UNCHECKED_CAST")

package cc.ekblad.toml.transcoding

import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.util.KotlinName
import cc.ekblad.toml.util.TomlName
import java.util.SortedMap
import kotlin.reflect.KClass
import kotlin.reflect.KClassifier
import kotlin.reflect.KParameter
import kotlin.reflect.KProperty1
import kotlin.reflect.KType
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.isSubclassOf
import kotlin.reflect.full.memberProperties
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

class TomlDecoder internal constructor(
    private val decoders: Map<KClass<*>, List<TomlDecoder.(KType, TomlValue) -> Any?>>,
    private val mappings: Map<KClass<*>, Map<KotlinName, TomlName>>,
    private val defaultValues: Map<KClass<*>, Any>
) {
    /**
     * Thrown by a TOML decoder function to indicate that it can't decode the given TOML into its target type and that
     * the next decoder function for the target type should be given a chance.
     */
    internal object Pass : Throwable()

    /**
     * Called by a decoder function to indicate that it can't decode the given TOML into its target type and that
     * the next decoder function for the target type should be given a chance instead.
     */
    fun pass(): Nothing = throw Pass

    internal fun mappingFor(type: KClass<*>): Map<KotlinName, TomlName> =
        mappings[type] ?: emptyMap()

    internal fun <T : Any?> decoderFor(type: KClass<*>): ((KType, TomlValue) -> T)? =
        decoders[type]?.let { decodersForType ->
            return decoder@{ type, value ->
                decodersForType.forEach { decode ->
                    try {
                        return@decoder (this.decode(type, value) as T)
                    } catch (e: Pass) {
                        /* no-op */
                    }
                }
                throw Pass
            }
        }

    internal fun defaultValueFor(type: KClass<*>, parameter: KParameter): Any? =
        defaultValues[type]?.let { defaultValue ->
            val property = type.memberProperties.single { it.name == parameter.name } as KProperty1<Any, Any>
            property.get(defaultValue)
        }
}

/**
 * Decode the given value into the given target type.
 * Behavior is undefined if [T] is not equal to or a superclass of [target].
 */
fun <T : Any?> TomlDecoder.decode(value: TomlValue, target: KType): T {
    val kClass = requireKClass(target.classifier)
    decoderFor<T>(kClass)?.let { decode ->
        try {
            return@decode decode(target, value)
        } catch (e: TomlDecoder.Pass) {
            /* no-op */
        }
    }
    return when (value) {
        is TomlValue.List -> toList(value, target)
        is TomlValue.Map -> toObject(value, target)
        is TomlValue.String -> toEnum(value, target)
        else -> throw noDecoder(value, target)
    }
}

private fun noDecoder(value: TomlValue, target: KType) =
    TomlException.DecodingError("no decoder registered for value/target pair", value, target)

fun <T : Any> toEnum(value: TomlValue.String, target: KType): T {
    val kClass = requireKClass(target.classifier)
    if (!kClass.isSubclassOf(Enum::class)) {
        throw noDecoder(value, target)
    }
    val enumValues = kClass.java.enumConstants as Array<Enum<*>>
    val enumValue = enumValues.singleOrNull { it.name == value.value }
        ?: throw TomlException.DecodingError(
            "${value.value} is not a constructor of enum class ${kClass.simpleName}",
            value,
            target
        )
    return enumValue as T
}

private val anyKType: KType = Any::class.createType()
private val stringKType: KType = String::class.createType()

private fun <T : Any> TomlDecoder.toList(value: TomlValue.List, target: KType): T =
    when (requireKClass(target.classifier)) {
        // Set/List also covers the MutableSet/MutableList cases
        List::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType) as T
        Set::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType).toSet() as T
        Collection::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType) as T
        Iterable::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType).asIterable() as T
        Any::class -> decodeList(value.elements, anyKType) as T
        else -> throw TomlException.DecodingError(
            "lists can only be decoded into lists, sets, collections or iterables",
            value,
            target
        )
    }

private fun TomlDecoder.decodeList(value: List<TomlValue>, elementType: KType): List<Any> =
    value.map { decode(it, elementType) }

private fun <T : Any> TomlDecoder.toObject(value: TomlValue.Map, target: KType): T {
    val kClass = requireKClass(target.classifier)
    return when {
        // Map also covers the MutableMap case
        kClass == Map::class -> toMap(value, target) as T
        kClass == SortedMap::class -> toMap(value, target).toSortedMap() as T
        kClass == Any::class -> toMap(value, Any::class.createType()) as T
        kClass.primaryConstructor != null -> toDataClass(value, target, kClass)
        else -> throw TomlException.DecodingError(
            "objects can only be decoded into maps, data classes, " +
                "or types for which a custom decoder function has been registered",
            value,
            target
        )
    }
}

private fun TomlDecoder.toMap(value: TomlValue.Map, targetMapType: KType): Map<String, Any> {
    if (targetMapType.arguments.firstOrNull()?.type !in setOf(null, anyKType, stringKType)) {
        throw TomlException.DecodingError(
            "when decoding an object into a map, that map must have keys of type String or Any",
            value,
            targetMapType
        )
    }
    val elementType = targetMapType.arguments.getOrNull(1)?.type ?: anyKType
    return value.properties.mapValues { decode(it.value, elementType) }
}

private fun <T : Any> TomlDecoder.toDataClass(
    tomlMap: TomlValue.Map,
    kType: KType,
    kClass: KClass<*>
): T {
    val constructor = kClass.primaryConstructor!!
    val tomlNamesByParameterName = mappingFor(kClass)
    val parameters = mutableMapOf<KParameter, Any?>()
    for (constructorParameter in constructor.parameters) {
        val tomlName = tomlNamesByParameterName[constructorParameter.name] ?: constructorParameter.name
        val encodedParameterValue = tomlMap.properties[tomlName]
        if (encodedParameterValue == null && constructorParameter.isOptional) {
            continue
        }
        val decodedParameterValue = encodedParameterValue?.let { value ->
            decode<Any?>(value, constructorParameter.type)
        }
        val parameterValue = decodedParameterValue ?: defaultValueFor(kClass, constructorParameter)
        if (!constructorParameter.type.isMarkedNullable && parameterValue == null) {
            throw TomlException.DecodingError(
                "no value found for non-nullable parameter '${constructorParameter.name}'",
                tomlMap,
                kType
            )
        }
        parameters[constructorParameter] = parameterValue
    }

    if (kClass.visibility == KVisibility.PRIVATE) {
        constructor.isAccessible = true
    }
    return constructor.callBy(parameters) as T
}

internal fun requireKClass(classifier: KClassifier?): KClass<*> =
    requireNotNull(classifier as? KClass<*>) {
        "classifier '$classifier' is not a KClass; you can only decode to concrete types"
    }
