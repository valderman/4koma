package cc.ekblad.toml

import org.junit.jupiter.api.Test
import java.time.format.DateTimeFormatter

class DateTimeTests : RandomTest {
    @Test
    fun `can parse OffsetDateTime`() {
        random.values(100) { nextOffsetDateTime() }.assertAll {
            assertParsesTo(TomlValue.OffsetDateTime(it), it.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
        }
    }

    @Test
    fun `can parse LocalDateTime`() {
        random.values(100) { nextLocalDateTime() }.assertAll {
            assertParsesTo(TomlValue.LocalDateTime(it), it.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
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
}
