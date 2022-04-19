package cc.ekblad.toml.transcoder

import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.serialization.from
import cc.ekblad.toml.tomlMapper
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@OptIn(ExperimentalStdlibApi::class)
class CustomDecoderTests {
    @Test
    fun `can use single custom decoder function`() {
        val mapper = tomlMapper {
            decoder { it: TomlValue.List -> it.elements.size }
        }
        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertEquals(2, mapper.decode(toml))
        assertEquals(123, mapper.decode(TomlValue.Integer(123)))
    }

    @Test
    fun `can use multiple custom decoder functions`() {
        data class Foo(val value: Int)
        data class Bar(val value: String)
        data class Baz(val a: Foo, val b: Bar)
        val mapper = tomlMapper {
            decoder { it: TomlValue.Integer ->
                Foo(it.value.toInt())
            }

            decoder { it: TomlValue.Integer ->
                Bar(it.value.toString())
            }
        }
        val toml = TomlValue.Map(
            "a" to TomlValue.Integer(123),
            "b" to TomlValue.Integer(456),
        )
        assertEquals(Baz(Foo(123), Bar("456")), mapper.decode(toml))
    }

    @Test
    fun `decoder functions are searched in the correct order`() {
        val goodMapper = tomlMapper {
            decoder<TomlValue.List, Int> { it: TomlValue.List -> it.elements.size }
            decoder<TomlValue.List, Int> { _ -> error("should never get here") }
        }

        val badMapper = tomlMapper {
            decoder<TomlValue.List, Int> { _ -> error("boom!") }
            decoder<TomlValue.List, Int> { it: TomlValue.List -> it.elements.size }
        }

        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertEquals(2, goodMapper.decode(toml))
        assertFailsWith<IllegalStateException> { badMapper.decode<Int>(toml) }
    }

    @Test
    fun `can use pass to pass the ball to the next decoder`() {
        val mapper = tomlMapper {
            decoder<TomlValue.List, Int> { _, _ -> pass() }
            decoder { it: TomlValue.List -> it.elements.size }
        }

        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertEquals(2, mapper.decode(toml))
    }

    @Test
    fun `throws DecodingError if all decoders throw unsupported and no default decoder exists`() {
        val mapper = tomlMapper {
            decoder<TomlValue.List, Int> { _, _ -> pass() }
        }

        val toml = TomlValue.List(TomlValue.Integer(123), TomlValue.Bool(false))
        assertFailsWith<TomlException.DecodingError> { mapper.decode(toml) }
    }

    @Test
    fun `default decoder is used if all decoder functions pass`() {
        val mapper = tomlMapper {
            decoder<TomlValue.Integer, Int> { _, _ -> pass() }
        }

        val toml = TomlValue.Integer(123)
        assertEquals(123, mapper.decode(toml))
    }

    @Test
    fun `custom mapping does not get in the way of default decoder`() {
        val mapper = tomlMapper {
            mapping<User>("bar" to "fullName")
        }

        assertEquals(123, mapper.decode(TomlValue.Integer(123)))
        assertEquals(Config(emptyList()), mapper.decode(TomlValue.Map("users" to TomlValue.List())))
    }

    @Test
    fun `custom mapping can be used together with custom decoder function if they do not overlap`() {
        val mapper = tomlMapper {
            decoder { it: TomlValue.Integer -> User("Anonymous", it.value.toInt(), null) }
            mapping<User>("bar" to "fullName")
        }

        assertEquals(User("Anonymous", 123, null), mapper.decode(TomlValue.Integer(123)))
    }

    @Test
    fun `custom mapping does not let you convert maps into something else`() {
        val mapper = tomlMapper {
            mapping<User>("name" to "fullName")
        }
        assertFailsWith<TomlException.DecodingError> { mapper.decode<User>(TomlValue.Integer(123)) }
    }

    @Test
    fun `can add multiple mappings for the same type`() {
        val mapper = tomlMapper {
            mapping<User>("name" to "fullName")
            mapping<User>("years" to "age")
        }
        assertEquals(
            User("Anonymous", 123, null),
            mapper.decode(
                TomlValue.Map(
                    "name" to TomlValue.String("Anonymous"),
                    "years" to TomlValue.Integer(123)
                )
            )
        )
    }

    @Test
    fun `more than one kotlin property can't map to the same toml name`() {
        data class Test(val a: Int, val b: Int)
        val mapper = tomlMapper {
            mapping<Test>("x" to "a", "x" to "b")
        }
        assertFailsWith<TomlException.DecodingError> {
            mapper.decode(TomlValue.Map("x" to TomlValue.Integer(42)))
        }
    }

    @Test
    fun `the latest mapping is chosen when more than one toml name maps to the same kotlin property`() {
        data class Test(val x: Int)
        val mapper = tomlMapper {
            mapping<Test>("a" to "x", "b" to "x")
        }
        assertEquals(
            Test(123),
            mapper.decode(
                TomlValue.Map(
                    "a" to TomlValue.Integer(0),
                    "b" to TomlValue.Integer(123)
                )
            )
        )
    }

