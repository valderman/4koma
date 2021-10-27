package cc.ekblad.toml

import kotlin.random.Random

interface RandomTest : UnitTest {
    companion object {
        val random = Random(42)
    }
}

fun <T> Random.values(numValues: Int, gen: Random.() -> T): List<T> =
    (1..numValues).map { gen() }
