# 4koma
![Build Status](https://github.com/valderman/4koma/workflows/build-and-test/badge.svg)
![Coverage](.github/badges/jacoco.svg)
[![License](https://img.shields.io/github/license/valderman/4koma)](https://github.com/valderman/4koma/blob/main/LICENSE)


A small, stand-alone, easy to use TOML 1.0 parser library for Kotlin.


## Usage

### 1. Add a dependency on 4koma
For `build.gradle.kts`:
```kotlin
repositories {
  maven{
    url = uri("https://jitpack.io")
  }
}

dependencies {
    implementation("cc.ekblad:4koma:0.1")
}
```

### 2. Obtain a TOML file
```toml
[settings]
maxLoginTries = 3

[[user]]
name = "Alice"
password = "password123"

[[user]]
name = "Bob"
password = "correct horse battery staple"
```

### 3. Write some code
```kotlin
import cc.ekblad.toml.TomlValue
import cc.ekblad.toml.convert
import cc.ekblad.toml.get
import java.nio.file.Path

data class Config(
    val settings: Settings,
    val user: List<User>
) {
    data class User(val name: String, val password: String)
    data class Settings(val maxLoginRetries: Int)
}

fun main() {
    // Parse a TOML document from a string, stream, or file
    val tomlDocument = TomlValue.from(Path.of("test.toml"))

    // Convert it to your config type
    val config = tomlDocument.convert<Config>()

    // If you're lazy, just convert it to a map
    val mapConfig = tomlDocument.convert<Map<String, Any>>()

    // ...or access properties directly
    val maxLoginTries = tomlDocument["settings", "maxLoginTries"]

    // ...or just grab a part of the document and convert it to some convenient data class
    val settings = tomlDocument.get<Config.Settings>("settings")
    val users = tomlDocument.get<List<User>>("user")

    // You can also access properties on objects inside lists
    val userNames = tomlDocument["user", "name"] // <- returns listOf("Alice", "Bob")
}
```