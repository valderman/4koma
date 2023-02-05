package cc.ekblad.toml.parser

import cc.ekblad.toml.StringTest
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.serialization.from
import kotlin.test.Test
import kotlin.test.assertEquals

class BugRegressionTests : StringTest {
    @Test
    fun `issue 20`() {
        // https://github.com/valderman/4koma/issues/20
        val actual = TomlValue.from(
            """
            [messages]
            joinMessage = "<green>[+] %playerName%"
            leaveMessage = "<red>[-] %playerName%"
            chatFormat = "%displayName% : %message%"
            """.trimIndent()
        )
        val expected = TomlValue.Map(
            "messages" to TomlValue.Map(
                "joinMessage" to TomlValue.String("<green>[+] %playerName%"),
                "leaveMessage" to TomlValue.String("<red>[-] %playerName%"),
                "chatFormat" to TomlValue.String("%displayName% : %message%"),
            ),
        )
        assertEquals(expected, actual)
    }
}
