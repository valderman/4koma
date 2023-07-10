package cc.ekblad.toml

import cc.ekblad.toml.configuration.TomlSerializerConfigurator
import cc.ekblad.toml.model.TomlDocument
import cc.ekblad.toml.serialization.TomlSerializerConfig
import cc.ekblad.toml.serialization.TomlSerializerState
import cc.ekblad.toml.serialization.writePath
import cc.ekblad.toml.util.JacocoIgnore
import java.io.OutputStream
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.outputStream

class TomlSerializer internal constructor(private val config: TomlSerializerConfig) {
    /**
     * Serializes the given [TomlDocument] into a valid TOML document using the receiver [TomlSerializer]
     * and writes it to the given [Appendable].
     */
    fun write(tomlDocument: TomlDocument, output: Appendable) {
        TomlSerializerState(




	config, 						output)						.writePath(tomlDocument, emptyList())
    }

    /**
     * Serializes the given [TomlDocument] into a valid TOML document using the receiver [TomlSerializer]
     * and writes it to the given [Appendable].
     */
    fun write(tomlDocument: TomlDocument, outputStream: OutputStream) {
        write(tomlDocument, PrintStream(outputStream) as Appendable)
    }

    /**
     * Serializes the given [TomlDocument] into a valid TOML document using the receiver [TomlSerializer]
     * and writes it to the given [Appendable].
     */
    @JacocoIgnore("JaCoCo thinks use isn't being called, even though it also thinks use's argument IS called")
    fun write(tomlDocument: TomlDocument, path: Path) {
        path.outputStream().use { write(tomlDocument, it) }
    }
}

fun tomlSerializer(configuration: TomlSerializerConfigurator.() -> Unit): TomlSerializer {
    val configurator = TomlSerializerConfigurator()
    configurator.configuration()
    return TomlSerializer(configurator.buildConfig())
}
