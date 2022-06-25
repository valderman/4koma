package cc.ekblad.toml.serialization

import cc.ekblad.toml.configuration.CollectionSyntax
import cc.ekblad.toml.model.TomlValue

internal class TomlSerializerState(
    private val config: TomlSerializerConfig,
    private val output: Appendable,
) {
    private object State {
        var indentationLevel: Int = 0
        var inMultilineList: Boolean = false
        var newlinesProhibited: Boolean = false
        var atStartOfLine: Boolean = true
        var indentation: String = ""
    }

    private fun indent(block: TomlSerializerState.() -> Unit) = try {
        State.indentationLevel += config.indentStep
        State.indentation = " ".repeat(State.indentationLevel)
        block()
        appendLine()
    } finally {
        State.indentationLevel -= config.indentStep
        State.indentation = " ".repeat(State.indentationLevel)
    }

    private fun shouldListBeMultiline(list: TomlValue.List): Boolean = when (config.inlineListMode) {
        InlineListMode.SingleLine -> false
        InlineListMode.MultiLine -> !State.newlinesProhibited
        InlineListMode.Adaptive -> !State.newlinesProhibited && list.elements.any { it.couldBeBig }
    }

    /**
     * Should the given value be serialized inline or as a table/table array, under the serializer's settings?
     */
    internal fun shouldBeInline(value: TomlValue): Boolean = when (value) {
        is TomlValue.Primitive -> true
        is TomlValue.Map -> config.preferredTableSyntax == CollectionSyntax.Inline
        is TomlValue.List -> config.preferredListSyntax == CollectionSyntax.Inline || value.containsNonMapElements
    }

    /**
     * Append the given strings to the output stream.
     * If the current line is empty, the given strings will be indented in accordance with
     * the current indentation level.
     */
    internal fun append(vararg strings: String) {
        if (State.atStartOfLine) {
            output.append(State.indentation)
        }
        strings.forEach { output.append(it) }
        State.atStartOfLine = false
    }

    /**
     * Append the given strings to the output stream as a single line, terminated by a newline character.
     * If the current line is empty, the entire line will be indented in accordance with the current indentation level.
     */
    internal fun appendLine(vararg strings: String) {
        if (strings.isNotEmpty() && State.atStartOfLine) {
            output.append(State.indentation)
        }
        strings.forEach { output.append(it) }
        output.appendLine()
        State.atStartOfLine = true
    }

    /**
     * Serialize a list, using the given block to output the list elements themselves.
     */
    internal fun list(list: TomlValue.List, block: TomlSerializerState.() -> Unit) {
        val wasInMultilineList = State.inMultilineList
        State.inMultilineList = shouldListBeMultiline(list)
        try {
            if (State.inMultilineList) {
                appendLine("[")
                indent(block)
                append("]")
            } else {
                append("[ ")
                block()
                append(" ]")
            }
        } finally {
            State.inMultilineList = wasInMultilineList
        }
    }

    /**
     * Serialize a map, using the given block to output the map elements themselves.
     */
    internal fun table(block: TomlSerializerState.() -> Unit) {
        val wereNewlinesProhibited = State.newlinesProhibited
        State.newlinesProhibited = true
        try {
            append("{ ")
            block()
            append(" }")
        } finally {
            State.newlinesProhibited = wereNewlinesProhibited
        }
    }

    /**
     * Append the appropriate list separator (depending on whether we're writing a multiline list or not)
     * to the output stream.
     */
    internal fun appendListSeparator() {
        if (State.inMultilineList) {
            appendLine(",")
        } else {
            append(", ")
        }
    }
}

internal data class TomlSerializerConfig(
    val indentStep: Int,
    val inlineListMode: InlineListMode,
    val preferredTableSyntax: cc.ekblad.toml.serialization.CollectionSyntax,
    val preferredListSyntax: cc.ekblad.toml.serialization.CollectionSyntax,
) {
    companion object {
        val default: TomlSerializerConfig = TomlSerializerConfig(
            indentStep = 4,
            inlineListMode = InlineListMode.Adaptive,
            preferredTableSyntax = CollectionSyntax.Table,
            preferredListSyntax = CollectionSyntax.Table
        )
    }
}

enum class InlineListMode { SingleLine, MultiLine, Adaptive }
enum class CollectionSyntax { Inline, Table }

/**
 * Is the given value potentially big enough to warrant putting it on its own line, when part of a list or object?
 */
private val TomlValue.couldBeBig: Boolean
    get() = this is TomlValue.List || this is TomlValue.Map

/**
 * Does the given list contain any elements that are not maps?
 */
private val TomlValue.List.containsNonMapElements: Boolean
    get() = elements.any { it !is TomlValue.Map }
