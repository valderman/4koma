package cc.ekblad.toml.decoder

import cc.ekblad.toml.TomlException
import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.serialization.from
import cc.ekblad.toml.transcoding.TomlDecoder
import cc.ekblad.toml.transcoding.decode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

@OptIn(ExperimentalStdlibApi::class)
class CustomDecoderTests {
    @Test
    fun `can use single custom decoder function`() {
        val decoder = TomlDecoder.default.with { it: TomlValue.List -> it.elements.size }
        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertEquals(2, toml.decode(decoder))
        assertEquals(123, TomlValue.Integer(123).decode(decoder))
    }

    @Test
    fun `can use multiple custom decoder functions`() {
        data class Foo(val value: Int)
        data class Bar(val value: String)
        data class Baz(val a: Foo, val b: Bar)
        val decoder = TomlDecoder.default.with(
            Foo::class to { _, it ->
                (it as? TomlValue.Integer)?.let {
                    Foo(it.value.toInt())
                }
            },
            Bar::class to { _, it ->
                (it as? TomlValue.Integer)?.let {
                    Bar(it.value.toString())
                }
            },
        )
        val toml = TomlValue.Map(
            "a" to TomlValue.Integer(123),
            "b" to TomlValue.Integer(456),
        )
        assertEquals(Baz(Foo(123), Bar("456")), toml.decode(decoder))
    }

    @Test
    fun `decoder functions are searched in the correct order`() {
        val goodDecoder = TomlDecoder.default
            .with<TomlValue.List, Int> { _, _ -> error("should never get here") }
            .with { it: TomlValue.List -> it.elements.size }

        val badDecoder = TomlDecoder.default
            .with { it: TomlValue.List -> it.elements.size }
            .with<TomlValue.List, Int> { _, _ -> error("boom!") }

        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertEquals(2, toml.decode(goodDecoder))
        assertFailsWith<IllegalStateException> { toml.decode<Int>(badDecoder) }
    }

    @Test
    fun `can use pass to pass the ball to the next decoder`() {
        val decoder = TomlDecoder.default
            .with<TomlValue.List, Int> { _, _ -> pass() }
            .with { it: TomlValue.List -> it.elements.size }

        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertEquals(2, toml.decode(decoder))
    }

    @Test
    fun `throws DecodingError if all decoders throw unsupported and no default decoder exists`() {
        val decoder = TomlDecoder.default
            .with<TomlValue.List, Int> { _, _ -> pass() }

        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertFailsWith<TomlException.DecodingError> { toml.decode<Int>(decoder) }
    }

    @Test
    fun `default decoder is used if all decoder functions pass`() {
        val decoder = TomlDecoder.default
            .with<TomlValue.Integer, Int> { _, _ -> pass() }

        val toml = TomlValue.Integer(123)
        assertEquals(123, toml.decode(decoder))
    }

    @Test
    fun `custom mapping does not get in the way of default decoder`() {
        val decoder = TomlDecoder.default.withMapping<User>("bar" to "baz")

        assertEquals(123, TomlValue.Integer(123).decode(decoder))
        assertEquals(Config(emptyList()), TomlValue.Map("users" to TomlValue.List()).decode(decoder))
    }

    @Test
    fun `custom mapping can be used together with custom decoder function`() {
        val decoder = TomlDecoder.default
            .with { it: TomlValue.Integer -> User("Anonymous", it.value.toInt(), null) }
            .withMapping<User>("bar" to "baz")

        assertEquals(User("Anonymous", 123, null), TomlValue.Integer(123).decode(decoder))
    }

    @Test
    fun `custom mapping does not let you convert maps into something else`() {
        val decoder = TomlDecoder.default.withMapping<User>("bar" to "baz")
        assertFailsWith<TomlException.DecodingError> { TomlValue.Integer(123).decode<User>(decoder) }
    }

    @Test
    fun `can add multiple mappings for the same type`() {
        val decoder = TomlDecoder.default
            .withMapping<User>("name" to "fullName")
            .withMapping<User>("years" to "age")
        assertEquals(
            User("Anonymous", 123, null),
            TomlValue.Map(
                "name" to TomlValue.String("Anonymous"),
                "years" to TomlValue.Integer(123)
            ).decode(decoder)
        )
    }

    @Test
    fun `later mapping overrides earlier one`() {
        val decoder = TomlDecoder.default
            .withMapping<User>("name" to "KABOOM")
            .withMapping<User>("name" to "fullName")
        assertEquals(
            User("Anonymous", 123, null),
            TomlValue.Map(
                "name" to TomlValue.String("Anonymous"),
                "age" to TomlValue.Integer(123)
            ).decode(decoder)
        )
    }

    @Test
    fun `extending a decoder with a new mapping doesn't affect other the parent decoder`() {
        val decoder = TomlDecoder.default.withMapping<User>("name" to "fullName")
        decoder.withMapping<User>("name" to "KABOOM")
        assertEquals(
            User("Anonymous", 123, null),
            TomlValue.Map(
                "name" to TomlValue.String("Anonymous"),
                "age" to TomlValue.Integer(123)
            ).decode(decoder)
        )
    }

    @Test
    fun `can't add custom mapping for map or list`() {
        assertFailsWith<IllegalArgumentException> {
            TomlDecoder.default.withMapping<Map<*, *>>("name" to "KABOOM")
        }
        assertFailsWith<IllegalArgumentException> {
            TomlDecoder.default.withMapping<List<*>>("name" to "KABOOM")
        }
    }

    @Test
    fun `can remap toml property names`() {
        val toml = """
            [[user]]
            full_name = 'Bosse Bus'
            age = 47
            
            [[user]]
            full_name = 'Dolan Duck'
            age = 107
        """.trimIndent()
        val decoder = TomlDecoder.default.withMapping<User>(
            "full_name" to "fullName",
            "address" to "homeAddress"
        ).withMapping<Config>("user" to "users")

        val expected = Config(
            listOf(
                User("Bosse Bus", 47, null),
                User("Dolan Duck", 107, null)
            )
        )
        assertEquals(expected, TomlValue.from(toml).decode(decoder))
    }
    data class User(val fullName: String, val age: Int, val homeAddress: String?)
    data class Config(val users: List<User>)

    @Test
    fun `creating an extended decoder does not affect the default decoder in any way`() {
        val nonDefaultDecoder = TomlDecoder.default
            .with<TomlValue.Integer, Int> { _, _ -> error("we never get here") }

        val toml = TomlValue.Integer(123)
        assertEquals(123, toml.decode())
        assertNotEquals(nonDefaultDecoder, TomlDecoder.default)
    }
}
