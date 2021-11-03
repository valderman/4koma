package cc.ekblad.toml

import kotlin.reflect.KClass
import kotlin.reflect.KType

sealed class TomlException : RuntimeException() {
    data class ParseError(
        val errorDescription: String,
        val line: Int,
        override val cause: Throwable?
    ) : TomlException() {
        constructor(errorDescription: String, line: Int) : this(errorDescription, line, null)

        override val message: String =
            "toml parse error, on line $line: $errorDescription"
    }

    data class ConversionError(
        val reason: String?,
        val sourceValue: TomlValue,
        val targetType: KType,
        override val cause: Throwable?
    ) : TomlException() {
        constructor(reason: String?, sourceValue: TomlValue, targetType: KType) :
            this(reason, sourceValue, targetType, null)
        constructor(sourceValue: TomlValue, targetType: KType) :
            this(null, sourceValue, targetType, null)

        override val message: String =
            "toml conversion error: unable to convert toml value '$sourceValue' " +
                "to type '${(targetType.classifier as KClass<*>).simpleName}'" +
                (reason?.let { ": $it" } ?: "")
    }
}
