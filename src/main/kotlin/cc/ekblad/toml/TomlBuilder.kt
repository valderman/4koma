package cc.ekblad.toml

internal class TomlBuilder private constructor() {
    fun set(line: Int, fragments: List<String>, value: MutableTomlValue) {
        require(fragments.isNotEmpty())
        val oldContext = tableContext
        defineTableInCurrentContext(line, fragments.dropLast(1), true)
        val previousValue = tableContext.putIfAbsent(fragments.last(), value)
        if (previousValue != null) {
            val path = fragments.joinToString(".")
            throw TomlException("overwriting previously defined value at '$path' is not allowed", line)
        }
        tableContext = oldContext
    }

    fun addTableArrayEntry(line: Int, fragments: List<String>) {
        // TODO: table arrays with dotted keys
        require(fragments.size == 1)
        val head = fragments.single()
        tableContext = mutableMapOf()
        val list = topLevelTable.getOrPut(head) { MutableTomlValue.List(mutableListOf()) }
        when (list) {
            is MutableTomlValue.List -> { /* all ok! */ }
            is MutableTomlValue.InlineList ->
                throw TomlException("appending to inline array '$head' is not allowed", line)
            else ->
                throw TomlException("tried to append to non-list '$head'", line)
        }
        list.value.add(MutableTomlValue.Map(tableContext))
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
                throw TomlException("table '$head' already declared", line)
            }

            val newContext = tableContext.getOrPut(head) { MutableTomlValue.Map(mutableMapOf()) }
            when (newContext) {
                is MutableTomlValue.Map -> { /* all ok! */ }
                is MutableTomlValue.InlineMap ->
                    throw TomlException("extending inline table '$head' is not allowed", line)
                else ->
                    throw TomlException("tried to extend non-table '$head'", line)
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
