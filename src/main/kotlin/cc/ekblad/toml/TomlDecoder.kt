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

class TomlDecoder private constructor(private val decoders: Map<KType, List<TomlDecoder.(TomlValue) -> Any?>>) {

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

    /**
     * Extend a TOML decoder with zero or more additional custom decoder functions.
     * A custom decoder function is a function from a [TomlValue] to some target type, associated with
     * a [KType] representing that target type.
     *
     * When a TOML value is decoded to some target type, the decoder will look for all decoder functions associated with
     * that type. All decoder functions matching that type are then tried in the order
     * they were registered with the decoder, from newest to oldest.
     * I.e. for some decoder `D = TomlDecoder.default.with(T to A).with(T to B)`,
     * `B` will always be tried before `A` when trying to decode values of type `T`.
     *
     * A decoder function can signal that they are unable to decode their given input by calling [pass].
     * When this happens, the decoder will go on to try the next relevant decoder, if any.
     *
     * As an example, to decode a TOML document allowing integers to be decoded into Kotlin strings:
     *
     * <br>
     *
     * ```
     * val myDecoder = TomlDecoder.default.with(
     *     typeOf<String>() to {
     *         (it as? TomlValue.Integer)?.let { it.value.toString() } ?: pass()
     *     }
     * )
     * val result = TomlValue.from(Path.of("path", "to", "file.toml")).decode(myDecoder)
     * ```
     *
     * <br>
     *
     */
    fun with(vararg decoderFunctions: Pair<KType, TomlDecoder.(TomlValue) -> Any?>): TomlDecoder {
        val mutableDecoders = mutableMapOf<KType, MutableList<TomlDecoder.(TomlValue) -> Any?>>()
        decoders.mapValuesTo(mutableDecoders) { it.value.toMutableList() }
        decoderFunctions.forEach { (type, newDecoder) ->
            mutableDecoders.compute(type) { _, value ->
                if (value == null) {
                    mutableListOf(newDecoder)
                } else {
                    value += newDecoder
                    value
                }
            }
        }
        return TomlDecoder(mutableDecoders)
    }

