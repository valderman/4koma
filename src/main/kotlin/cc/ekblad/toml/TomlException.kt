package cc.ekblad.toml

import kotlin.reflect.KClass

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
        val targetType: KClass<*>,
        override val cause: Throwable?
    ) : TomlException() {
        constructor(reason: String?, sourceValue: TomlValue, targetType: KClass<*>) :
            this(reason, sourceValue, targetType, null)
        constructor(sourceValue: TomlValue, targetType: KClass<*>) :
            this(null, sourceValue, targetType, null)

        override val message: String =
            "toml conversion error: unable to convert toml value '$sourceValue' to type '${targetType.simpleName}'" +
                (reason?.let { ": $it" } ?: "")
    }
}
