package cc.ekblad.toml

import java.time.LocalDate
import kotlin.test.Test

class ListTests : UnitTest {
    @Test
    fun `can parse empty list`() {
        listOf("[]", "[\n]", "[   ]", "[# this is a comment\n]", "[\n\n# this is a comment\n]").assertAll {
            assertParsesTo(TomlValue.List(listOf()), it)
        }
    }

    @Test
    fun `can parse list of inline tables`() {
        assertParsesTo(
            TomlValue.List(TomlValue.Map()),
            "[{}]"
        )
        assertParsesTo(
            TomlValue.List(
                TomlValue.Map("a" to TomlValue.Integer(1)),
                TomlValue.Map("b" to TomlValue.String("qwe"))
            ),
            "[{a=1},{b='qwe'}]"
        )
        assertParsesTo(
            TomlValue.List(
                TomlValue.Map("a" to TomlValue.Integer(1)),
                TomlValue.Map("b" to TomlValue.String("qwe"))
            ),
            "[  { a=1},\n{b='qwe'}\n]"
        )
    }

    @Test
    fun `can parse simple lists`() {
        listOf(
            listOf(1, 2, 3) to "[1,2,3]",
            listOf(1) to "[   1   ]",
            listOf(1, 2) to "[ 1 , 2 ]",
            listOf(2) to "[ 2]",
            listOf(1, 2) to "[1\n\n  ,\n\t2\n]",
            listOf(1, 2) to "[1\n# comment\n,\n2# comment\n]",
            listOf(1, 2) to "[ 1\n, 2\n]",
        ).assertAll { (list, expr) ->
            assertParsesTo(TomlValue.List(list.map { TomlValue.Integer(it.toLong()) }), expr)
        }
    }

    @Test
    fun `can parse nested lists`() {
        listOf(
            listOf(TomlValue.List(emptyList())) to "[[]]",
            listOf(TomlValue.List(listOf(TomlValue.List(listOf(TomlValue.List(emptyList())))))) to "[[[[]]]]",
            listOf(TomlValue.List(emptyList()), TomlValue.List(emptyList())) to "[[],[]]",
            listOf(TomlValue.List(emptyList())) to "[\n[\r\n] ]",
            listOf(
                TomlValue.List(listOf(TomlValue.String("a"))),
                TomlValue.List(listOf(TomlValue.String("b"), TomlValue.String("c"))),
            ) to "[[\n  'a'],['b',\n'c']]",
        ).assertAll { (list, expr) ->
            assertParsesTo(TomlValue.List(list), expr)
        }
    }

    @Test
    fun `can parse mixed lists`() {
        listOf(
            listOf(
                TomlValue.List(emptyList()),
                TomlValue.Integer(123),
                TomlValue.Double(Double.NaN)
            ) to "[[],123,-nan]",
            listOf(
                TomlValue.List(listOf(TomlValue.String("\n\thej"))),
                TomlValue.Double(1.23e2),
                TomlValue.Bool(false),
                TomlValue.LocalDate(LocalDate.of(2011, 11, 11))
            ) to "[['''\n\n\thej'''], 1.23e2 ,false,2011-11-11]",
        ).assertAll { (list, expr) ->
            assertParsesTo(TomlValue.List(list), expr)
        }
    }

    @Test
    fun `throws on bad list`() {
        listOf("][", "[[", "[[]", "[]]", "]]", "[,]", "[1,,2]", "[1,,]", "[,1]").assertAll(::assertValueParseError)
    }
}