    @OptIn(ExperimentalStdlibApi::class)
    /**
     * Extend the receiver TOML decoder with a single custom decoder function,
     * without having to manually specify its target type.
     *
     * A decoder function registered this way may specify a more specific argument type than [TomlValue]. If it does,
     * the function will only try to handle inputs of that specific type, automatically passing the input to the next
     * decoder function in line if the input is of any other type.
     *
     * If you care about the performance of this operation, the explicitly typed overload of this function is
     * significantly faster when registering several decoder functions at the same time.
     */
    inline fun <reified T : TomlValue, reified R> with(crossinline decoderFunction: TomlDecoder.(T) -> R): TomlDecoder =
        with(
            typeOf<R>() to { value ->
                (value as? T)?.let { decoderFunction(it) } ?: pass()
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
         * The default TOML decoder. Handles decoding to basic Kotlin types such as Int, List, etc.
         * See [TomlValue.decode] for an exhaustive list of supported target types.
         *
         * To support custom decoders, such as remapping attribute names or massaging data into some preferred format
         * as part of the decoding process, extend this decoder with new decoder functions using [with] and pass the
         * result to the [TomlValue.decode], [TomlValue.get], etc. functions.
         */
        @OptIn(ExperimentalStdlibApi::class)
        val default: TomlDecoder = TomlDecoder(emptyMap()).with(
            tomlValueDecoderFunction<TomlValue>(),
            tomlValueDecoderFunction<TomlValue.String>(),
            tomlValueDecoderFunction<TomlValue.Integer>(),
            tomlValueDecoderFunction<TomlValue.Double>(),
            tomlValueDecoderFunction<TomlValue.Bool>(),
            tomlValueDecoderFunction<TomlValue.OffsetDateTime>(),
            tomlValueDecoderFunction<TomlValue.LocalDateTime>(),
            tomlValueDecoderFunction<TomlValue.LocalDate>(),
            tomlValueDecoderFunction<TomlValue.LocalTime>(),
            tomlValueDecoderFunction<TomlValue.Map>(),
            tomlValueDecoderFunction<TomlValue.List>(),
        )

        @OptIn(ExperimentalStdlibApi::class)
        private inline fun <reified T> tomlValueDecoderFunction(): Pair<KType, TomlDecoder.(TomlValue) -> Any?> {
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
 * Decodes the receiver TOML value to the type indicated by type parameter `T` using the default TOML decoder.
 * If the value can't be decoded into the target type, a [TomlException.DecodingError] is thrown.
 *
 * <br>
 *
 * TOML types can be decoded to Kotlin types as follows:
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
 * Additionally, any subclass of [TomlValue] can always be decoded into itself.
 */
@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> TomlValue.decode(): T =
    decode(typeOf<T>())

fun <T : Any> TomlValue.decode(type: KType): T =
    decode(TomlDecoder.default, type)

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> TomlValue.decode(decoder: TomlDecoder): T =
    decode(decoder, typeOf<T>())

fun <T : Any> TomlValue.decode(decoder: TomlDecoder, type: KType): T =
    decoder.decode(this, type)

private fun <T : Any> TomlDecoder.decode(value: TomlValue, target: KType): T {
    decoderFor(target)?.let { decode ->
        try {
            return@decode decode(value) as T
        } catch (e: TomlDecoder.Pass) {
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

private fun <T : Any> TomlDecoder.toList(value: TomlValue.List, target: KType): T =
    when (target.classifier) {
        // List also covers the MutableList case
        List::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType) as T
        Collection::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType) as T
        Iterable::class -> decodeList(value.elements, target.arguments.single().type ?: anyKType).asIterable() as T
        Any::class -> decodeList(value.elements, anyKType) as T
        else -> throw TomlException.DecodingError(value, target)
    }

private fun TomlDecoder.decodeList(value: List<TomlValue>, elementType: KType): List<Any> =
    value.map { decode(it, elementType) }

private fun <T : Any> TomlDecoder.toObject(value: TomlValue.Map, target: KType): T = when {
    // Map also covers the MutableMap case
    target.classifier == Map::class -> toMap(value, target) as T
    target.classifier == SortedMap::class -> toMap(value, target).toSortedMap() as T
    target.classifier == Any::class -> toMap(value, Any::class.createType()) as T
    (target.classifier as KClass<*>).primaryConstructor != null -> toDataClass(value, target)
    else -> throw TomlException.DecodingError(
        "objects can only be decoded into maps or data classes",
        value,
        target
    )
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

private fun <T : Any> TomlDecoder.toDataClass(value: TomlValue.Map, target: KType): T {
    val kClass = target.classifier as KClass<*>
    val constructor = kClass.primaryConstructor!!
    val parameters = constructor.parameters.map {
        val parameterValue = value.properties[it.name]
        if (!it.type.isMarkedNullable && parameterValue == null) {
            throw TomlException.DecodingError(
                "no value found for non-nullable parameter '${it.name}'",
                value,
                target
            )
        }
        parameterValue?.let { value -> decode<Any>(value, it.type) }
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
        else -> throw TomlException.DecodingError(value, target)
    } as T

private fun <T : Any> toDouble(value: TomlValue.Double, target: KType): T =
    when (target.classifier) {
        Double::class -> value.value
        Float::class -> value.value.toFloat()
        BigDecimal::class -> value.value.toBigDecimal()
        Any::class -> value.value
        else -> throw TomlException.DecodingError(value, target)
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
        else -> throw TomlException.DecodingError(value, target)
    } as T

private fun <T : Any> toString(value: TomlValue.String, target: KType): T =
    when (target.classifier) {
        String::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.DecodingError(value, target)
    } as T

private fun <T : Any> toLocalDate(value: TomlValue.LocalDate, target: KType): T =
    when (target.classifier) {
        LocalDate::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.DecodingError(value, target)
    } as T

private fun <T : Any> toLocalTime(value: TomlValue.LocalTime, target: KType): T =
    when (target.classifier) {
        LocalTime::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.DecodingError(value, target)
    } as T

private fun <T : Any> toLocalDateTime(value: TomlValue.LocalDateTime, target: KType): T =
    when (target.classifier) {
        LocalDateTime::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.DecodingError(value, target)
    } as T

private fun <T : Any> toOffsetDateTime(value: TomlValue.OffsetDateTime, target: KType): T =
    when (target.classifier) {
        OffsetDateTime::class -> value.value
        Any::class -> value.value
        else -> throw TomlException.DecodingError(value, target)
    } as T
