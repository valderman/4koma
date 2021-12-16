package cc.ekblad.toml.transcoding

import cc.ekblad.toml.TomlException
import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.util.Generated
import cc.ekblad.toml.util.KotlinName
import cc.ekblad.toml.util.TomlName
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import kotlin.reflect.KClass
import kotlin.reflect.KProperty1
import kotlin.reflect.KVisibility
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

/**
 * Describes how Kotlin model types are encoded into [TomlValue]s.
 * This API closely mirrors that of [TomlDecoder].
 */
class TomlEncoder private constructor(
    private val encoders: Map<KClass<*>, List<TomlEncoder.(Any) -> TomlValue>>,
    private val mappings: Map<KClass<*>, Map<KotlinName, TomlName>>,
) {
    /**
     * Thrown by an encoder function to indicate that it can't encode the given value, and that
     * the next encoder function for the source type should be given a chance instead.
     */
    internal object Pass : Throwable()

    /**
     * Called by an encoder function to indicate that it can't encode the given value, and that
     * the next encoder function for the source type should be given a chance instead.
     */
    fun pass(): Nothing = throw Pass

    /**
     * Returns a copy of the target TOML encoder, extended with zero or more additional custom encoder functions.
     * A custom encoder function is a function from some Kotlin value to a [TomlValue].
     * Custom encoder functions are associated with a [KClass] representing the source type.
     *
     */
    fun with(
        vararg encoderFunctions: Pair<KClass<*>, TomlEncoder.(kotlinValue: Any) -> TomlValue>
    ): TomlEncoder {
        val mutableEncoders = mutableMapOf<KClass<*>, MutableList<TomlEncoder.(Any) -> TomlValue>>()
        encoders.mapValuesTo(mutableEncoders) { it.value.toMutableList() }
        encoderFunctions.forEach { (type, newEncoder) ->
            mutableEncoders.compute(type) { _, value ->
                if (value == null) {
                    mutableListOf(newEncoder)
                } else {
                    value += newEncoder
                    value
                }
            }
        }
        return TomlEncoder(mutableEncoders, mappings)
    }

    /**
     * Returns a copy of the receiver TOML encoder extended with a single custom encoder function,
     * without having to manually specify its target type.
     */
    inline fun <reified T> with(
        crossinline encoderFunction: TomlEncoder.(kotlinValue: T) -> TomlValue
    ): TomlEncoder =
        with(
            T::class to @Generated { value ->
                if (value !is T) {
                    pass()
                }
                encoderFunction(value)
            }
        )

    /**
     * Returns a copy of the receiver TOML encoder, extended with a custom property mapping for the type `T`.
     * Mappings are given on the form `"kotlinName" to "tomlName"`.
     *
     * See [TomlDecoder.withMapping] for more information about custom mappings.
     */
    inline fun <reified T : Any> withMapping(vararg mapping: Pair<KotlinName, TomlName>): TomlEncoder =
        withMapping(T::class, *mapping)

    /**
     * Returns a copy of the receiver TOML encoder, extended with a custom property mapping for the type indicated
     * by the given `KClass`.
     * Mappings are given on the form `"kotlinName" to "tomlName"`.
     *
     * See [TomlDecoder.withMapping] for more information about custom mappings.
     */
    fun <T : Any> withMapping(kClass: KClass<T>, vararg mapping: Pair<KotlinName, TomlName>): TomlEncoder {
        require(kClass.isData) {
            "type ${kClass.qualifiedName ?: kClass.simpleName} is not a data class"
        }

        val propertyNames = kClass.declaredMemberProperties.map { it.name }.toSet()
        val missingPropertyNames = mapping.filter { it.first !in propertyNames }
        require(missingPropertyNames.isEmpty()) {
            val missingProperties = missingPropertyNames.joinToString(", ") { it.first }
            val className = kClass.qualifiedName ?: kClass.simpleName
            "the following properties do not exist on type $className: $missingProperties"
        }

        val updatedMappings = mappings.toMutableMap()
        updatedMappings.compute(kClass) { _, previousMapping ->
            (previousMapping ?: emptyMap()) + mapping
        }
        return TomlEncoder(encoders, updatedMappings)
    }

    internal fun mappingFor(type: KClass<*>): Map<KotlinName, TomlName> =
        mappings[type] ?: emptyMap()

    internal fun encoderFor(type: KClass<*>): ((Any) -> TomlValue)? =
        encoders[type]?.let { encodersForType ->
            return encoder@{ value ->
                encodersForType.asReversed().forEach { encode ->
                    try {
                        return@encoder this.encode(value)
                    } catch (e: Pass) {
                        /* no-op */
                    }
                }
                throw Pass
            }
        }

    companion object {
        /**
         * The default TOML encoder. Handles decoding to basic Kotlin types such as Int, List, etc.
         * Mirrors [TomlDecoder.default].
         * See [TomlValue.decode] for an exhaustive list of supported source types.
         */
        val default: TomlEncoder = TomlEncoder(emptyMap(), emptyMap()).with(
            defaultEncoderFunction { it: TomlValue.List -> it },
            defaultEncoderFunction { it: TomlValue.Map -> it },
            defaultEncoderFunction { it: TomlValue.Integer -> it },
            defaultEncoderFunction { it: TomlValue.Double -> it },
            defaultEncoderFunction { it: TomlValue.String -> it },
            defaultEncoderFunction { it: TomlValue.Bool -> it },
            defaultEncoderFunction { it: TomlValue.LocalDate -> it },
            defaultEncoderFunction { it: TomlValue.LocalTime -> it },
            defaultEncoderFunction { it: TomlValue.LocalDateTime -> it },
            defaultEncoderFunction { it: TomlValue.OffsetDateTime -> it },
            defaultEncoderFunction { it: Long -> TomlValue.Integer(it) },
            defaultEncoderFunction { it: Int -> TomlValue.Integer(it.toLong()) },
            defaultEncoderFunction { it: BigInteger -> TomlValue.Integer(it.toLong()) },
            defaultEncoderFunction { it: Float -> TomlValue.Double(it.toDouble()) },
            defaultEncoderFunction { it: Double -> TomlValue.Double(it) },
            defaultEncoderFunction { it: BigDecimal -> TomlValue.Double(it.toDouble()) },
            defaultEncoderFunction { it: Boolean -> TomlValue.Bool(it) },
            defaultEncoderFunction { it: OffsetDateTime -> TomlValue.OffsetDateTime(it) },
            defaultEncoderFunction { it: LocalDateTime -> TomlValue.LocalDateTime(it) },
            defaultEncoderFunction { it: LocalDate -> TomlValue.LocalDate(it) },
            defaultEncoderFunction { it: LocalTime -> TomlValue.LocalTime(it) },
            defaultEncoderFunction { it: String -> TomlValue.String(it) },
        )
    }
}

