package cc.ekblad.toml

internal class TomlBuilder private constructor(private val values: MutableMap<String, MutableTomlValue>){
    operator fun set(fragments: List<String>, value: MutableTomlValue) {
        require(fragments.isNotEmpty())
        val fragmentsWithContext = tableContext?.let { it + fragments } ?: fragments
        set(fragmentsWithContext.first(), fragmentsWithContext.drop(1), value)
    }

    var tableContext: List<String>? = null

    fun build(): TomlValue = TomlValue.Map(values.mapValues { it.value.freeze() })

    private fun set(key: String, fragments: List<String>, value: MutableTomlValue) {
        when {
            fragments.isEmpty() -> {
                values[key] = value
            }
            else -> {
                val innerValues = values.getOrPut(key) { MutableTomlValue.Map(mutableMapOf()) }
                require(innerValues is MutableTomlValue.Map)
                val innerBuilder = TomlBuilder(innerValues.value)
                innerBuilder.set(fragments.first(), fragments.drop(1), value)
            }
        }
    }

    companion object {
        fun create(): TomlBuilder = TomlBuilder(mutableMapOf())
    }
}

internal sealed class MutableTomlValue {
    data class Map(val value: MutableMap<String, MutableTomlValue>) : MutableTomlValue()
    data class List(val value: MutableList<MutableTomlValue>) : MutableTomlValue()
    data class Primitive(val value: TomlValue.Primitive) : MutableTomlValue()

    fun freeze(): TomlValue = when (this) {
        is List -> TomlValue.List(value.map { it.freeze() })
        is Map -> TomlValue.Map(value.mapValues { it.value.freeze() })
        is Primitive -> value
    }
}
