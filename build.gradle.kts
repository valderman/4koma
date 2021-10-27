plugins {
    kotlin("jvm") version "1.5.31"
    antlr
}

group = "cc.ekblad"
version = "0.1"

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    antlr("org.antlr:antlr4:4.9.2")

    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
