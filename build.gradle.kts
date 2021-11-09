import java.nio.file.Paths

plugins {
    kotlin("jvm") version "1.5.31"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.0"
    `maven-publish`
    antlr
    jacoco
}

group = "cc.ekblad"
version = "0.1"
val kotlinJvmTarget = 16

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            groupId = "cc.ekblad"
            artifactId = "4koma"
            version = "0.1"
            from(components["kotlin"])
        }
    }
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation(kotlin("reflect"))
    antlr("org.antlr:antlr4:4.9.2")

    testImplementation(kotlin("test"))
}

tasks {
    generateGrammarSource {
        outputDirectory = Paths.get(
            "build", "generated-src", "antlr", "main", "cc", "ekblad", "toml", "parser"
        ).toFile()
    }

    compileKotlin {
        dependsOn("generateGrammarSource")
        kotlinOptions {
            jvmTarget = kotlinJvmTarget.toString()
            freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
    compileTestKotlin {
        dependsOn("generateGrammarSource")
        kotlinOptions {
            jvmTarget = kotlinJvmTarget.toString()
            freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
    compileJava { options.release.set(kotlinJvmTarget) }
    compileTestJava { options.release.set(kotlinJvmTarget) }

    test {
        useJUnitPlatform()
        finalizedBy(jacocoTestReport)
    }

    check {
        dependsOn(test)
        dependsOn(ktlintCheck)
        dependsOn(jacocoTestCoverageVerification)
    }

    jacocoTestReport {
        dependsOn(test)

        // Exclude generated code from coverage report
        classDirectories.setFrom(files(classDirectories.files.filter { !it.path.contains("build/classes/java") }))
    }

    jacocoTestCoverageVerification {
        dependsOn(jacocoTestReport)
        violationRules {
            rule {
                limit {
                    minimum = BigDecimal(0.8)
                }
            }
        }
    }
}
