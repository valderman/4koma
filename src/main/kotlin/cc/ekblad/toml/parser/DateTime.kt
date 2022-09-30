package cc.ekblad.toml.parser

import cc.ekblad.konbini.map
import cc.ekblad.konbini.oneOf
import cc.ekblad.konbini.parser
import cc.ekblad.konbini.regex
import cc.ekblad.toml.model.TomlValue
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

private const val date = "[0-9]{4}-[0-9]{2}-[0-9]{2}"
private const val time = "[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?"
private val localDateRegex = regex(date)
private val localTimeRegex = regex(time)
private val localDateTimeRegex = regex("$date[tT ]$time")
private val offsetDateTimeRegex = regex("$date[tT ]$time([zZ]|([+-][0-9]{2}:[0-9]{2}))")

private val localDate = parser {
    val value = localDateRegex()
    try {
        LocalDate.parse(value)
    } catch (e: DateTimeParseException) {
        fail("Invalid date: '$value'.")
    }
}.map { TomlValue.LocalDate(it) }

private val localTime = parser {
    val value = localTimeRegex()
    try {
        LocalTime.parse(value)
    } catch (e: DateTimeParseException) {
        fail("Invalid time: '$value'.")
    }
}.map { TomlValue.LocalTime(it) }

private val localDateTime = parser {
    val value = localDateTimeRegex()
    try {
        LocalDateTime.parse(value.replace(' ', 'T'))
    } catch (e: DateTimeParseException) {
        fail("Invalid date and/or time: '$value'.")
    }
}.map { TomlValue.LocalDateTime(it) }

private val offsetDateTime = parser {
    val value = offsetDateTimeRegex()
    try {
        OffsetDateTime.parse(value.replace(' ', 'T'))
    } catch (e: DateTimeParseException) {
        fail("Invalid date and/or time: '$value'.")
    }
}.map { TomlValue.OffsetDateTime(it) }

/**
 * Any one of TOML's date/time types.
 */
internal val dateTime = oneOf(offsetDateTime, localDateTime, localTime, localDate)
