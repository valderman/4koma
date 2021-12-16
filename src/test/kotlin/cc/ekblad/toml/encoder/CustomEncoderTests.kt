package cc.ekblad.toml.encoder

import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.transcoding.TomlEncoder
import cc.ekblad.toml.transcoding.encode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class CustomEncoderTests : UnitTest {
    private object Dummy
    private data class Foo(val bar: String)

    @Test
    fun `can use custom encoder function`() {
        val encoder = TomlEncoder.default.with { _: Dummy -> TomlValue.String("the dummy") }
        assertEquals(TomlValue.String("the dummy"), encoder.encode(Dummy))
        assertEquals(TomlValue.Map("foo" to TomlValue.String("the dummy")), encoder.encode(mapOf("foo" to Dummy)))
    }

    @Test
    fun `newer encoder overrides older`() {
        val encoder = TomlEncoder.default
            .with { _: Dummy -> fail("shouldn't get here") }
            .with { _: Dummy -> TomlValue.String("the dummy") }
        assertEquals(TomlValue.String("the dummy"), encoder.encode(Dummy))
        assertEquals(TomlValue.Map("foo" to TomlValue.String("the dummy")), encoder.encode(mapOf("foo" to Dummy)))
    }

    @Test
    fun `newer encoder can pass to older`() {
        val encoder = TomlEncoder.default
            .with { _: Dummy -> TomlValue.String("the dummy") }
            .with { _: Dummy -> pass() }
        assertEquals(TomlValue.String("the dummy"), encoder.encode(Dummy))
        assertEquals(TomlValue.Map("foo" to TomlValue.String("the dummy")), encoder.encode(mapOf("foo" to Dummy)))
    }

    @Test
    fun `custom encoder can override default`() {
        val encoder = TomlEncoder.default.with { n: Int -> TomlValue.String(n.toString()) }
        assertEquals(TomlValue.String("123"), encoder.encode(123))
        assertEquals(TomlValue.Map("foo" to TomlValue.String("123")), encoder.encode(mapOf("foo" to 123)))
    }

    @Test
    fun `custom encoder can pass to default`() {
        val encoder = TomlEncoder.default.with { _: Int -> pass() }
        assertEquals(TomlValue.Integer(123), encoder.encode(123))
        assertEquals(TomlValue.Map("foo" to TomlValue.Integer(123)), encoder.encode(mapOf("foo" to 123)))
    }

    @Test
    fun `custom encoder can override default for data class`() {
        val encoder = TomlEncoder.default.with { _: Foo -> TomlValue.String("foo") }
        assertEquals(TomlValue.String("foo"), encoder.encode(Foo("123")))
        assertEquals(
            TomlValue.Map("foo" to TomlValue.String("foo")),
            encoder.encode(mapOf("foo" to Foo("123")))
        )
    }

    @Test
    fun `custom encoder can pass to default for data class`() {
        val encoder = TomlEncoder.default.with { _: Foo -> pass() }
        assertEquals(TomlValue.Map("bar" to TomlValue.String("123")), encoder.encode(Foo("123")))
        assertEquals(
            TomlValue.Map("foo" to TomlValue.Map("bar" to TomlValue.String("123"))),
            encoder.encode(mapOf("foo" to Foo("123")))
        )
    }
}
