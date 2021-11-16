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

class TomlConverter private constructor(private val decoders: Map<KType, List<TomlConverter.(TomlValue) -> Any?>>) {

    /**
     * Thrown by a TOML decoder to indicate that it can't convert the given TOML into its target type and that
     * the next decoder for the target type should be given a chance.
     */
    internal object Pass : Throwable()

    /**
     * Called by a custom decoder to indicate that it can't convert the given TOML into its target type and that
     * the next decoder for the target type should be given a chance instead.
     */
    fun pass(): Nothing = throw Pass

    /**
     * Extend a TOML converter with zero or more additional custom decoders.
     * A custom decoder is just a function from a [TomlValue] to some target type, associated with
     * a [KType] representing that target type.
     *
     * When a TOML value is decoded to some target type, the converter will look for all converters associated with
     * that type. All decoders matching that type are then tried in the order they were registered with the converter,
     * from newest to oldest.
     * I.e. for some converter `C = TomlConverter.default.with(T to A).with(T to B)`,
     * `B` will always be tried before `A` when trying to convert values of type `T`.
     *
     * A decoder can signal that they are unable to convert their given input by calling [pass].
     * When this happens, the converter will go on to try the next relevant decoder, if any.
     *
     * As an example, to convert a TOML document allowing integers to be converted into Kotlin strings:
     *
     * <br>
     *
     * ```
     * val myConverter = TomlConverter.default.with(
     *     typeOf<String>() to {
     *         (it as? TomlValue.Integer)?.let { it.value.toString() } ?: pass()
     *     }
     * )
     * val tomlDocument = TomlValue.from(Path.of("path", "to", "file.toml"))
     * println(myConverter.convert(tomlDocument))
     * ```
     *
     * <br>
     *
     */
    fun with(vararg newDecoders: Pair<KType, TomlConverter.(TomlValue) -> Any?>): TomlConverter {
        val mutableDecoders = mutableMapOf<KType, MutableList<TomlConverter.(TomlValue) -> Any?>>()
        decoders.mapValuesTo(mutableDecoders) { it.value.toMutableList() }
        newDecoders.forEach { (type, newDecoder) ->
            mutableDecoders.compute(type) { _, value ->
                if (value == null) {
                    mutableListOf(newDecoder)
                } else {
                    value += newDecoder
                    value
                }
            }
        }
        return TomlConverter(mutableDecoders)
    }

    @OptIn(ExperimentalStdlibApi::class)
    /**
     * Extend the target TOML converter with a single custom decoder,
     * without having to manually specify its target type.
     *
     * A decoder registered this way may specify a more specific argument type than [TomlValue]. If it does,
     * the decoder will only try to handle inputs of that specific type, automatically passing the input to the next
     * decoder in line if the input is of any other type.
     *
     * If you care about the performance of this operation, the explicitly typed overload of this function is
     * significantly faster when registering several decoders at the same time.
     */
    inline fun <reified T : TomlValue, reified R> with(crossinline decoder: TomlConverter.(T) -> R): TomlConverter =
        with(
            typeOf<R>() to { value ->
                (value as? T)?.let { decoder(it) } ?: pass()
            }
        )

    internal fun decoderFor(type: KType): ((TomlValue) -> Any?)? = decoders[type]?.let { decodersForType ->
        return decoder@{
            decodersForType.asReversed().forEach { decode ->
                try {
                    return@decoder this.decode(it)
                } catch (e: Pass) {
                    /* no-op */
                }
            }
            throw Pass
        }
    }

