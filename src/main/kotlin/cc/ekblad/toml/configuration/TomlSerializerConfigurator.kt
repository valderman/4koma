package cc.ekblad.toml.configuration

import cc.ekblad.toml.serialization.CollectionSyntax
import cc.ekblad.toml.serialization.InlineListMode
import cc.ekblad.toml.serialization.TomlSerializerConfig

class TomlSerializerConfigurator internal constructor() {
    private var indentStep: Int = TomlSerializerConfig.default.indentStep
    private var inlineListMode: ListMode = TomlSerializerConfig.default.inlineListMode
    private var preferredTableSyntax: CollectionSyntax = TomlSerializerConfig.default.preferredTableSyntax
    private var preferredListSyntax: CollectionSyntax = TomlSerializerConfig.default.preferredListSyntax

    /**
     * Indent each nesting level of a multi-line lists by this many spaces.
     *
     * Default: 4
     */
    fun indentStep(spaces: Int) {
        indentStep = spaces
    }

    /**
     * How should inline lists be serialized?
     *
     * - If [ListMode.SingleLine], inline lists are always serialized on a single line.
     * - If [ListMode.MultiLine], inline lists are always serialized with one element per line, as long as the TOML
     *   spec allows it.
     * - If [ListMode.Adaptive], inline lists are serialized with one element per line only if they contain one or more
     *   list or table, as long as the TOML spec allows it.
     *
     * Default: ListMode.Adaptive
     */
    fun inlineListMode(listMode: ListMode) {
        inlineListMode = listMode
    }

    /**
     * Should tables be generated using table syntax or inline table syntax (i.e. curly brackets)?
     *
     * Default: [CollectionSyntax.Table]
     */
    fun preferTableSyntax(syntax: CollectionSyntax) {
        preferredTableSyntax = syntax
    }

    /**
     * Should tables be generated using table syntax or inline table syntax (i.e. square brackets)?
     * If [CollectionSyntax.Table], lists will still be serialized using inline syntax when required to produce
     * correct TOML.
     *
     * Default: [CollectionSyntax.Table]
     */
    fun preferListSyntax(syntax: CollectionSyntax) {
        preferredListSyntax = syntax
    }

    internal fun buildConfig(): TomlSerializerConfig = TomlSerializerConfig(
        indentStep = indentStep,
        inlineListMode = inlineListMode,
        preferredTableSyntax = preferredTableSyntax,
        preferredListSyntax = preferredListSyntax
    )
}

/**
 * When should list elements be separated by line breaks instead of spaces?
 */
typealias ListMode = InlineListMode

/**
 * Preferred syntax for lists and tables: inline or table?
 */
typealias CollectionSyntax = CollectionSyntax
