# 4koma
[![Docs](https://img.shields.io/badge/docs-latest-informational)](http://valderman.github.io/4koma/4koma/cc.ekblad.toml/)
[![Release](https://jitpack.io/v/cc.ekblad/4koma.svg)](https://jitpack.io/#cc.ekblad/4koma)
![Build Status](https://github.com/valderman/4koma/workflows/CI/badge.svg)
![Coverage](.github/badges/jacoco.svg)
[![License](https://img.shields.io/github/license/valderman/4koma)](https://github.com/valderman/4koma/blob/main/LICENSE)
<img align="left" width="100" src="logo.png" style="margin-right: 1em">


A small, stand-alone, easy to use TOML parser library for Kotlin.

4koma supports an array of convenient features, such as full TOML 1.0 compliance,
type-safe decoding of configurations into arbitrary data classes using Kotlin generics,
serialization support, and easy access to individual properties for when you don't need the entire document.

4koma follows the UNIX philosophy, in that it tries to do one thing, and do it well.
If what you need is no-nonsense TOML processing for your Kotlin project, 4koma might be just what you need.
If you need support for multiple configuration formats, or are using a JVM language other than Kotlin,
some of the projects listed in the [Alternatives](#alternatives) section might serve you better.


## Usage
Getting started with 4koma is super easy.

### 1. Add a dependency on 4koma
For `build.gradle.kts`:
```kotlin
repositories {
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation("cc.ekblad:4koma:1.0.1")
}
```

### 2. Obtain a TOML file
```toml
[settings]
maxLoginRetries = 3

[[user]]
name = "Alice"
password = "password123"

[[user]]
name = "Bob"
password = "correct horse battery staple"
```

### 3. Write some code

#### A minimal example
```kotlin
import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import java.nio.file.Path

data class Config(
    val settings: Settings,
    val user: List<User>
) {
    data class User(val name: String, val password: String)
    data class Settings(val maxLoginRetries: Int)
}

fun main() {
    // Create a TOML mapper without any custom configuration
    val mapper = tomlMapper { }

    // Read our config from file
    val tomlFile = Path.of("test.toml")
    val config = mapper.decode<Config>(tomlFile)
    println(config)
}
```

#### Default values
```kotlin
import cc.ekblad.toml.decodeWithDefaults
import cc.ekblad.toml.tomlMapper
import java.nio.file.Path

// We extend our config with extra fields (e.g. isAdmin, realmName and useTLS)
// which are not configured in our example TOML document.
data class Config(
    val settings: Settings,
    val user: List<User>
) {
    data class User(val name: String, val password: String, val isAdmin: Boolean)
    data class Settings(val maxLoginRetries: Int, val realmName: String, val useTLS: Boolean)
}

// Then, we define a default configuration, from which we will fill in any missing values when we read the config.
val defaultConfig = Config(
    settings = Config.Settings(
        maxLoginRetries = 5,
        realmName = "Example Realm",
        useTLS = true
    ),
    user = emptyList()
)

// We also define a default value for our user type, as we extended it with an extra field.
val defaultUser = Config.User(
    name = "default name",
    password = "default password",
    isAdmin = false
)

fun main() {
    // Create a TOML mapper with default fields configured for our Config.User type.
    val mapper = tomlMapper {
        default(defaultUser)
    }

    // Read our config from file, replacing any missing values with the defaults from defaultConfig and defaultUser.
    val tomlFile = Path.of("test.toml")
    val config = mapper.decodeWithDefaults<Config>(defaultConfig, tomlFile)
    println(config)
}
```

#### Mapping between TOML and Kotlin field names
```kotlin
import cc.ekblad.toml.decode
import cc.ekblad.toml.tomlMapper
import java.nio.file.Path

data class Config(
    val settings: Settings,
    val users: List<User>
) {
    data class User(val userName: String, val password: String)
    data class Settings(val retriesAfterWhichToDisableLogin: Int)
}

fun main() {
    // Set up a mapper which maps "user", "name", etc. fields found in the TOML file onto fields "users", "userName",
    // etc. in their respective Kotlin data types
    val mapper = tomlMapper {
        mapping<Config>("user" to "users")
        mapping<Config.User>(
            "name" to "userName",
            "password" to "userSecret"
        )
        mapping<Config.Settings>(
            "maxLoginRetries" to "retriesAfterWhichToDisableLogin"
        )
    }

    // Read our config from file, using our custom field name mappings
    val tomlFile = Path.of("test.toml")
    val config = mapper.decode<Config>(tomlFile)
    println(config)
}
```

#### Custom decoders
```kotlin
import cc.ekblad.toml.decode
import cc.ekblad.toml.model.TomlValue
import cc.ekblad.toml.tomlMapper
import java.nio.file.Path

fun main() {
    // Set up a mapper which replaces any string appearing in the list of bad words with a number of asterisks
    val listOfBadWords = listOf("poop", "darn", "blockchain")
    val mapper = tomlMapper {
        decoder { it: TomlValue.String ->
            if (it.value in listOfBadWords) {
                // We don't allow any swearing in our strings!
                it.value.map { '*' }.joinToString("")
            } else {
                it.value
            }
        }
    }

    // Read an arbitrary TOML document from file, using our custom string decoder
    val tomlFile = Path.of("test.toml")
    val config = mapper.decode<Map<String, Any>>(tomlFile)
    println(config)
}
```

For more detailed information, see the [API documentation](http://valderman.github.io/4koma/4koma/cc.ekblad.toml/).

## <span id="alternatives">Alternatives</span>

Why should you use 4koma? Maybe you shouldn't! When it comes to TOML libraries there are several to choose from.
This table compares 4koma with a number of alternatives that (a) can parse TOML, (b) can be reasonably easily used in
a Kotlin/JVM project, and (c) have seen at least one new commit in the last four years.

If you'd like your library to be on this list, or it's already there and you believe it's being misrepresented,
please open a pull request to rectify the situation.

| Feature | 4koma | [KToml](https://github.com/akuleshov7/ktoml) | [konf](https://github.com/uchuhimo/konf) | [konfy](https://github.com/TanVD/konfy) | [toml4j](https://github.com/mwanji/toml4j) | [tomlj](https://github.com/tomlj/tomlj) | [jtoml](https://github.com/agrison/jtoml) | [Night Config](https://github.com/TheElectronWill/Night-Config) | [Jackson](https://github.com/FasterXML/jackson-dataformats-text)
| -------------------------------- | :--: | :--: | :--: | :--: | :--: | :--: | :--: | :--: | :--: |
| TOML 1.0 compliant               |  ✅  |  ❌  |  ✅  |  ❌  |  ❌  |  ❌  |  ❌  |  ✅  |  ✅  |
| TOML 0.4 compliant               |  ✅  |  ❌  |  ✅  |  ❌  |  ✅  |  ✅  |  ❌  |  ✅  |  ✅  |
| Inline tables                    |  ✅  |  ❌  |  ✅  |  ❌  |  ✅  |  ✅  |  ❌  |  ✅  |  ✅  |
| Table arrays                     |  ✅  |  ❌  |  ✅  |  ❌  |  ❌  |  ✅  |  ❌  |  ✅  |  ✅  |
| Date/time literals               |  ✅  |  ❌  |  ✅  |  ❌  |  ❌  |  ✅  |  ✅  |  ✅  |  ✅  |
| Easy property access¹            |  ✅  |  ❌  |  ✅  |  ❌  |  ✅  |  ✅  |  ✅  |  ✅  |  ✅  |
| Decodes to Kotlin types          |  ✅  |  ✅  |  ✅  |  ✅  |  ✅  |  ❌  |  ✅  |  ✅  |  ✅  |
| ...without extra boilerplate²    |  ✅  |  ❌  |  ❌  |  ❌  |  ✅  |  ❌  |  ✅  |  ✅  |  ✅  |
| ...without modification to type  |  ✅  |  ❌  |  ❌  |  ❌  |  ✅  |  ❌  |  ✅  |  ✅  |  ✅  |
| Type-safe generic decoding³      |  ✅  |  ❔  |  ✅  |  ✅  |  ❌  |  ❌  |  ❌  |  ❌  |  ❌  |
| Kotlin multiplatform             |  ❌  |  ✅  |  ❌  |  ❌  |  ❌  |  ❌  |  ❌  |  ❌  |  ❌  |
| Serialization                    |  ✅  |  ❌  |  ✅  |  ❌  |  ✅  |  ❌  |  ✅  |  ✅  |  ✅  |
| Online API docs                  |  ✅  |  ❌  |  ❌  |  ❌  |  ❌  |  ✅  |  ❌  |  ✅  |  ✅  |
| Small and lean⁴                  |  ✅  |  ✅  |  ❌  |  ❌  |  ✅  |  ✅  |  ✅  |  ❌  |  ❌  |
| Everything but the kitchen sink⁵ |  ❌  |  ❌  |  ✅  |  ✅  |  ❌  |  ❌  |  ❌  |  ✅  |  ✅  |

(¹) Individual properties can be accessed by means of `parsedConfig.get("foo.bar")` or similar,
without requiring the entire document to be decoded into some model type.

(²) The library does not require annotations or other modifications to existing code in order to support decoding
to complex model types.

(³) The library does not rely on type-erased JVM generics for decoding to complex model types.

(⁴) The library focuses on reading/writing/processing TOML and does not contain any "unnecessary" features
unrelated to that scope.

(⁵) The library aims to provide a comprehensive configuration platform, with support for multiple configuration file
formats and all sorts of associated bells and whistles.
