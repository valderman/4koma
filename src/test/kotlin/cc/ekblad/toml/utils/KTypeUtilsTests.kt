package cc.ekblad.toml.utils

import cc.ekblad.toml.util.subst
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals

class KTypeUtilsTests {
    @Test
    fun `can substitute type variable on top level`() {
        data class Foo<T>(val x: T)
        val type = Foo::class.primaryConstructor!!.parameters.single().type
        val parameter = Foo::class.typeParameters.single()
        assertEquals(
            typeOf<Int>(),
            type.subst(mapOf(parameter to typeOf<Int>()))
        )
    }

    @Test
    fun `can substitute type variable in type argument`() {
        data class Foo<T>(val x: List<T>)
        val type = Foo::class.primaryConstructor!!.parameters.single().type
        val parameter = Foo::class.typeParameters.single()
        assertEquals(
            typeOf<List<Int>>(),
            type.subst(mapOf(parameter to typeOf<Int>()))
        )
    }

    @Test
    fun `can substitute type variable in nested type argument`() {
        data class Foo<T>(val x: List<List<T>>)
        val type = Foo::class.primaryConstructor!!.parameters.single().type
        val parameter = Foo::class.typeParameters.single()
        assertEquals(
            typeOf<List<List<List<*>>>>(),
            type.subst(mapOf(parameter to typeOf<List<*>>()))
        )
    }

    @Test
    fun `substitution doesn't touch free type variables`() {
        data class Foo<T>(val x: List<List<T>>)
        val type = Foo::class.primaryConstructor!!.parameters.single().type
        assertEquals(
            type,
            type.subst(emptyMap())
        )
    }

    @Test
    fun `performing substitution on a star projection returns the original projection`() {
        val type = typeOf<List<*>>()
        assertEquals(
            type.arguments.single(),
            type.subst(emptyMap()).arguments.single()
        )
    }
}
