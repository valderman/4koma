package cc.ekblad.toml

/**
 * Kotlin representation of a TOML value.
 * A full TOML document is always represented as a [TomlValue.Map].
 *
 * You can either traverse this representation manually, access individual properties using [TomlValue.get], or
 * decode the whole thing into a data class of your choice using [TomlValue.decode].
 *
 * [TomlValue.from] can be used to obtain a `TomlValue` from an input TOML document in the form of a [String],
 * [java.nio.file.Path], or [java.io.InputStream].
 */
sealed class TomlValue {
    sealed class Primitive : TomlValue()

    data class String(val value: kotlin.String) : Primitive()
    data class Integer(val value: Long) : Primitive()
    data class Double(val value: kotlin.Double) : Primitive()
    data class Bool(val value: Boolean) : Primitive()
    data class OffsetDateTime(val value: java.time.OffsetDateTime) : Primitive()
    data class LocalDateTime(val value: java.time.LocalDateTime) : Primitive()
    data class LocalDate(val value: java.time.LocalDate) : Primitive()
    data class LocalTime(val value: java.time.LocalTime) : Primitive()

    data class Map(val properties: kotlin.collections.Map<kotlin.String, TomlValue>) : TomlValue() {
        constructor(vararg entries: Pair<kotlin.String, TomlValue>) : this(entries.toMap())
    }
    data class List(val elements: kotlin.collections.List<TomlValue>) : TomlValue() {
        constructor(vararg values: TomlValue) : this(values.toList())
    }

    // For serialization extension methods
    companion object
}
