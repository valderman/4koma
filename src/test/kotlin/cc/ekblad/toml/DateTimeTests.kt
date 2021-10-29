package cc.ekblad.toml

import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.format.DateTimeFormatter

class DateTimeTests : RandomTest {
    @Test
    fun `can parse OffsetDateTime`() {
        random.values(100) { nextOffsetDateTime() }.assertAll {
            val string = it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
            assertParsesTo(TomlValue.OffsetDateTime(it), string)
            assertParsesTo(TomlValue.OffsetDateTime(it), string.replace('T', 't'))
            assertParsesTo(TomlValue.OffsetDateTime(it), string.replace('T', ' '))
        }
    }

    @Test
    fun `can parse LocalDateTime`() {
        random.values(100) { nextLocalDateTime() }.assertAll {
            val dt = TomlValue.LocalDateTime(it)
            val t = it.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME)
            val d = it.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            assertParsesTo(dt, "$d $t")
            assertParsesTo(dt, "${d}t$t")
            assertParsesTo(dt, "${d}T$t")
        }
    }

    @Test
    fun `can parse LocalDate`() {
        random.values(100) { nextLocalDate() }.assertAll {
            assertParsesTo(TomlValue.LocalDate(it), it.format(DateTimeFormatter.ISO_LOCAL_DATE))
        }
    }

    @Test
    fun `can parse LocalTime`() {
        random.values(100) { nextLocalTime() }.assertAll {
            assertParsesTo(TomlValue.LocalTime(it), it.format(DateTimeFormatter.ISO_LOCAL_TIME))
        }
    }

    @Nested
    inner class MalformedInput {
        private val badLocalTimes = listOf(
            "-02:00:00", "02:-00:00", "24:00:00", "20:69:00", "2:00:00",
            "02:2:00", "02:0:00", "12.11.00", "12:21"
        )

        private val badLocalDates = listOf(
            "-2019-11-11", "11-11-11", "2011-1-11", "2011-11-1", "2011-13-11",
            "2011-11-31", "2011-00-01", "2011-02-29", "2011-12-32", "2011-11-00"
        )
        private val badOffsets = listOf(
            "x", "+ff:ff", "+1", "+01", "-1", "-01", "+0:30", "+01:3", "-0:30",
            "-01:3", "+19:00", "-19:00", "+18:01", "-18:01"
        )

        @Test
        fun `throws on bad LocalTime`() {
            badLocalTimes.assertAll(::assertValueParseError)
        }

        @Test
        fun `throws on bad LocalDate`() {
            badLocalDates.assertAll(::assertValueParseError)
        }

        @Test
        fun `throws on bad LocalDateTime`() {
            badLocalTimes.assertAll { t ->
                badLocalDates.assertAll { d ->
                    val goodLocalDateTime = random.nextLocalDateTime()
                    val gt = goodLocalDateTime.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME)
                    val gd = goodLocalDateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                    assertValueParseError("$d $t")
                    assertValueParseError("${d}T$t")
                    assertValueParseError("$gd $t")
                    assertValueParseError("${d}T$gt")
                }
            }
        }

        @Test
        fun `throws on bad OffsetDateTime`() {
            badLocalTimes.assertAll { t ->
                badLocalDates.assertAll { d ->
                    badOffsets.assertAll { offset ->
                        val goodOffsetDateTime = random.nextOffsetDateTime()
                        val gt = goodOffsetDateTime.toLocalTime().format(DateTimeFormatter.ISO_LOCAL_TIME)
                        val gd = goodOffsetDateTime.toLocalDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
                        val goff = goodOffsetDateTime.offset.toString()
                        assertValueParseError("$d $t$offset")
                        assertValueParseError("${d}T$t$offset")
                        assertValueParseError("$gd $gt$offset")
                        assertValueParseError("${d}T$gt$goff")
                        assertValueParseError("${gd}T$t$goff")
                    }
                }
            }
        }
    }
}
