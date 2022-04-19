package cc.ekblad.toml.serializer

import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.serialization.write
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.nio.file.Path
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.io.path.deleteIfExists
import kotlin.io.path.inputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WriterTests : UnitTest {
    private val tomlValue = TomlValue.Map(
        "key" to TomlValue.String("value"),
        "foo" to TomlValue.Map(
            "bar" to TomlValue.Map(
                "baz" to TomlValue.String("baztu")
            ),
            "baz" to TomlValue.Integer(0xffff),
            "bark" to TomlValue.Map(
                "one" to TomlValue.Map("two" to TomlValue.LocalTime(LocalTime.of(0, 27)))
            ),
        ),
        "yarr" to TomlValue.List(
            TomlValue.Map(
                "baz" to TomlValue.String("baztu"),
                "in both" to TomlValue.Integer(-5),
            ),
            TomlValue.Map(
                "one" to TomlValue.Map("two" to TomlValue.LocalTime(LocalTime.of(0, 27))),
                "in both" to TomlValue.Integer(42)
            ),
        ),
        "key2" to TomlValue.OffsetDateTime(
            OffsetDateTime.of(2021, 12, 13, 0, 38, 11, 0, ZoneOffset.UTC)
        ),
    )
    private val expectedTomlDocument = """
        key = "value"
        key2 = 2021-12-13T00:38:11Z

        [foo]
        bar.baz = "baztu"
        baz = 65535
        bark.one.two = 00:27:00

        [[yarr]]
        baz = "baztu"
        'in both' = -5

        [[yarr]]
        one.two = 00:27:00
        'in both' = 42
        
    """.trimIndent()

    @Test
    fun `can serialize to string buffer`() {
        val buf = StringBuffer()
        tomlValue.write(buf)
        val tomlDocument = buf.toString()
        assertEquals(expectedTomlDocument, tomlDocument)
    }

    @Test
    fun `can serialize to output stream`() {
        val stream = ByteArrayOutputStream()
        tomlValue.write(stream)
        val tomlDocument = stream.toString(Charset.forName("UTF-8"))
        assertEquals(expectedTomlDocument, tomlDocument)
    }

    @Test
    fun `can serialize to file`() {
        val file = kotlin.io.path.createTempFile()
        try {
            tomlValue.write(file)
            val tomlDocument = file.inputStream().use {
                it.readAllBytes().toString(Charset.forName("UTF-8"))
            }
            assertEquals(expectedTomlDocument, tomlDocument)
        } finally {
            file.deleteIfExists()
        }
    }

    @Test
    fun `serializing to bad file throws IOException`() {
        assertFailsWith<IOException> {
            val file = Path.of("nonexistent", "directory", "kaboom.toml")
            tomlValue.write(file)
        }
    }

    @Test
    fun `serializing bad toml to file throws SerializationError`() {
        val file = kotlin.io.path.createTempFile()
        try {
            assertFailsWith<TomlException.SerializationError> {
                TomlValue.Map("x" to TomlValue.String("\u0000")).write(file)
            }
        } finally {
            file.deleteIfExists()
        }
    }
}
