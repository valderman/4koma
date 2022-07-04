package cc.ekblad.toml.transcoder

import cc.ekblad.toml.UnitTest
import cc.ekblad.toml.delegate
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class CustomEncoderTests : UnitTest {
    private object Dummy
    private data class Foo(val bar: String)
    private data class User(val fullName: String, val age: Int, val homeAddress: String?)

    @Test
    fun `can use custom encoder function`() {
        val mapper = tomlMapper {
            encoder { _: Dummy -> TomlValue.String("the dummy") }
        }
        assertEquals(TomlValue.String("the dummy"), mapper.encode(Dummy))
        assertEquals(TomlValue.Map("foo" to TomlValue.String("the dummy")), mapper.encode(mapOf("foo" to Dummy)))
    }

    @Test
    fun `newer encoder overrides older`() {
        val mapper = tomlMapper {
            encoder { _: Dummy -> fail("shouldn't get here") }
            encoder { _: Dummy -> TomlValue.String("the dummy") }
        }
        assertEquals(TomlValue.String("the dummy"), mapper.encode(Dummy))
        assertEquals(TomlValue.Map("foo" to TomlValue.String("the dummy")), mapper.encode(mapOf("foo" to Dummy)))
    }

    @Test
    fun `newer encoder can pass to older`() {
        val mapper = tomlMapper {
            encoder { _: Dummy -> TomlValue.String("the dummy") }
            encoder { _: Dummy -> pass() }
        }
        assertEquals(TomlValue.String("the dummy"), mapper.encode(Dummy))
        assertEquals(TomlValue.Map("foo" to TomlValue.String("the dummy")), mapper.encode(mapOf("foo" to Dummy)))
    }

    @Test
    fun `custom encoder can override default`() {
        val mapper = tomlMapper {
            encoder { n: Int -> TomlValue.String(n.toString()) }
        }
        assertEquals(TomlValue.String("123"), mapper.encode(123))
        assertEquals(TomlValue.Map("foo" to TomlValue.String("123")), mapper.encode(mapOf("foo" to 123)))
    }

    @Test
    fun `custom encoder can pass to default`() {
        val mapper = tomlMapper {
            encoder { _: Int -> pass() }
        }
        assertEquals(TomlValue.Integer(123), mapper.encode(123))
        assertEquals(TomlValue.Map("foo" to TomlValue.Integer(123)), mapper.encode(mapOf("foo" to 123)))
    }

    @Test
    fun `custom encoder can override default for data class`() {
        val mapper = tomlMapper {
            encoder { _: Foo -> TomlValue.String("foo") }
        }
        assertEquals(TomlValue.String("foo"), mapper.encode(Foo("123")))
        assertEquals(
            TomlValue.Map("foo" to TomlValue.String("foo")),
            mapper.encode(mapOf("foo" to Foo("123")))
        )
    }

    @Test
    fun `custom encoder can pass to default for data class`() {
        val mapper = tomlMapper {
            encoder { _: Foo -> pass() }
        }
        assertEquals(TomlValue.Map("bar" to TomlValue.String("123")), mapper.encode(Foo("123")))
        assertEquals(
            TomlValue.Map("foo" to TomlValue.Map("bar" to TomlValue.String("123"))),
            mapper.encode(mapOf("foo" to Foo("123")))
        )
    }

    @Test
    fun `can extend encoder with custom mapping`() {
        val mapper = tomlMapper {
            mapping<Foo>("hello" to "bar")
        }
        assertEquals(TomlValue.Map("hello" to TomlValue.String("123")), mapper.encode(Foo("123")))
    }

    @Test
    fun `can extend encoder with multiple custom mappings`() {
        data class Test(val a: Int, val b: Int, val c: Int)
        val mapper = tomlMapper {
            mapping<Test>("A" to "a", "B" to "b")
        }
        assertEquals(
            TomlValue.Map(
                "A" to TomlValue.Integer(1),
                "B" to TomlValue.Integer(2),
                "c" to TomlValue.Integer(3),
            ),
            mapper.encode(Test(1, 2, 3))
        )
    }

    @Test
    fun `can extend encoder with custom mappings in several calls`() {
        data class Test(val a: Int, val b: Int, val c: Int)
        val mapper = tomlMapper {
            mapping<Test>("A" to "a")
            mapping<Test>("B" to "b")
        }
        assertEquals(
            TomlValue.Map(
                "A" to TomlValue.Integer(1),
                "B" to TomlValue.Integer(2),
                "c" to TomlValue.Integer(3),
            ),
            mapper.encode(Test(1, 2, 3))
        )
    }

    @Test
    fun `newer mapping overrides older`() {
        data class Test(val a: Int, val b: Int, val c: Int)
        val mapper = tomlMapper {
            mapping<Test>("A" to "a")
            mapping<Test>("X" to "a")
        }
        assertEquals(
            TomlValue.Map(
                "X" to TomlValue.Integer(1),
                "b" to TomlValue.Integer(2),
                "c" to TomlValue.Integer(3),
            ),
            mapper.encode(Test(1, 2, 3))
        )
    }

    @Test
    fun `properties are serialized in order when there is a toml name clash`() {
        data class Test(val a: Int, val b: Int, val c: Int)
        val mapper = tomlMapper {
            mapping<Test>("b" to "a")
        }
        assertEquals(
            TomlValue.Map(
                "b" to TomlValue.Integer(2),
                "c" to TomlValue.Integer(3),
            ),
            mapper.encode(Test(1, 2, 3))
        )
    }

    @Test
    fun `cant register mapping with non-data class`() {
        class Test(val a: Int, val b: Int, val c: Int)

        assertFailsWith<IllegalArgumentException> {
            tomlMapper {
                mapping<Test>("A" to "a")
            }
        }.also {
            assertContains(it.message ?: "", "Test")
            assertContains(it.message ?: "", "is not a data class")
        }

        assertFailsWith<IllegalArgumentException> {
            tomlMapper {
                mapping<Dummy>("A" to "a")
            }
        }.also {
            assertContains(it.message ?: "", "CustomEncoderTests.Dummy")
            assertContains(it.message ?: "", "does not have a primary constructor")
        }
    }

    @Test
    fun `can't add custom mapping for nonexistent property`() {
        data class Test(val x: Int)

        assertFailsWith<IllegalArgumentException> {
            tomlMapper {
                mapping<Test>("x" to "KABOOM")
            }
        }.also {
            assertContains(it.message ?: "", "KABOOM")
            assertContains(it.message ?: "", "Test")
        }

        assertFailsWith<IllegalArgumentException> {
            tomlMapper {
                mapping<Foo>("x" to "KABOOM")
            }
        }.also {
            assertContains(it.message ?: "", "KABOOM")
            assertContains(it.message ?: "", "CustomEncoderTests.Foo")
        }
    }

    @Test
    fun `can delegate to another mapper`() {
        val otherMapper = tomlMapper {
            mapping<User>("name" to "fullName")
        }
        val mapper = tomlMapper {
            delegate<User>(otherMapper)
        }
        assertEquals(
            TomlValue.Map(
                "name" to TomlValue.String("Anonymous"),
                "age" to TomlValue.Integer(123)
            ),
            mapper.encode(User("Anonymous", 123, null))
        )
    }

    @Test
    fun `can override delegated mapper`() {
        val otherMapper = tomlMapper {
            mapping<User>("years" to "age")
            mapping<User>("name" to "homeAddress")
        }
        val mapper = tomlMapper {
            delegate<User>(otherMapper)
            mapping<User>("name" to "fullName")
        }

        assertEquals(
            TomlValue.Map(
                "name" to TomlValue.String("Anonymous"),
                "years" to TomlValue.Integer(123)
            ),
            mapper.encode(User("Anonymous", 123, null))
        )
    }

    @Test
    fun `delegate decoding of primitive values`() {
        val otherMapper = tomlMapper {
            encoder<Int> { TomlValue.Integer(42) }
        }
        val mapper = tomlMapper {
            delegate<Int>(otherMapper)
        }
        assertEquals(
            TomlValue.Map(
                "fullName" to TomlValue.String("Anonymous"),
                "age" to TomlValue.Integer(42)
            ),
            mapper.encode(User("Anonymous", 123, null))
        )
    }
}
