package cc.ekblad.toml

import kotlin.reflect.KType
import kotlin.reflect.javaType

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
    data class SerializationError(override val message: String, override val cause: Throwable?) : TomlException()

    /**
     * An error occurred while decoding a TOML value into some other Kotlin type.
     */
    data class DecodingError(
        val reason: String?,
        val sourceValue: TomlValue,
        val targetType: KType,
        override val cause: Throwable?
    ) : TomlException() {
        constructor(reason: String, sourceValue: TomlValue, targetType: KType) :
            this(reason, sourceValue, targetType, null)
        constructor(sourceValue: TomlValue, targetType: KType) :
            this(null, sourceValue, targetType, null)

        @OptIn(ExperimentalStdlibApi::class)
        override val message: String
            get() {
                val reasonSuffix = if (reason != null) ": $reason" else ""
                val type = targetType.javaType.typeName
                return "toml decoding error: unable to decode toml value '$sourceValue' to type '$type'$reasonSuffix"
            }
    }

    /**
     * An error occurred while encoding a Kotlin value into a TOML value.
     */
    data class EncodingError(
        val sourceValue: Any?,
        override val cause: Throwable?
    ) : TomlException() {
        override val message: String
            get() = "toml decoding error: unable to encode '$sourceValue' into a toml value"
    }
}