    companion object {
        /**
         * The default TOML converter. Handles conversion to basic Kotlin types such as Int, List, etc.
         * See [TomlValue.convert] for an exhaustive list of supported target types.
         *
         * To support custom conversions, such as remapping attribute names or massaging data into some preferred format
         * as part of the conversion process, extend this converter with new decoders using [with] and use the
         * [TomlConverter.convert], [TomlConverter.get], etc. extension functions
         * instead of their [TomlValue] counterparts.
         */
        @OptIn(ExperimentalStdlibApi::class)
        val default: TomlConverter = TomlConverter(emptyMap()).with(
            tomlValueConverter<TomlValue>(),
            tomlValueConverter<TomlValue.String>(),
            tomlValueConverter<TomlValue.Integer>(),
            tomlValueConverter<TomlValue.Double>(),
            tomlValueConverter<TomlValue.Bool>(),
            tomlValueConverter<TomlValue.OffsetDateTime>(),
            tomlValueConverter<TomlValue.LocalDateTime>(),
            tomlValueConverter<TomlValue.LocalDate>(),
            tomlValueConverter<TomlValue.LocalTime>(),
            tomlValueConverter<TomlValue.Map>(),
            tomlValueConverter<TomlValue.List>(),
        )

        @OptIn(ExperimentalStdlibApi::class)
        private inline fun <reified T> tomlValueConverter(): Pair<KType, TomlConverter.(TomlValue) -> Any?> {
            val type = typeOf<T>()
            return type to {
                if (it !is T) {
                    throw Pass
                }
                it
            }
        }
    }
}

/**
 * Converts the receiver TOML value to the type indicated by type parameter `T` using the default TOML converter.
 * If the value can't be converted into the target type, a [TomlException.ConversionError] is thrown.
 *
 * <br>
 *
 * TOML types can be converted to Kotlin types as follows:
 * * List: [List], [MutableList], [Collection] or [Iterable]
 * * Map: [Map], [MutableMap], [SortedMap], or any class with primary constructor fields corresponding
 *     to the keys of the TOML document.
 * * Bool: [Boolean]
 * * Double: [Double], [Float] or [BigDecimal]
 * * Integer: [Int], [Long], [Float], [Double], [BigDecimal] or [BigInteger]
 * * String: [String]
 * * LocalDate: [LocalDate]
 * * LocalTime: [LocalTime]
 * * LocalDateTime: [LocalDateTime]
 * * OffsetDateTime: [OffsetDateTime]
 *
 * Additionally, any subclass of [TomlValue] can always be converted into itself.
 */
@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> TomlValue.convert(): T =
    convert(typeOf<T>())

fun <T : Any> TomlValue.convert(type: KType): T =
    convert(TomlConverter.default, type)

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> TomlValue.convert(converter: TomlConverter): T =
    convert(converter, typeOf<T>())

fun <T : Any> TomlValue.convert(converter: TomlConverter, type: KType): T =
    converter.convert(this, type)

private fun <T : Any> TomlConverter.convert(value: TomlValue, target: KType): T {
    decoderFor(target)?.let { decode ->
        try {
            return@convert decode(value) as T
        } catch (e: TomlConverter.Pass) {
            /* no-op */
        }
    }
    return when (value) {
        is TomlValue.List -> toList(value, target)
        is TomlValue.Map -> toObject(value, target)
        is TomlValue.Bool -> toBoolean(value, target)
        is TomlValue.Double -> toDouble(value, target)
        is TomlValue.Integer -> toInteger(value, target)
        is TomlValue.String -> toString(value, target)
        is TomlValue.LocalDate -> toLocalDate(value, target)
        is TomlValue.LocalTime -> toLocalTime(value, target)
        is TomlValue.LocalDateTime -> toLocalDateTime(value, target)
        is TomlValue.OffsetDateTime -> toOffsetDateTime(value, target)
    }
}

private val anyKType: KType = Any::class.createType()
private val stringKType: KType = String::class.createType()

private fun <T : Any> TomlConverter.toList(value: TomlValue.List, target: KType): T =
    when (target.classifier) {
        // List also covers the MutableList case
        List::class -> convertList(value.elements, target.arguments.single().type ?: anyKType) as T
        Collection::class -> convertList(value.elements, target.arguments.single().type ?: anyKType) as T
        Iterable::class -> convertList(value.elements, target.arguments.single().type ?: anyKType).asIterable() as T
        Any::class -> convertList(value.elements, anyKType) as T
        else -> throw TomlException.ConversionError(value, target)
    }

