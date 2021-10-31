import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.5.31"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.0"
    antlr
    jacoco
}

group = "cc.ekblad"
version = "0.1"
val kotlinJvmTarget = 16

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    antlr("org.antlr:antlr4:4.9.2")

    testImplementation(kotlin("test"))
}

val compileKotlin: KotlinCompile by tasks
val compileTestKotlin: KotlinCompile by tasks
val compileJava: JavaCompile by tasks
val compileTestJava: JavaCompile by tasks

compileKotlin.dependsOn("generateGrammarSource")
compileKotlin.kotlinOptions {
    jvmTarget = kotlinJvmTarget.toString()
    
}
compileTestKotlin.dependsOn("generateGrammarSource")
compileTestKotlin.kotlinOptions {
    jvmTarget = kotlinJvmTarget.toString()
}
compileJava.options.release.set(kotlinJvmTarget)
compileTestJava.options.release.set(kotlinJvmTarget)

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
}
