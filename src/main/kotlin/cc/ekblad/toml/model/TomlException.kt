package cc.ekblad.toml.model

import kotlin.reflect.KParameter
import kotlin.reflect.KType
import kotlin.reflect.javaType
import kotlin.reflect.jvm.javaType

/**
 * Base class of all exceptions that might be thrown during TOML processing.
 */
sealed class TomlException : RuntimeException() {
    /**
     * An error occurred while parsing a TOML document.
     */
    data class ParseError(
        val errorDescription: String,
        val line: Int,
        override val cause: Throwable?
    ) : TomlException() {
        constructor(errorDescription: String, line: Int) : this(errorDescription, line, null)

        override val message: String =
            "toml parse error, on line $line: $errorDescription"
    }

    /**
     * An error occurred while serializing a TOML value.
     */
    sealed class SerializationError(override val message: String, val sourceValue: Any) : TomlException() {
        /**
         * Thrown when attempting to serialize a value which successfully encoded to a non-map TOML value,
         * as all valid TOML documents are maps with zero or more keys.
         */
        class NotAMap(sourceValue: Any, val tomlValue: TomlValue) : SerializationError(
            "Class '${sourceValue.javaClass.name}' encoded to a non-map TOML value, which is not serializable.",
            sourceValue
        )

        /**
         * Thrown when attempting to serialize a TOML document containing strings with one or more invalid characters.
         */
        class InvalidString(sourceValue: String, val invalidChars: Set<Char>) : SerializationError(
            "String contains characters which are not allowed in a TOML document: " +
                "'${invalidChars.joinToString(", ") { "\\u${it.code.toString(16)}" }}'.",
            sourceValue
        )
    }

    /**
     * An error occurred while decoding a TOML value into some other Kotlin type.
     */
    sealed class DecodingError(
        val reason: String,
        val sourceValue: TomlValue,
        val targetType: KType
    ) : TomlException() {
        /**
         * Thrown when there is no decoder registered that can convert [sourceValue] into a Kotlin value of
         * [targetType].
         */
        class NoSuchDecoder(sourceValue: TomlValue, targetType: KType) : DecodingError(
            "No decoder registered for value/target pair.",
            sourceValue,
            targetType
        )

        /**
         * Thrown when attempting to decode [sourceValue] into an enum class for which it does not match any of the
         * constructor names.
         */
        class InvalidEnumValue(tomlString: TomlValue.String, targetType: KType) : DecodingError(
            "'${tomlString.value}' is not a constructor of enum class '${targetType.javaType.typeName}'.",
            tomlString,
            targetType
        )

        /**
         * Thrown when attempting to decode a list into a non-list like type for which there is no custom decoder.
         */
        class IllegalListTargetType(tomlList: TomlValue.List, target: KType) : DecodingError(
            "Lists can only be decoded into lists, sets, collections, iterables, " +
                "or types for which a custom decoder function has been registered.",
            tomlList,
            target
        )

        /**
         * Throw when attempting to decode a map into a non-map like type for which there is no custom decoder.
         */
        class IllegalMapTargetType(tomlMap: TomlValue.Map, target: KType) : DecodingError(
            "Objects can only be decoded into maps, data classes, " +
                "or types for which a custom decoder function has been registered.",
            tomlMap,
            target
        )

        /**
         * Thrown when attempting to decode a map into a map with an invalid key type.
         */
        class IllegalMapKeyType(tomlMap: TomlValue.Map, targetMapType: KType) : DecodingError(
            "Tried to decode object into map with illegal key type " +
                "'${targetMapType.javaType.typeName}'. Key type must be String or Any.",
            tomlMap,
            targetMapType
        )

        /**
         * Thrown when [targetType] is a data class with a non-nullable parameter named [parameter],
         * but [sourceValue] does not contain any fields by that name.
         */
        class MissingNonNullableValue(val parameter: KParameter, tomlMap: TomlValue.Map, kType: KType) : DecodingError(
            "No value found for non-nullable parameter '${parameter.name}'.",
            tomlMap,
            kType
        )

        @OptIn(ExperimentalStdlibApi::class)
        override val message: String
            get() {
                val type = targetType.javaType.typeName
                return "TOML decoding error: unable to decode toml value '$sourceValue' to type '$type'. $reason"
            }
    }

    /**
     * An error occurred while encoding a Kotlin value into a TOML value.
     */
    sealed class EncodingError(
        val reason: String,
        val sourceValue: Any
    ) : TomlException() {
        /**
         * Thrown when attempting to encode a type for which no encoder is registered.
         */
        class NoSuchEncoder(sourceValue: Any) : EncodingError(
            "No encoder registered for type.",
            sourceValue
        )

        /**
         * Thrown when a lazy value evaluates to null during encoding.
         */
        class LazyValueEvaluatedToNull(sourceValue: Lazy<*>) : EncodingError(
            "Lazy values must not evaluate to null.",
            sourceValue
        )
        override val message: String
            get() = "TOML encoding error: unable to encode '$sourceValue' into a TOML value. $reason"
    }

    /**
     * An access error occurred while encoding a Kotlin value into a TOML value.
     * This exception should never be thrown under normal operation. If you see this happen, please file a bug report
     * at [github.com/valderman/4koma](https://github.com/valderman/4koma/issues).
     */
    data class AccessError(
        val name: String,
        val tomlName: String,
        override val cause: Throwable?
    ) : TomlException() {
        override val message: String
            get() = "Cannot access constructor property: '$name' mapped from '$tomlName'"
    }
}
