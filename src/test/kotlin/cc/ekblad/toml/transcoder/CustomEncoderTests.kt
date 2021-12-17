package cc.ekblad.toml.transcoder

import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.transcoding.TomlEncoder
import cc.ekblad.toml.transcoding.encode
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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

    @Test
    fun `can extend encoder with custom mapping`() {
        val encoder = TomlEncoder.default.withMapping<Foo>("bar" to "hello")
        assertEquals(TomlValue.Map("hello" to TomlValue.String("123")), encoder.encode(Foo("123")))
    }

    @Test
    fun `can extend encoder with multiple custom mappings`() {
        data class Test(val a: Int, val b: Int, val c: Int)
        val encoder = TomlEncoder.default.withMapping<Test>("a" to "A", "b" to "B")
        assertEquals(
            TomlValue.Map(
                "A" to TomlValue.Integer(1),
                "B" to TomlValue.Integer(2),
                "c" to TomlValue.Integer(3),
            ),
            encoder.encode(Test(1, 2, 3))
        )
    }

    @Test
    fun `can extend encoder with custom mappings in several calls`() {
        data class Test(val a: Int, val b: Int, val c: Int)
        val encoder = TomlEncoder.default
            .withMapping<Test>("a" to "A")
            .withMapping<Test>("b" to "B")
        assertEquals(
            TomlValue.Map(
                "A" to TomlValue.Integer(1),
                "B" to TomlValue.Integer(2),
                "c" to TomlValue.Integer(3),
            ),
            encoder.encode(Test(1, 2, 3))
        )
    }

    @Test
    fun `newer mapping overrides older`() {
        data class Test(val a: Int, val b: Int, val c: Int)
        val encoder = TomlEncoder.default
            .withMapping<Test>("a" to "A")
            .withMapping<Test>("a" to "X")
        assertEquals(
            TomlValue.Map(
                "X" to TomlValue.Integer(1),
                "b" to TomlValue.Integer(2),
                "c" to TomlValue.Integer(3),
            ),
            encoder.encode(Test(1, 2, 3))
        )
    }

    @Test
    fun `properties are serialized in order when there is a toml name clash`() {
        data class Test(val a: Int, val b: Int, val c: Int)
        val encoder = TomlEncoder.default.withMapping<Test>("a" to "b")
        assertEquals(
            TomlValue.Map(
                "b" to TomlValue.Integer(2),
                "c" to TomlValue.Integer(3),
            ),
            encoder.encode(Test(1, 2, 3))
        )
    }

    @Test
    fun `extending an encoder with a custom mapping does not affect parent`() {
        data class Test(val a: Int, val b: Int, val c: Int)
        val encoder = TomlEncoder.default.withMapping<Test>("a" to "X")
        TomlEncoder.default.withMapping<Test>("a" to "Y")
        assertEquals(
            TomlValue.Map(
                "X" to TomlValue.Integer(1),
                "b" to TomlValue.Integer(2),
                "c" to TomlValue.Integer(3),
            ),
            encoder.encode(Test(1, 2, 3))
        )
    }

    @Test
    fun `cant register mapping with non-data class`() {
        class Test(val a: Int, val b: Int, val c: Int)

        assertFailsWith<IllegalArgumentException> {
            TomlEncoder.default.withMapping<Test>("a" to "A")
        }.also {
            assertContains(it.message ?: "", "Test")
            assertContains(it.message ?: "", "is not a data class")
        }

        assertFailsWith<IllegalArgumentException> {
            TomlEncoder.default.withMapping<Dummy>("a" to "A")
        }.also {
            assertContains(it.message ?: "", "CustomEncoderTests.Dummy")
            assertContains(it.message ?: "", "is not a data class")
        }
    }

    @Test
    fun `can't add custom mapping for nonexistent property`() {
        data class Test(val x: Int)

        assertFailsWith<IllegalArgumentException> {
            TomlEncoder.default.withMapping<Test>("KABOOM" to "x")
        }.also {
            assertContains(it.message ?: "", "KABOOM")
            assertContains(it.message ?: "", "Test")
        }

        assertFailsWith<IllegalArgumentException> {
            TomlEncoder.default.withMapping<Foo>("KABOOM" to "x")
        }.also {
            assertContains(it.message ?: "", "KABOOM")
            assertContains(it.message ?: "", "CustomEncoderTests.Foo")
        }
    }
}
