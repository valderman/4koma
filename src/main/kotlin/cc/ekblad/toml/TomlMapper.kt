package cc.ekblad.toml

import cc.ekblad.toml.configuration.TomlMapperConfigurator
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.model.merge
import cc.ekblad.toml.transcoding.TomlDecoder
import cc.ekblad.toml.transcoding.TomlEncoder
import cc.ekblad.toml.transcoding.decode
import cc.ekblad.toml.transcoding.encode
import cc.ekblad.toml.util.InternalAPI
import java.math.BigDecimal
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.util.SortedMap
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * A bidirectional TOML transcoder, capable of converting [TomlValue]s into Kotlin values, and vice versa.
 *
 * A transcoder is intended to make your life easier by keeping custom mappings in sync between your encoder and
 * decoder. Unless you need custom en/decoder functions, using a transcoder instead of a separate encoder and decoder
 * is highly recommended.
 */
@OptIn(ExperimentalStdlibApi::class, InternalAPI::class)
class TomlMapper internal constructor(
    private val encoder: TomlEncoder,
    private val decoder: TomlDecoder,
) {
    /**
     * Encodes the given Kotlin value to as into a [TomlValue] using the receiver [TomlEncoder].
     * If the value can't be encoded, a [cc.ekblad.toml.model.TomlException.EncodingError] is thrown.
     *
     * Note that as TOML does not have the concept of `null`, any `null` values present in lists, maps or data classes
     * are not included in the encoding.
     */
    fun encode(value: Any): TomlValue =
        encoder.encode(value)

    /**
     * Decodes the receiver TOML value to the type indicated by type parameter `T` using the default TOML decoder.
     * If the value can't be decoded into the target type, a [cc.ekblad.toml.model.TomlException.DecodingError]
     * is thrown.
     *
     * When decoding into a data class, all fields of that class must be present in the given `TomlValue`,
     * except fields that are nullable and/or have a default value.
     * A field which is both nullable and defaulted will be assigned its default value, if missing from the given
     * `TomlValue`.
     */
    inline fun <reified T : Any?> decode(tomlValue: TomlValue): T =
        decode(typeOf<T>(), tomlValue)

    /**
     * Like [decode], but will fill in any missing fields from the [defaultValue].
     * Appropriate for use cases such as configuration files, where you may not want to force the user to configure
     * every last thing, but just override the bits they want to customize.
     *
     * Values from `defaultValue` take priority over any default values set in the target class itself.
     */
    inline fun <reified T : Any?> decodeWithDefaults(defaultValue: T, tomlValue: TomlValue): T =
        decode(typeOf<T>(), tomlValue, defaultValue)

    @InternalAPI
    fun <T : Any?> decode(targetKType: KType, tomlValue: TomlValue): T =
        decoder.decode(tomlValue, targetKType)

    @InternalAPI
    fun <T : Any?> decode(targetKType: KType, tomlValue: TomlValue, defaultValue: T): T {
        val defaultTomlValue = defaultValue?.let { encoder.encode(defaultValue) }
        val mergedTomlValue = defaultTomlValue?.merge(tomlValue) ?: tomlValue
        return decoder.decode(mergedTomlValue, targetKType)
    }

    @Suppress("UNCHECKED_CAST")
    @InternalAPI
    fun <T : Any> delegate(kClass: KClass<T>, configurator: TomlMapperConfigurator) {
        encoder.encoderFor(kClass)?.let { encoder ->
            configurator.encoder(kClass) { encoder(it) }
        }
        decoder.decoderFor<T>(kClass)?.let { decoder ->
            configurator.decoder(kClass) { kType, tomlValue -> decoder(kType, tomlValue) }
        }
        if (kClass.isData) {
            decoder.mappingFor(kClass).entries.let { entries ->
                configurator.mapping(kClass, entries.map { it.value to it.key })
            }
        }
    }

    internal fun createDerivedConfigurator(): TomlMapperConfigurator {
        return TomlMapperConfigurator(
            encoders = encoder.encoders.entries.associateTo(mutableMapOf()) { it.key to it.value.toMutableList() },
            decoders = decoder.decoders.entries.associateTo(mutableMapOf()) { it.key to it.value.toMutableList() },
            mappings = decoder.mappings.mapValuesTo(mutableMapOf()) { (_, mappingForType) ->
                mappingForType.entries.associateTo(mutableMapOf()) { it.value to it.key }
            },
            defaultValues = decoder.defaultValues.toMutableMap()
        )
    }
}

/**
 * Creates a new TOML mapper with the given configuration.
 * In the interest of efficiency, consider creating a mapper once and then keeping it around for the duration of
 * your program, rather than re-creating it every time you need to process a TOML value.
 *
 * <br>
 *
 * Without any extra configuration, TOML types can be transcoded to/from Kotlin types as follows:
 * * List: [List], [MutableList], [Collection] or [Iterable]
 * * Map: [Map], [MutableMap], [SortedMap], or any class with primary constructor fields corresponding
 *     to the keys of the TOML document.
 * * Bool: [Boolean]
 * * Double: [Double], [Float] or [BigDecimal]
 * * Integer: [Int], [Long], [Float], [Double], [BigDecimal] or [BigInteger]
 * * String: [String] or any [Enum] type
 * * LocalDate: [LocalDate]
 * * LocalTime: [LocalTime]
 * * LocalDateTime: [LocalDateTime]
 * * OffsetDateTime: [OffsetDateTime]
 *
 * Additionally, any subclass of [TomlValue] can always be transcoded to/from itself,
 * and any value which could be transcoded to/from a type `T` can also be transcoded to/from `Lazy<T>`.
 */