private fun TomlConverter.convertList(value: List<TomlValue>, elementType: KType): List<Any> =
    value.map { convert(it, elementType) }

private fun <T : Any> TomlConverter.toObject(value: TomlValue.Map, target: KType): T = when {
    // Map also covers the MutableMap case
    target.classifier == Map::class -> toMap(value, target) as T
    target.classifier == SortedMap::class -> toMap(value, target).toSortedMap() as T
    target.classifier == Any::class -> toMap(value, Any::class.createType()) as T
    (target.classifier as KClass<*>).primaryConstructor != null -> toDataClass(value, target)
    else -> throw TomlException.ConversionError(
        "objects can only be converted into maps or data classes",
        value,
        target
    )
}

private fun TomlConverter.toMap(value: TomlValue.Map, targetMapType: KType): Map<String, Any> {
    if (targetMapType.arguments.firstOrNull()?.type !in setOf(null, anyKType, stringKType)) {
        throw TomlException.ConversionError(
            "when converting an object into a map, that map must have keys of type String or Any",
            value,
            targetMapType
        )
    }
    val elementType = targetMapType.arguments.getOrNull(1)?.type ?: anyKType
    return value.properties.mapValues { convert(it.value, elementType) }
}

private fun <T : Any> TomlConverter.toDataClass(value: TomlValue.Map, target: KType): T {
    val kClass = target.classifier as KClass<*>
    val constructor = kClass.primaryConstructor!!
    val parameters = constructor.parameters.map {
        val parameterValue = value.properties[it.name]
        if (!it.type.isMarkedNullable && parameterValue == null) {
            throw TomlException.ConversionError(
                "no value found for non-nullable parameter '${it.name}'",
                value,
                target
            )
        }
        parameterValue?.let { value -> convert<Any>(value, it.type) }
    }.toTypedArray()

    if (kClass.visibility == KVisibility.PRIVATE) {
        constructor.isAccessible = true
    }
    return constructor.call(*parameters) as T
}

private fun <T : Any> toBoolean(value: TomlValue.Bool, target: KType): T =
    when (target.classifier) {
        Boolean::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.ConversionError(value, target)
    } as T

private fun <T : Any> toDouble(value: TomlValue.Double, target: KType): T =
    when (target.classifier) {
        Double::class -> value.value
        Float::class -> value.value.toFloat()
        BigDecimal::class -> value.value.toBigDecimal()
        Any::class -> value.value
        else -> throw TomlException.ConversionError(value, target)
    } as T

private fun <T : Any> toInteger(value: TomlValue.Integer, target: KType): T =
    when (target.classifier) {
        Long::class -> value.value
        Int::class -> value.value.toInt()
        Double::class -> value.value.toDouble()
        Float::class -> value.value.toFloat()
        BigInteger::class -> value.value.toBigInteger()
        BigDecimal::class -> value.value.toBigDecimal()
        Any::class -> value.value
        else -> throw TomlException.ConversionError(value, target)
    } as T

private fun <T : Any> toString(value: TomlValue.String, target: KType): T =
    when (target.classifier) {
        String::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.ConversionError(value, target)
    } as T

private fun <T : Any> toLocalDate(value: TomlValue.LocalDate, target: KType): T =
    when (target.classifier) {
        LocalDate::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.ConversionError(value, target)
    } as T

private fun <T : Any> toLocalTime(value: TomlValue.LocalTime, target: KType): T =
    when (target.classifier) {
        LocalTime::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.ConversionError(value, target)
    } as T

private fun <T : Any> toLocalDateTime(value: TomlValue.LocalDateTime, target: KType): T =
    when (target.classifier) {
        LocalDateTime::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.ConversionError(value, target)
    } as T

private fun <T : Any> toOffsetDateTime(value: TomlValue.OffsetDateTime, target: KType): T =
    when (target.classifier) {
        OffsetDateTime::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.ConversionError(value, target)
    } as T
