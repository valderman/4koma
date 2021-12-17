package cc.ekblad.toml.transcoding

import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.util.KotlinName
import cc.ekblad.toml.util.TomlName
import kotlin.reflect.KClass
import kotlin.reflect.typeOf

/**
 * A bidirectional TOML transcoder, capable of converting [TomlValue]s into Kotlin values, and vice versa.
 *
 * A transcoder is intended to make your life easier by keeping custom mappings in sync between your encoder and
 * decoder. Unless you need custom en/decoder functions, using a transcoder instead of a separate encoder and decoder
 * is highly recommended.
 */
class TomlTranscoder(
    /**
     * Encoder half of the transcoder. The inverse of [decoder].
     */
    val encoder: TomlEncoder,

    /**
     * Decoder half of the transcoder. The inverse of [encoder].
     */
    val decoder: TomlDecoder
) {
    /**
     * Returns a copy of the receiver TOML transcoder, extended with a custom property mapping for the type `T`.
     * Mappings are given on the form `"tomlName" to "kotlinName"`.
     *
     * See [TomlDecoder.withMapping] for more information about custom mappings.
     */
    inline fun <reified T : Any> withMapping(vararg mapping: Pair<TomlName, KotlinName>): TomlTranscoder =
        withMapping(T::class, *mapping)

    /**
     * Returns a copy of the receiver TOML transcoder, extended with a custom property mapping for the type represented
     * by the given `KClass`. Mappings are given on the form `"tomlName" to "kotlinName"`.
     *
     * See [TomlDecoder.withMapping] for more information about custom mappings.
     */
    fun <T : Any> withMapping(kClass: KClass<T>, vararg mapping: Pair<TomlName, KotlinName>): TomlTranscoder =
        TomlTranscoder(
            encoder.withMapping(kClass, *mapping.map { it.second to it.first }.toTypedArray()),
            decoder.withMapping(kClass, *mapping)
        )

    /**
     * Encode the given value into a [TomlValue] using the receiver `TomlTranscoder`.
     *
     * See [TomlEncoder.encode] for information about which types can be encoded.
     */
    fun encode(value: Any): TomlValue =
        encoder.encode(value)

    /**
     * Decode the given [TomlValue] into the type represented by the given type parameter.
     *
     * See [TomlValue.decode] for more information about which types can be decoded.
     */
    inline fun <reified T : Any?> decode(value: TomlValue): T =
        value.decode(decoder, typeOf<T>())

    companion object {
        /**
         * The default TOML transcoder. Handles the same types as [TomlEncoder.default] and [TomlDecoder.default].
         */
        val default: TomlTranscoder = TomlTranscoder(
            TomlEncoder.default,
            TomlDecoder.default
        )
    }
}