fun tomlMapper(configuration: TomlMapperConfigurator.() -> Unit): TomlMapper =
    tomlMapper(defaultTomlMapper, configuration)

/**
 * Creates a mapper which inherits all its configuration from the given [baseMapper].
 *
 * Note that custom encoders/decoders always take precedence over mappings. If your mapper inherits from a mapper
 * which contains a custom decoder for some type T, this decoder will *not* be overridden by subsequent calls to
 * [TomlMapperConfigurator.mapping], unless the decoder's [TomlMapper] subtype is something other than [TomlValue.Map].
 */
fun tomlMapper(baseMapper: TomlMapper, configuration: TomlMapperConfigurator.() -> Unit): TomlMapper =
    tomlMapper(baseMapper.createDerivedConfigurator(), configuration)

/**
 * Delegate the handling of the given type to the given [mapper].
 * Overrides any previous configuration for that type, and will be overridden in turn by any subsequent configuration.
 */
@OptIn(InternalAPI::class)
inline fun <reified T : Any> TomlMapperConfigurator.delegate(mapper: TomlMapper) {
    mapper.delegate(T::class, this)
}

private fun tomlMapper(
    configurator: TomlMapperConfigurator,
    configuration: TomlMapperConfigurator.() -> Unit
): TomlMapper {
    val config = configurator.apply(configuration).buildConfig()
    val mappingsByParameter = config.mappings.mapValues { (_, mappingForType) ->
        mappingForType.entries.associate { it.value to it.key }
    }
    val encoder = TomlEncoder(config.encoders, mappingsByParameter)
    val decoder = TomlDecoder(config.decoders, mappingsByParameter, config.defaultValues)
    return TomlMapper(encoder, decoder)
}

/**
 * Mapper containing all default values. Used to create derived mappers instead of applying the [defaultConfig]
 * over and over for performance reasons.
 */
private val defaultTomlMapper: TomlMapper by lazy {
    val configurator = TomlMapperConfigurator(
        encoders = mutableMapOf(),
        decoders = mutableMapOf(),
        mappings = mutableMapOf(),
        defaultValues = mutableMapOf()
    )
    tomlMapper(configurator, TomlMapperConfigurator::defaultConfig)
}

private fun TomlMapperConfigurator.defaultConfig() {
    // Default encoders
    encoder { it: TomlValue.List -> it }
    encoder { it: TomlValue.Map -> it }
    encoder { it: TomlValue.Integer -> it }
    encoder { it: TomlValue.Double -> it }
    encoder { it: TomlValue.String -> it }
    encoder { it: TomlValue.Bool -> it }
    encoder { it: TomlValue.LocalDate -> it }
    encoder { it: TomlValue.LocalTime -> it }
    encoder { it: TomlValue.LocalDateTime -> it }
    encoder { it: TomlValue.OffsetDateTime -> it }
    encoder { it: Long -> TomlValue.Integer(it) }
    encoder { it: Int -> TomlValue.Integer(it.toLong()) }
    encoder { it: BigInteger -> TomlValue.Integer(it.toLong()) }
    encoder { it: Float -> TomlValue.Double(it.toDouble()) }
    encoder { it: Double -> TomlValue.Double(it) }
    encoder { it: BigDecimal -> TomlValue.Double(it.toDouble()) }
    encoder { it: Boolean -> TomlValue.Bool(it) }
    encoder { it: OffsetDateTime -> TomlValue.OffsetDateTime(it) }
    encoder { it: LocalDateTime -> TomlValue.LocalDateTime(it) }
    encoder { it: LocalDate -> TomlValue.LocalDate(it) }
    encoder { it: LocalTime -> TomlValue.LocalTime(it) }
    encoder { it: String -> TomlValue.String(it) }

    // Default decoders
    decoder { it: TomlValue -> it }
    decoder { it: TomlValue.String -> it }
    decoder { it: TomlValue.Integer -> it }
    decoder { it: TomlValue.Double -> it }
    decoder { it: TomlValue.Bool -> it }
    decoder { it: TomlValue.OffsetDateTime -> it }
    decoder { it: TomlValue.LocalDateTime -> it }
    decoder { it: TomlValue.LocalDate -> it }
    decoder { it: TomlValue.LocalTime -> it }
    decoder { it: TomlValue.Map -> it }
    decoder { it: TomlValue.List -> it }
    decoder { it: TomlValue.Double -> it.value }
    decoder { it: TomlValue.Double -> it.value.toBigDecimal() }
    decoder { it: TomlValue.Double -> it.value.toFloat() }
    decoder { it: TomlValue.Integer -> it.value }
    decoder { it: TomlValue.Integer -> it.value.toDouble() }
    decoder { it: TomlValue.Integer -> it.value.toBigDecimal() }
    decoder { it: TomlValue.Integer -> it.value.toFloat() }
    decoder { it: TomlValue.Integer -> it.value.toInt() }
    decoder { it: TomlValue.Integer -> it.value.toBigInteger() }
    decoder { it: TomlValue.Bool -> it.value }
    decoder { it: TomlValue.OffsetDateTime -> it.value }
    decoder { it: TomlValue.LocalDateTime -> it.value }
    decoder { it: TomlValue.LocalDate -> it.value }
    decoder { it: TomlValue.LocalTime -> it.value }
    decoder { it: TomlValue.String -> it.value }
    decoder { targetType, it: TomlValue -> decode<Any>(it, targetType.elementType).let { lazy { it } } }
    decoder<TomlValue, Any> { it ->
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
}
