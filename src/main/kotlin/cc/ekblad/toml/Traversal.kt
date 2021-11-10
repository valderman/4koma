package cc.ekblad.toml

import kotlin.reflect.KType
import kotlin.reflect.typeOf

/**
 * Look up the value(s) at the given path in the receiver TOML structure, then convert them to the type given by
 * @param T . If conversion is not possible, a [TomlException.ConversionError] is thrown.
 * If there is no value at the given path, null is returned.
 *
 * As an example, given the following TOML:
 *
 * <pre>
 * {@code
 * [[ user ]]
 * name = "Alice"
 * passwords = ["password123", "qwerty"]
 *
 * [[ user ]]
 * name = "Bob"
 * passwords = ["correct horse battery staple"]
 * }
 * </pre>
 *
 * {@code tomlDocument.get<List<String>>("user", "name")} will return {@code listOf("Alice", "Bob")}.
 *
 * Nested lists of values may be flattened, if necessary to make conversion possible.
 * For instance, while
 * {@code tomlDocument.get<List<List<String>>>("user", "passwords")} will return
 * {@code listOf(listOf("password123", "Bob"), listOf("correct horse battery staple"))},
 * {@code tomlDocument.get<List<String>>("user", "passwords")} will automatically flatten the list to fit the given
 * type parameter, and return {@code listOf("password123", "qwerty", "correct horse battery staple")}.
 *
 * If no type parameter is given, [Any] is assumed and returned values are converted to their closest Kotlin
 * counterparts.
 */
@OptIn(ExperimentalStdlibApi::class)
inline operator fun <reified T : Any> TomlValue.get(vararg path: String): T? =
    get(typeOf<T>(), path.toList())

fun <T> TomlValue.get(targetType: KType, path: List<String>): T? =
    get(path)?.flatten(targetType)?.convert(targetType)

private fun TomlValue.get(path: List<String>): TomlValue? = when {
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