private inline fun <reified T : Any> defaultEncoderFunction(
    crossinline encode: (T) -> TomlValue
): Pair<KClass<*>, TomlEncoder.(Any) -> TomlValue> =
    T::class to @Generated {
        if (it !is T) {
            pass()
        }
        encode(it)
    }

/**
 * Encodes the given Kotlin value to as into a [TomlValue] using the receiver [TomlEncoder].
 * If the value can't be encoded, a [TomlException.EncodingError] is thrown.
 *
 * <br>
 *
 * Kotlin types can be encoded into TOML types as follows:
 * * [Iterable]: [TomlValue.List]
 * * [Map], or any data class: [TomlValue.Map]
 * * [Boolean]: [TomlValue.Bool]
 * * [Double], [Float], [BigDecimal]: [TomlValue.Double]
 * * [Int], [Long], [BigInteger]: [TomlValue.Integer]
 * * [String]: [TomlValue.String]
 * * [LocalDate]: [TomlValue.LocalDate]
 * * [LocalTime]: [TomlValue.LocalTime]
 * * [LocalDateTime]: [TomlValue.LocalDateTime]
 * * [OffsetDateTime]: [TomlValue.OffsetDateTime]
 *
 * Additionally, any subclass of [TomlValue] can always be encoded into itself.
 *
 * Note that as TOML does not have the concept of `null`, any `null` values present in encoded lists or maps
 * are ignored.
 */
fun TomlEncoder.encode(value: Any): TomlValue {
    encoderFor(value::class)?.let { encode ->
        try {
            return@encode encode(value)
        } catch (e: TomlEncoder.Pass) {
            /* no-op */
        }
    }
    return when {
        value is Map<*, *> -> fromMap(value)
        value is Iterable<*> -> TomlValue.List(value.mapNotNull { it?.let(::encode) })
        value::class.isData -> fromDataClass(value)
        else -> throw TomlException.EncodingError(value, null)
    }
}

private fun TomlEncoder.fromMap(value: Map<*, *>): TomlValue {
    val entries = value.mapNotNull { (key, value) ->
        value?.let { key.toString() to encode(it) }
    }
    return TomlValue.Map(entries.toMap())
}

private fun TomlEncoder.fromDataClass(value: Any): TomlValue.Map {
    val tomlNamesByParameterName = mappingFor(value::class)
    val fields = value::class.declaredMemberProperties.mapNotNull { prop ->
        if (value::class.visibility == KVisibility.PRIVATE) {
            prop.isAccessible = true
        }

        @Suppress("UNCHECKED_CAST")
        (prop as KProperty1<Any, Any?>).get(value)?.let {
            val name = tomlNamesByParameterName[prop.name] ?: prop.name
            name to encode(it)
        }
    }
    return TomlValue.Map(fields.toMap())
}
