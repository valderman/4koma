package cc.ekblad.toml.parser

import cc.ekblad.toml.StringTest
import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.serialization.from
import cc.ekblad.toml.transcoding.requireKClass
import org.antlr.v4.runtime.InputMismatchException
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class MiscTests : StringTest {
    private val test_toml = TomlValue.Map(
        "section" to TomlValue.Map(
            "int" to TomlValue.Integer(123),
            "strings" to TomlValue.List(
                TomlValue.String("foo"),
                TomlValue.String("bar"),
                TomlValue.String("baz")
            )
        )
    )

    @Test
    fun `can parse from input stream`() {
        this::class.java.classLoader.getResourceAsStream("test.toml")?.use {
            assertEquals(test_toml, TomlValue.from(it))
        } ?: error("couldn't open resource")
    }

    @Test
    fun `can parse from path`() {
        val url = this::class.java.classLoader.getResource("test.toml") ?: error("couldn't find resource")
        assertEquals(test_toml, TomlValue.from(Path.of(url.toURI())))
    }

    @Test
    fun `can parse trailing whitespace`() {
        assertParsesTo(TomlValue.Bool(true), "true    ")
        assertParsesTo(TomlValue.Bool(false), "false\n\n")
    }

    @Test
    fun `can parse bizarre mix of indentation and comments`() {
        val actual = TomlValue.from(
            """
               [section]#attached comment
               #[notsection]
               one = "11"#cmt
               two = "22#"
               three = '#'
               
               four = ""${'"'}# no comment
               # nor this
               #also not comment""${'"'}#is_comment
               
               five = 5.5#66
               six = 6#7
               8 = "eight"
               #nine = 99
               ten = 10e2#1
               eleven = 1.11e1#23
               
               ["hash#tag"]
               "#!" = "hash bang"
               arr3 = [ "#", '#', ""${'"'}###""${'"'} ]
               arr4 = [ 1,# 9, 9,
               2#,9
               ,#9
               3#]
               ,4]
               arr5 = [[[[#["#"],
               ["#"]]]]#]
               ]
               tbl1 = { "#" = '}#'}#}}
            """.trimIndent()
        )
        val expected = TomlValue.Map(
            "section" to TomlValue.Map(
                "one" to TomlValue.String("11"),
                "two" to TomlValue.String("22#"),
                "three" to TomlValue.String("#"),

                "four" to TomlValue.String("# no comment\n# nor this\n#also not comment"),
                "five" to TomlValue.Double(5.5),
                "six" to TomlValue.Integer(6),
                "8" to TomlValue.String("eight"),
                "ten" to TomlValue.Double(10e2),
                "eleven" to TomlValue.Double(1.11e1)
            ),
            "hash#tag" to TomlValue.Map(
                "#!" to TomlValue.String("hash bang"),
                "arr3" to TomlValue.List(
                    TomlValue.String("#"),
                    TomlValue.String("#"),
                    TomlValue.String("###")
                ),
                "arr4" to TomlValue.List(
                    TomlValue.Integer(1),
                    TomlValue.Integer(2),
                    TomlValue.Integer(3),
                    TomlValue.Integer(4)
                ),
                "arr5" to TomlValue.List(
                    TomlValue.List(TomlValue.List(TomlValue.List(TomlValue.List(TomlValue.String("#")))))
                ),
                "tbl1" to TomlValue.Map("#" to TomlValue.String("}#"))
            )
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `throws on trailing garbage`() {
        assertDocumentParseError(
            """
                [foo]
                bar = 123
                
                HELLO I AM GARBAGE
            """.trimIndent()
        )
    }

    @Test
    fun `parse error contains correct line number, cause and description`() {
        val exception = assertFailsWith<TomlException.ParseError> {
            TomlValue.from(
                """
                    [foo]
                    bar = 123
                    
                    [[asdf]]
                    bagu
                    fgs = "hello"
                """.trimIndent()
            )
        }
        assertEquals(5, exception.line)
        assertContains(exception.message, "line 5")
        assertContains(exception.message, exception.errorDescription)
        assertContains(exception.errorDescription, "mismatched input")
        assertIs<InputMismatchException>(exception.cause)
    }

    @Test
    fun `throws on unspecified value`() {
        assertDocumentParseError("foo =")
    }

    @Test
    fun `throws on comment containing ASCII control char`() {
        asciiControlChars.assertAll {
            assertDocumentParseError("# Bad comment: $it (${it.code})")
        }
    }

    @Test
    fun `throws on key containing plus sign`() {
        assertDocumentParseError("+12 = 12")
    }

    @Test
    fun `requireKClass throws on non-KClass`() {
        assertFailsWith<IllegalArgumentException> {
            requireKClass(Map::class.typeParameters.first())
        }
    }

    @Test
    fun `requireKClass throws on null classifier`() {
        assertFailsWith<IllegalArgumentException> {
            requireKClass(null)
        }
    }
}
