package cc.ekblad.toml

import kotlin.reflect.KClass
import kotlin.reflect.KType

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

        override val message: String =
            "toml decoding error: unable to decode toml value '$sourceValue' " +
                "to type '${(targetType.classifier as KClass<*>).simpleName}'" +
                (reason?.let { ": $it" } ?: "")
    }
}
