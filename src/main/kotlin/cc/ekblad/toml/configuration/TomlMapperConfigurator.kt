package cc.ekblad.toml.configuration

import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.transcoding.TomlDecoder
import cc.ekblad.toml.transcoding.TomlEncoder
import cc.ekblad.toml.util.Generated
import cc.ekblad.toml.util.InternalAPI
import cc.ekblad.toml.util.KotlinName
import cc.ekblad.toml.util.TomlName
import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.full.primaryConstructor

@OptIn(InternalAPI::class)
class TomlMapperConfigurator internal constructor(
    private val encoders: MutableMap<KClass<*>, MutableList<TomlEncoder.(Any) -> TomlValue>>,
    private val decoders: MutableMap<KClass<*>, MutableList<TomlDecoder.(KType, TomlValue) -> Any?>>,
    private val mappings: MutableMap<KClass<*>, MutableMap<TomlName, KotlinName>>,
    private val defaultValues: MutableMap<KClass<*>, Any>
) {
    /**
     * Configures a custom property mapping for the type Kotlin type `T`, where `T` is any class with
     * a primary constructor.
     *
     * Having a custom property mapping from `"tomlName"` to `"kotlinName"` for some type `T` means that
     * whenever the decoder (a) is decoding a table (b) into a value of type `T`,
     * any constructor parameter of `T` with the name `"kotlinName"` will receive its value from a TOML property
     * with the name `"tomlName"`.
     *
     * As a motivating example, in a TOML document describing a list of users, it is natural to use the singular
     * of "user" to add new users to the list:
     *
     * <br>
     *
     * ```
     * [[user]]
     * name = 'Alice'
     * password = 'correcthorsebatterystaple'
     *
     * [[user]]
     * name = 'Bob'
     * password = 'password1'
     * ```
     *
     * <br>
     *
     * However, this makes less sense in the corresponding Kotlin type, where you would normally use the plural "users"
     * as the name for a list of users:
     *
     * <br>
     *
     * ```
     * data class User(val name: String, val password: String)
     * data class UserList(val users: List<User>)
     * ```
     *
     * <br>
     *
     * A custom mapping allows us to quickly bridge this gap, without compromising on either our Kotlin naming standards
     * or our configuration syntax:
     *
     * <br>
     *
     * ```
     * val myDecoder = TomlDecoder.default.withMapping<UserList>("user" to "users")
     * val myUsers = toml.from(Path.of("path", "to", "users.toml")).decode<UserList>(myDecoder)
     * ```
     *
     * <br>
     *
     * This also lets us rename fields in our model types while maintaining a stable configuration file syntax by simply
     * specifying a custom mapping, all without having to add intrusive annotations to model types
     * where they don't belong.
     *
     * Note that mappings do not affect types which are handled by custom encoder/decoder functions.
     */
    inline fun <reified T : Any> mapping(vararg mappings: Pair<TomlName, KotlinName>) =
        mapping(T::class, mappings.toList())

    /**
     * Configures a custom encoder function for the given Kotlin type.
     * A custom encoder function is a function from some Kotlin value to a [TomlValue].
     * Custom encoder functions are associated with a [KClass] representing the source type.
     *
     */
    inline fun <reified T : Any> encoder(noinline encoder: TomlEncoder.(kotlinValue: T) -> TomlValue) {
        encoder(T::class) @Generated { value ->
            if (value !is T) {
                pass()
            }
            encoder(value)
        }
    }

    /**
     * Configures a custom decoder function for the given Kotlin type.
     * A custom decoder function is a function from a [TomlValue] and a [KType] representing a target type,
     * to that target type. Custom decoder functions are associated with a [KClass] representing that target type.
     *
     * When a TOML value is decoded to some target type, the decoder will look for all decoder functions associated with
     * that type. All decoder functions matching that type are then tried in the order
     * they were registered with the decoder.
     * I.e. for some decoder `D = tomlMapper { decoder<T>(A) ; decoder<T>(B) }`,
     * `A` will always be tried before `B` when trying to decode values of type `T`.
     *
     * A decoder function can signal that they are unable to decode their given input by calling [TomlDecoder.pass].
     * When this happens, the decoder will go on to try the next relevant decoder, if any.
     *
     * Binding decoder functions to a KClass rather than a [KType], while allowing the decoder function to access that
     * KType, allows for more fine-grained control over deserialization. Let's say, for instance, that you have a custom
     * data structure, generic in its elements, that you want to decode TOML values into.
     *
     * If a decoder function was bound to a KType, you would need to register one decoder function for
     * `MyDataStructure<Int>`, one for `MyDataStructure<String>`, etc. - a lot of unnecessary boilerplate.
     *
     * If a decoder function was bound to a KClass and did not have access to the corresponding KType, you would have
     * no way of knowing the type of the elements of the data structure. You would instead be forced to rely on
     * the default decoding of TOML values - [TomlValue.Integer] into [Long], [TomlValue.Map] into [Map],
     * and so on - an unacceptable loss of functionality.
     *
     * A decoder function with access to the target type's KType, bound to the target type's KClass gets the best of
     * both worlds. As an example, here is how you would create a custom decoder function for the generic data structure
     * used in the above paragraphs.
     *
     * <br>
     *
     * ```
     * val myDecoder = TomlDecoder.default.with(
     *     MyDataStructure::class to { kType, tomlValue ->
     *         (tomlValue as? TomlValue.List)?.let {
     *             val myDataStructure = MyDataStructure<Any>()
     *             tomlValue.forEach {
     *                 it.convert(this, kType.arguments.single().type!!)
     *                 myDataStructure.add(convertedElement)
     *             }
     *             myDataStructure
     *             } ?: pass()
     *     }
     * )
     * val result = TomlValue.from(Path.of("path", "to", "file.toml")).decode(myDecoder)
     * ```
     *
     * <br>
     *
     */
    inline fun <reified T : TomlValue, reified R : Any> decoder(
        noinline decoder: TomlDecoder.(targetType: KType, tomlValue: T) -> R?
    ) {
        decoder(R::class) @Generated { kType, value ->
            if (value !is T) {
                pass()
            }
            decoder(kType, value)
        }
    }

    /**
     * Set the given [defaultValue] as the default for any missing values of type [T].
     * Any time an object of type T is being decoded but the TOML document is missing one or more fields
     * needed to construct the object, that value will be fetched from the default value.
     *
     * While it is possible to set defaults for atomic types, it will not have any effect as atomic types
     * have no fields.
     *
     * As an example , if we try to decode a TOML document which
     * only has the key `x = 42` into the type `data class Foo(val x: Int, val y: Int)`
     * with a default value of `Foo(0, 0)`, the result will be
     * `Foo(42, 0)`, as the `y` field is filled in from the default value.
     */
    fun <T : Any> default(defaultValue: T) {
        defaultValues[defaultValue::class] = defaultValue
    }

    /**
     * Convenience overload for [decoder], for when you don't need to consider the full target KType.
     */
    inline fun <reified T : TomlValue, reified R : Any> decoder(crossinline decoder: TomlDecoder.(tomlValue: T) -> R?) =
        decoder<T, R> @Generated { _, it -> decoder(it) }

    @InternalAPI
    fun <T : Any> mapping(kClass: KClass<T>, mappings: List<Pair<TomlName, KotlinName>>) {
        val className = kClass.qualifiedName ?: kClass.simpleName

        val constructor = requireNotNull(kClass.primaryConstructor) {
            "type $className does not have a primary constructor"
        }

        require(kClass.isData) {
            "mappings can only be registered for data classes, but $className is not a data class"
        }

        val parameterNames = constructor.parameters.map { it.name }.toSet()
        val missingParameterNames = mappings.filter { it.second !in parameterNames }
        require(missingParameterNames.isEmpty()) {
            val missingParameters = missingParameterNames.joinToString(", ") { it.second }
            "the following parameters do not exist on constructor for type $className: $missingParameters"
        }

        this.mappings.putIfAbsent(kClass, mappings.toMap(mutableMapOf()))?.putAll(mappings)
    }

    @InternalAPI
    fun <T : Any> encoder(kClass: KClass<T>, encoder: TomlEncoder.(kotlinValue: Any) -> TomlValue) {
        encoders.putIfAbsent(kClass, mutableListOf(encoder))?.add(encoder)
    }

    @InternalAPI
    fun <T : Any> decoder(kClass: KClass<T>, decoder: TomlDecoder.(targetType: KType, tomlValue: TomlValue) -> Any?) {
        decoders.putIfAbsent(kClass, mutableListOf(decoder))?.add(decoder)
    }

    internal fun buildConfig(): TomlMapperConfig =
        TomlMapperConfig(encoders, decoders, mappings, defaultValues)
}

internal data class TomlMapperConfig(
    val encoders: Map<KClass<*>, List<TomlEncoder.(Any) -> TomlValue>>,
    val decoders: Map<KClass<*>, List<TomlDecoder.(KType, TomlValue) -> Any?>>,
    val mappings: Map<KClass<*>, Map<TomlName, KotlinName>>,
    val defaultValues: Map<KClass<*>, Any>
)
