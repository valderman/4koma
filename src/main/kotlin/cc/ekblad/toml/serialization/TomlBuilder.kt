package cc.ekblad.toml.serialization

import cc.ekblad.toml.model.TomlException
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.util.Generated

internal class TomlBuilder private constructor() {
    sealed interface Context {
        companion object {
            fun new(): Context = ContextImpl(mutableMapOf())
        }

        /**
         * Explicitly define a key in the current context. An explicitly defined key may never be explicitly assigned
         * to again. However, if the value is a (non-inline) table or array, it may be extended with additional
         * properties or additional elements respectively.
         */
        fun set(line: Int, key: String, value: MutableTomlValue) {
            val previousValue = (this as ContextImpl).properties.putIfAbsent(key, value)
            if (previousValue != null && !canOverwrite(previousValue, value)) {
                throw TomlException.ParseError("overwriting previously defined value at '$key' is not allowed", line)
            }
            // If this was previously an implicitly defined table, it's now explicitly defined.
            if (previousValue is MutableTomlValue.Map) {
                previousValue.redefinable = false
            }
        }

        private fun canOverwrite(oldValue: MutableTomlValue, newValue: MutableTomlValue): Boolean =
            oldValue is MutableTomlValue.Map && newValue is MutableTomlValue.Map && oldValue.redefinable

        fun asMap(): MutableTomlValue.Map =
            MutableTomlValue.Map((this as ContextImpl).properties, false)

        /**
         * Returns the subcontext at the given key, if any. Returns null if the context has no such key, or if the
         * key does not refer to a context.
         */
        fun subcontext(key: String): Context? =
            ((this as ContextImpl).properties[key] as? MutableTomlValue.Map)?.let { ContextImpl(it.value) }
    }

    @JvmInline
    @Generated
    private value class ContextImpl(val properties: MutableMap<String, MutableTomlValue>) : Context

    fun resetContext() {
        tableContext = topLevelTable
    }

    fun setContext(ctx: Context) {
        tableContext = ctx as ContextImpl
    }

    /**
     * Add an entry to the table at the given key in the receiver context.
     * If the table does not already exist, it is created.
     */
    fun Context.addTableArrayEntry(line: Int, key: String): Context {
        val list = (this as ContextImpl).properties.compute(key) { _, previousValue ->
            val list = (previousValue as? MutableTomlValue.List) ?: MutableTomlValue.List(mutableListOf())
            if (previousValue != null && previousValue !is MutableTomlValue.List) {
                throw TomlException.ParseError("tried to append to non-list '$key'", line)
            }
            list.value.add(MutableTomlValue.Map(mutableMapOf(), false))
            list
        } as MutableTomlValue.List?

        // We never remove anything from the list, so we're guaranteed to never see null here.
        return ContextImpl(list!!.value.last().value)
    }

    /**
     * Define a hierarchy of tables (i.e. foo.bar.baz), either explicitly or implicitly.
     * If explicitly, table in the hierarchy may be explicitly redefined using \[\[table]] syntax.
     */
    fun defineTable(line: Int, fragments: List<String>, implicit: Boolean): Context =
        fragments.fold(tableContext.properties) { context, fragment ->
            when (val newContext = context.getOrPut(fragment) { MutableTomlValue.Map(mutableMapOf(), implicit) }) {
                is MutableTomlValue.Map -> newContext.value
                is MutableTomlValue.List -> newContext.value.last().value
                is MutableTomlValue.InlineMap -> throw TomlException.ParseError(
                    "extending inline table '$fragment' is not allowed",
                    line
                )
                else -> throw TomlException.ParseError(
                    "tried to extend non-table '$fragment'",
                    line
                )
            }
        }.let(TomlBuilder::ContextImpl)

    private val topLevelTable: ContextImpl = Context.new() as ContextImpl
    private var tableContext: ContextImpl = topLevelTable

    fun build(): TomlValue.Map = TomlValue.Map(topLevelTable.properties.mapValues { it.value.freeze() })

    companion object {
        fun create(): TomlBuilder = TomlBuilder()
    }
}

internal sealed class MutableTomlValue {
    data class Map(val value: MutableMap<String, MutableTomlValue>, var redefinable: Boolean) : MutableTomlValue()
    data class List(val value: MutableList<Map>) : MutableTomlValue()
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
