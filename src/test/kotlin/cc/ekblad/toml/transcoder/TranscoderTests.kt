package cc.ekblad.toml.transcoder

import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.transcoding.TomlTranscoder
import cc.ekblad.toml.transcoding.decode
import cc.ekblad.toml.transcoding.encode
import kotlin.test.Test
import kotlin.test.assertEquals

class TranscoderTests : UnitTest {
    @Test
    fun `transcoder parts are the inverses of each other`() {
        data class Test(val a: Int, val b: String)
        val transcoder = TomlTranscoder.default.withMapping<Test>("A" to "a")
        val expectedValue = Test(123, "hello")
        val expectedToml = TomlValue.Map(
            "A" to TomlValue.Integer(123),
            "b" to TomlValue.String("hello")
        )
        val actualToml = transcoder.encode(Test(123, "hello"))
        val actualValue = transcoder.decode<Test>(actualToml)

        assertEquals(expectedToml, actualToml)
        assertEquals(expectedValue, actualValue)
    }

    @Test
    fun `transcoder encoding is equivalent to encoder member encoding`() {
        data class Test(val a: Int, val b: String)
        val transcoder = TomlTranscoder.default.withMapping<Test>("A" to "a")
        val transcoderToml = transcoder.encode(Test(123, "hello"))
        val encoderToml = transcoder.encoder.encode(Test(123, "hello"))

        assertEquals(transcoderToml, encoderToml)
    }

    @Test
    fun `transcoder decoding is equivalent to decoder member decoding`() {
        data class Test(val a: Int, val b: String)
        val transcoder = TomlTranscoder.default.withMapping<Test>("A" to "a")
        val toml = TomlValue.Map(
            "A" to TomlValue.Integer(123),
            "b" to TomlValue.String("hello")
        )
        val transcoderValue = transcoder.decode<Test>(toml)
        val encoderValue = toml.decode<Test>(transcoder.decoder)

        assertEquals(transcoderValue, encoderValue)
    }
}
