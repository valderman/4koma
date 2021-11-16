package cc.ekblad.toml

import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Look up the value(s) at the given path in the receiver TOML structure, then convert them to the type given by
 * the type parameter `T`. If conversion is not possible, a [TomlException.ConversionError] is thrown.
 * If there is no value at the given path, `null` is returned.
 *
 * <br>
 *
 * As an example, given the following TOML:
 *
 * <br>
 *
 * ```
 * [[ user ]]
 * name = "Alice"
 * passwords = ["password123", "qwerty"]
 *
 * [[ user ]]
 * name = "Bob"
 * passwords = ["correct horse battery staple"]
 * ```
 *
 * <br>
 *
 * A call to `tomlDocument.get<List<String>>("user", "name")` will return `listOf("Alice", "Bob")`.
 *
 * <br>
 *
 * Nested lists of values may be flattened, if necessary to make conversion possible.
 * For instance, while
 * `tomlDocument.get<List<List<String>>>("user", "passwords")` will return
 * `listOf(listOf("password123", "Bob"), listOf("correct horse battery staple"))`,
 * `tomlDocument.get<List<String>>("user", "passwords")` will automatically flatten the list to fit the given
 * type parameter, and return `listOf("password123", "qwerty", "correct horse battery staple")`.
 */
@OptIn(ExperimentalStdlibApi::class)
inline operator fun <reified T : Any> TomlValue.get(vararg path: String): T? =
    get(typeOf<T>(), path.toList())

fun <T> TomlValue.get(targetType: KType, path: List<String>): T? =
    get(TomlConverter.default, targetType, path)

@OptIn(ExperimentalStdlibApi::class)
inline fun <reified T : Any> TomlValue.get(converter: TomlConverter, vararg path: String): T? =
    get(converter, typeOf<T>(), path.toList())

fun <T> TomlValue.get(converter: TomlConverter, targetType: KType, path: List<String>): T? =
    get(path)?.flatten(targetType)?.convert(converter, targetType)

@Suppress("NON_TAIL_RECURSIVE_CALL")
private tailrec fun TomlValue.get(path: List<String>): TomlValue? = when {
    path.isEmpty() -> this
    this is TomlValue.Map -> properties[path.first()]?.get(path.drop(1))
    this is TomlValue.List -> TomlValue.List(elements.mapNotNull { it.get(path) })
    else -> null
}

private fun TomlValue.flatten(targetType: KType): TomlValue = when {
    this.isNestedList() && !targetType.elementType.isListType() ->
        TomlValue.List(
            (this as TomlValue.List).elements.map { (it.flatten(targetType) as TomlValue.List).elements }.flatten()
        )
    else ->
        this
}

private fun TomlValue.isNestedList(): Boolean =
    this is TomlValue.List && elements.all { it is TomlValue.List }

@OptIn(ExperimentalStdlibApi::class)
private val KType.elementType: KType
    get() = arguments.firstOrNull()?.type ?: typeOf<Any>()

private fun KType.isListType(): Boolean =
    this.classifier in setOf(List::class, MutableList::class, Collection::class, Iterable::class)
