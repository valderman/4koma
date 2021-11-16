package cc.ekblad.toml.converter

import cc.ekblad.toml.TomlConverter
import cc.ekblad.toml.TomlException
import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.convert
import org.junit.jupiter.api.assertThrows
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalStdlibApi::class)
class CustomConverterTests {
    @Test
    fun `can use single custom converter`() {
        val converter = TomlConverter.default.with { it: TomlValue.List -> it.elements.size }
        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertEquals(2, toml.convert(converter))
        assertEquals(123, TomlValue.Integer(123).convert(converter))
    }

    @Test
    fun `can use multiple custom converters`() {
        data class Foo(val value: Int)
        data class Bar(val value: String)
        data class Baz(val a: Foo, val b: Bar)
        val converter = TomlConverter.default.with(
            typeOf<Foo>() to {
                (it as? TomlValue.Integer)?.let {
                    Foo(it.value.toInt())
                }
            },
            typeOf<Bar>() to {
                (it as? TomlValue.Integer)?.let {
                    Bar(it.value.toString())
                }
            },
        )
        val toml = TomlValue.Map(
            "a" to TomlValue.Integer(123),
            "b" to TomlValue.Integer(456),
        )
        assertEquals(Baz(Foo(123), Bar("456")), toml.convert(converter))
    }

    @Test
    fun `converters are searched in the correct order`() {
        val goodConverter = TomlConverter.default
            .with<TomlValue.List, Int> { error("should never get here") }
            .with { it: TomlValue.List -> it.elements.size }

        val badConverter = TomlConverter.default
            .with { it: TomlValue.List -> it.elements.size }
            .with<TomlValue.List, Int> { error("boom!") }

        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertEquals(2, toml.convert(goodConverter))
        assertThrows<IllegalStateException> { toml.convert<Int>(badConverter) }
    }

    @Test
    fun `can use UnsupportedConversion to pass the ball to the next decoder`() {
        val converter = TomlConverter.default
            .with<TomlValue.List, Int> { pass() }
            .with { it: TomlValue.List -> it.elements.size }

        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertEquals(2, toml.convert(converter))
    }

    @Test
    fun `throws ConversionError if all decoders throw unsupported and no default conversion exists`() {
        val converter = TomlConverter.default
            .with<TomlValue.List, Int> { pass() }

        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertThrows<TomlException.ConversionError> { toml.convert<Int>(converter) }
    }

    @Test
    fun `default conversion is used if all decoders pass`() {
        val converter = TomlConverter.default
            .with<TomlValue.Integer, Int> { pass() }

        val toml = TomlValue.Integer(123)
        assertEquals(123, toml.convert(converter))
    }
}
