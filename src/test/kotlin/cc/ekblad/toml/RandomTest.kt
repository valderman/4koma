package cc.ekblad.toml

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.math.pow
import kotlin.random.Random
import kotlin.random.Random.Default.nextInt

interface RandomTest : UnitTest {
    companion object {
        private val random = Random(42)
    }

    val random: Random
        get() = Companion.random

    /**
     * Uniformly chooses a number 0 < n < maxDigits, then returns the receiver number modulo 10^n.
     */
    fun Long.withMaxDigits(maxDigits: Int): Long =
        this % 10.0.pow(nextInt(0, maxDigits + 1)).toInt()

    fun Int.withMaxDigits(maxDigits: Int): Int =
        toLong().withMaxDigits(maxDigits).toInt()

    fun <T> Random.oneOf(alternatives: Collection<T>): T =
        alternatives.random(this)

    fun <T> Random.weighted(vararg alternatives: Pair<Int, Random.() -> T>): T =
        weighted(alternatives.toList())

    fun <T> Random.weighted(alternatives: Collection<Pair<Int, Random.() -> T>>): T {
        require(alternatives.isNotEmpty())
        var gas = Random.nextInt(0, alternatives.sumOf { it.first } + 1)
        for (alternative in alternatives) {
            gas -= alternative.first
            if (gas <= 0) {
                return (alternative.second)()
            }
        }
        return (alternatives.last().second)()
    }

    fun <T> Random.values(numValues: Int, gen: Random.() -> T): List<T> =
        (1..numValues).map { gen() }

    fun Random.nextLocalDate(): LocalDate {
        val year = random.nextInt(0, 3000)
        val month = random.nextInt(1, 13)
        val day = random.nextInt(1, daysInMonth(year, month) + 1)
        return LocalDate.of(year, month, day)
    }

    fun Random.nextLocalTime(): LocalTime {
        val hour = random.nextInt(0, 24)
        val minute = random.nextInt(0, 60)
        val second = random.nextInt(0, 60)
        return LocalTime.of(hour, minute, second)
    }

    fun Random.nextZoneOffset(): ZoneOffset =
        ZoneOffset.ofHoursMinutes(nextInt(0, 12), nextInt(0, 60))

    fun Random.nextLocalDateTime(): LocalDateTime =
        LocalDateTime.of(nextLocalDate(), nextLocalTime())

    fun Random.nextOffsetDateTime(): OffsetDateTime =
        OffsetDateTime.of(nextLocalDateTime(), nextZoneOffset())

    /**
     * Uniformly pick [minLength, maxLength) random elements from the given alphabet.
     */
    fun <T> Random.nextSequence(alphabet: Collection<T>, minLength: Int = 0, maxLength: Int = 10): List<T> =
        values(nextInt(minLength, maxLength)) { oneOf(alphabet) }

    fun Random.nextSequence(alphabet: CharSequence, minLength: Int = 0, maxLength: Int = 10): String =
        values(nextInt(minLength, maxLength)) { alphabet.random(this) }.joinToString("")
}

private val daysInMonth = listOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)

private fun daysInMonth(year: Int, month: Int): Int = when {
    month != 2 -> daysInMonth[month - 1]
    year % 400 == 0 -> 29
    year % 100 == 0 -> 28
    year % 4 == 0 -> 29
    else -> 28
}
