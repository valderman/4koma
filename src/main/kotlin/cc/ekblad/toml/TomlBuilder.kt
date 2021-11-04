package cc.ekblad.toml

internal class TomlBuilder private constructor() {
    fun set(line: Int, fragments: List<String>, value: MutableTomlValue) {
        require(fragments.isNotEmpty())
        val oldContext = tableContext
        defineTableInCurrentContext(line, fragments.dropLast(1), true)
        val previousValue = tableContext.putIfAbsent(fragments.last(), value)
        if (previousValue != null) {
            val path = fragments.joinToString(".")
            throw TomlException.ParseError("overwriting previously defined value at '$path' is not allowed", line)
        }
        tableContext = oldContext
    }

    fun addTableArrayEntry(line: Int, fragments: List<String>) {
        tableContext = topLevelTable
        defineTableInCurrentContext(line, fragments.dropLast(1), true)
        val list = tableContext.compute(fragments.last()) { _, previousValue ->
            val list = (previousValue as? MutableTomlValue.List) ?: MutableTomlValue.List(mutableListOf())
            if (previousValue != null && previousValue !is MutableTomlValue.List) {
                val path = fragments.joinToString(".")
                throw TomlException.ParseError("tried to append to non-list '$path'", line)
            }
            list.value.add(MutableTomlValue.Map(mutableMapOf()))
            list
        }
        check(list is MutableTomlValue.List)
        tableContext = list.value.last().value
    }

    fun defineTable(line: Int, fragments: List<String>) {
        tableContext = topLevelTable
        defineTableInCurrentContext(line, fragments, false)
    }

    /**
     * Tables may be redeclared if they're defined as prefix of a dotted key.
     */
    private tailrec fun defineTableInCurrentContext(
        line: Int,
        fragments: List<String>,
        allowTableRedeclaration: Boolean
    ) {
        if (fragments.isNotEmpty()) {
            val head = fragments.first()
            if (!allowTableRedeclaration && fragments.size == 1 && head in tableContext) {
                throw TomlException.ParseError("table '$head' already declared", line)
            }

            val newContext = when (val ctx = tableContext.getOrPut(head) {
                MutableTomlValue.Map(mutableMapOf())
            }) {
                is MutableTomlValue.Map -> ctx
                is MutableTomlValue.List -> ctx.value.last()
                is MutableTomlValue.InlineMap ->
                    throw TomlException.ParseError("extending inline table '$head' is not allowed", line)
                else ->
                    throw TomlException.ParseError("tried to extend non-table '$head'", line)
            }
            tableContext = newContext.value
            defineTableInCurrentContext(line, fragments.drop(1), allowTableRedeclaration)
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
    data class List(val value: MutableList<MutableTomlValue.Map>) : MutableTomlValue()
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