    @Test
    fun `can't add custom mapping for type without primary constructor`() {
        assertFailsWith<IllegalArgumentException> {
            tomlMapper {
                mapping<Map<*, *>>("name" to "initialCapacity")
            }
        }.also {
            assertContains(it.message ?: "", "does not have a primary constructor")
            assertContains(it.message ?: "", "Map")
        }

        assertFailsWith<IllegalArgumentException> {
            tomlMapper {
                mapping<List<*>>("name" to "initialCapacity")
            }
        }.also {
            assertContains(it.message ?: "", "does not have a primary constructor")
            assertContains(it.message ?: "", "List")
        }
    }

    @Test
    fun `can't add custom mapping for nonexistent property`() {
        data class Test(val foo: Int)

        assertFailsWith<IllegalArgumentException> {
            tomlMapper {
                mapping<Test>("name" to "KABOOM")
            }
        }.also {
            assertContains(it.message ?: "", "parameters do not exist")
            assertContains(it.message ?: "", "Test")
            assertContains(it.message ?: "", "KABOOM")
        }

        assertFailsWith<IllegalArgumentException> {
            tomlMapper {
                mapping<User>("name" to "KABOOM")
            }
        }.also {
            assertContains(it.message ?: "", "parameters do not exist")
            assertContains(it.message ?: "", "CustomDecoderTests.User")
            assertContains(it.message ?: "", "KABOOM")
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
        val mapper = tomlMapper {
            mapping<User>(
                "full_name" to "fullName",
                "address" to "homeAddress"
            )
            mapping<Config>("user" to "users")
        }

        val expected = Config(
            listOf(
                User("Bosse Bus", 47, null),
                User("Dolan Duck", 107, null)
            )
        )
        assertEquals(expected, mapper.decode(TomlValue.from(toml)))
    }
    data class User(val fullName: String, val age: Int, val homeAddress: String?)
    data class Config(val users: List<User>)

    @Test
    fun `can decode to null`() {
        val mapper = tomlMapper {
            decoder<TomlValue.Integer, Int> { _, _ -> null }
        }
        val toml = TomlValue.Integer(123)
        assertNull(mapper.decode<Int?>(toml))
    }

    @Test
    fun `defaulting does nothing with atomic types`() {
        val mapper = tomlMapper {
            default("wrong")
            default(listOf("wrong"))
        }
        assertEquals(
            "correct",
            mapper.decode(TomlValue.String("correct"))
        )
        assertEquals(
            listOf("correct"),
            mapper.decode(TomlValue.List(TomlValue.String("correct")))
        )
        assertEquals(
            emptyList<String>(),
            mapper.decode(TomlValue.List())
        )
    }

    @Test
    fun `defaults don't come into play for fully specified TOML`() {
        data class Foo(val a: Int, val b: String)
        val mapper = tomlMapper {
            default(Foo(0, ""))
        }
        val toml = TomlValue.Map(
            "a" to TomlValue.Integer(123),
            "b" to TomlValue.String("hej")
        )
        assertEquals(
            Foo(123, "hej"),
            mapper.decode(toml)
        )
    }

    @Test
    fun `defaults work properly in flat maps`() {
        data class Foo(val a: Int, val b: String)
        val mapper = tomlMapper {
            default(Foo(0, "defaulted"))
        }
        val toml = TomlValue.Map(
            "a" to TomlValue.Integer(123),
        )
        assertEquals(
            Foo(123, "defaulted"),
            mapper.decode(toml)
        )
        assertEquals(
            Foo(0, "defaulted"),
            mapper.decode(TomlValue.Map())
        )
    }

    @Test
    fun `defaults work properly in lists`() {
        data class Foo(val a: Int, val b: String)
        val mapper = tomlMapper {
            default(Foo(0, "defaulted"))
        }
        val toml = TomlValue.List(
            TomlValue.Map("a" to TomlValue.Integer(123)),
            TomlValue.Map("b" to TomlValue.String("hello")),
            TomlValue.Map("a" to TomlValue.Integer(321), "b" to TomlValue.String("hello")),
            TomlValue.Map(),
        )
        val expected = listOf(
            Foo(123, "defaulted"),
            Foo(0, "hello"),
            Foo(321, "hello"),
            Foo(0, "defaulted"),
        )
        assertEquals(
            expected,
            mapper.decode(toml)
        )
    }

    @Test
    fun `defaults work properly in nested maps`() {
        data class Foo(val a: Int, val b: String)
        data class Bar(val x: Foo, val y: Int)
        val mapper = tomlMapper {
            default(Foo(0, "defaulted"))
            default(Bar(Foo(1, "the whole foo is missing"), 2))
        }

        TomlValue.Map(
            "x" to TomlValue.Map(
                "a" to TomlValue.Integer(123)
            ),
            "y" to TomlValue.Integer(321),
        ).let { toml ->
            assertEquals(
                Bar(Foo(123, "defaulted"), 321),
                mapper.decode(toml)
            )
        }

        TomlValue.Map("y" to TomlValue.Integer(321)).let { toml ->
            assertEquals(
                Bar(Foo(1, "the whole foo is missing"), 321),
                mapper.decode(toml)
            )
        }
    }
}
