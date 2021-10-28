package cc.ekblad.toml

internal class TomlBuilder private constructor() {
    operator fun set(fragments: List<String>, value: MutableTomlValue) {
        require(fragments.isNotEmpty())
        val oldContext = tableContext
        defineTableInCurrentContext(fragments.dropLast(1))
        val previousValue = tableContext.putIfAbsent(fragments.last(), value)
        check(previousValue == null)
        tableContext = oldContext
    }

    fun addTableArrayEntry(fragments: List<String>) {
        // TODO: table arrays with dotted keys
        require(fragments.size == 1)
        tableContext = mutableMapOf()
        val list = topLevelTable.getOrPut(fragments.single()) { MutableTomlValue.List(mutableListOf()) }
        check(list is MutableTomlValue.List)
        list.value.add(MutableTomlValue.Map(tableContext))
    }

    fun defineTable(fragments: List<String>) {
        tableContext = topLevelTable
        defineTableInCurrentContext(fragments)
    }

    private tailrec fun defineTableInCurrentContext(fragments: List<String>) {
        if (fragments.isNotEmpty()) {
            val newContext = tableContext.getOrPut(fragments.first()) { MutableTomlValue.Map(mutableMapOf()) }
            check(newContext is MutableTomlValue.Map)
            tableContext = newContext.value
            defineTableInCurrentContext(fragments.drop(1))
        }
    }

    private val topLevelTable: MutableMap<String, MutableTomlValue> = mutableMapOf()
    private var tableContext: MutableMap<String, MutableTomlValue> = topLevelTable

    fun build(): TomlValue.Map = TomlValue.Map(topLevelTable.mapValues { it.value.freeze() })

    companion object {
        fun create(): TomlBuilder = TomlBuilder()
    }
}

internal sealed class MutableTomlValue {
    data class Map(val value: MutableMap<String, MutableTomlValue>) : MutableTomlValue()
    data class List(val value: MutableList<MutableTomlValue>) : MutableTomlValue()
    data class Primitive(val value: TomlValue.Primitive) : MutableTomlValue()

    // Inline maps and lists are self-contained and thus immutable
    data class InlineMap(val value: TomlValue.Map) : MutableTomlValue()
    data class InlineList(val value: TomlValue.List) : MutableTomlValue()

    fun freeze(): TomlValue = when (this) {
        is List -> TomlValue.List(value.map { it.freeze() })
        is Map -> TomlValue.Map(value.mapValues { it.value.freeze() })
        is InlineMap -> value
        is InlineList -> value
        is Primitive -> value
    }
}
