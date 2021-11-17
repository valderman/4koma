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

class TomlDecoder private constructor(
    private val decoders: Map<KClass<*>, List<TomlDecoder.(KType, TomlValue) -> Any?>>
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

    /**
     * Extend a TOML decoder with zero or more additional custom decoder functions.
     * A custom decoder function is a function from a [TomlValue] and a [KType] representing that a target type
     * to that target type. Custom decoder functions are associated with a [KClass] representing that target type.
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
     *     String::class to { _, tomlValue ->
     *         (tomlValue as? TomlValue.Integer)?.let { tomlValue.value.toString() } ?: pass()
     *     }
     * )
     * val result = TomlValue.from(Path.of("path", "to", "file.toml")).decode(myDecoder)
     * ```
     *
     * <br>
     *
     * Binding decoder functions to a KClass rather than a KType, while allowing the decoder function to access that
     * KType, allows for more fine-grained control over deserialization. Let's say, for instance, that you have a custom
     * data structure, generic in its elements, that you want to decode TOML values into.
     *
     * If a decoder function was bound to a KType, you would need to register one decoder function for
     * MyDataStructure<Int>, one for MyDataStructure<String>, etc. - a lot of unnecessary boilerplate.
     *
     * If a decoder function was bound to a KClass and did not have access to the corresponding KType, you would have
     * no way of knowing the type of the elements of the data structure. You would instead be forced to rely on
     * the default decoding of TOML values - [TomlValue.Integer] into [Long], [TomlValue.Map] into [Map],
     * and so on - an unacceptable loss of functionality.
     *
     * A decoder function with access to the target type's KType, bound to the target type's KClass gets the best of
     * both worlds. As an example, here is how you would create a custom decoder function for the generic data structure
     * used in the above paragraphs.
     *
     * <br>
     *
     * ```
     * val myDecoder = TomlDecoder.default.with(
     *     MyDataStructure::class to { kType, tomlValue ->
     *         (tomlValue as? TomlValue.List)?.let {
     *             val myDataStructure = MyDataStructure<Any>()
     *             tomlValue.forEach {
     *                 it.convert(this, kType.arguments.single().type!!)
     *                 myDataStructure.add(convertedElement)
     *             }
     *             myDataStructure
     *             } ?: pass()
     *     }
     * )
     * val result = TomlValue.from(Path.of("path", "to", "file.toml")).decode(myDecoder)
     * ```
     *
     * <br>
     *
     */
    fun with(
        vararg decoderFunctions: Pair<KClass<*>, TomlDecoder.(targetType: KType, tomlValue: TomlValue) -> Any?>
    ): TomlDecoder {
        val mutableDecoders = mutableMapOf<KClass<*>, MutableList<TomlDecoder.(KType, TomlValue) -> Any?>>()
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
    inline fun <reified T : TomlValue, reified R> with(
        crossinline decoderFunction: TomlDecoder.(tomlValue: T) -> R
    ): TomlDecoder =
        with(
            R::class to { _, value ->
                (value as? T)?.let { decoderFunction(it) } ?: pass()
            }
        )

    inline fun <reified T : TomlValue, reified R> with(
        crossinline decoderFunction: TomlDecoder.(targetType: KType, tomlValue: T) -> R
    ): TomlDecoder =
        with(
            R::class to { kType, value ->
                (value as? T)?.let { decoderFunction(kType, it) } ?: pass()
            }
        )

    internal fun <T : Any> decoderFor(type: KClass<T>): ((KType, TomlValue) -> T)? =
        decoders[type]?.let { decodersForType ->
            return decoder@{ type, value ->
                decodersForType.asReversed().forEach { decode ->
                    try {
                        return@decoder (this.decode(type, value) as T)
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
            defaultDecoderFunction { it: TomlValue.Integer -> it.value },
            defaultDecoderFunction { it: TomlValue.Integer -> it.value.toInt() },
            defaultDecoderFunction { it: TomlValue.Integer -> it.value.toBigInteger() },
            defaultDecoderFunction { it: TomlValue.Integer -> it.value.toFloat() },
            defaultDecoderFunction { it: TomlValue.Integer -> it.value.toDouble() },
            defaultDecoderFunction { it: TomlValue.Integer -> it.value.toBigDecimal() },
            defaultDecoderFunction { it: TomlValue.Double -> it.value },
            defaultDecoderFunction { it: TomlValue.Double -> it.value.toFloat() },
            defaultDecoderFunction { it: TomlValue.Double -> it.value.toBigDecimal() },
            defaultDecoderFunction { it: TomlValue.Bool -> it.value },
            defaultDecoderFunction { it: TomlValue.OffsetDateTime -> it.value },
            defaultDecoderFunction { it: TomlValue.LocalDateTime -> it.value },
            defaultDecoderFunction { it: TomlValue.LocalDate -> it.value },
            defaultDecoderFunction { it: TomlValue.LocalTime -> it.value },
            defaultDecoderFunction { it: TomlValue.String -> it.value },
            Any::class to { _, it ->
                when (it) {
                    is TomlValue.Bool -> it.value
                    is TomlValue.Double -> it.value
                    is TomlValue.Integer -> it.value
                    is TomlValue.String -> it.value
                    is TomlValue.LocalDate -> it.value
                    is TomlValue.LocalTime -> it.value
                    is TomlValue.LocalDateTime -> it.value
                    is TomlValue.OffsetDateTime -> it.value
                    is TomlValue.List,
                    is TomlValue.Map -> pass()
                }
            }
        )
    }
}

private inline fun <reified T> tomlValueDecoderFunction(): Pair<KClass<*>, TomlDecoder.(KType, TomlValue) -> Any?> =
    T::class to { _, it ->
        if (it !is T) {
            pass()
        }
        it
    }

private inline fun <reified T : TomlValue, reified R> defaultDecoderFunction(
    crossinline decode: (T) -> R
): Pair<KClass<*>, TomlDecoder.(KType, TomlValue) -> R> =
    R::class to { _, it ->
        if (it !is T) {
            pass()
        }
        decode(it)
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
    decoderFor(target.classifier!! as KClass<T>)?.let { decode ->
        try {
            return@decode decode(target, value)
        } catch (e: TomlDecoder.Pass) {
            /* no-op */
        }
    }
    return when (value) {
        is TomlValue.List -> toList(value, target)
        is TomlValue.Map -> toObject(value, target)
        else -> throw TomlException.DecodingError(value, target)
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
        "objects can only be decoded into maps, data classes, " +
            "or types for which a custom decoder function has been registered",
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
