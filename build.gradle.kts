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
val kotlinJvmTarget = "1.8"
val kotlinJavaRelease = 8

repositories {
    mavenCentral()
}

publishing {
    publications {
        create<MavenPublication>("4koma") {
            groupId = "cc.ekblad"
            artifactId = "4koma"
            version = "0.1"
            from(components["kotlin"])
            pom {
                name.set("4koma")
                description.set("Simple, standards-compliant TOML parser")
                url.set("https://github.com/valderman/4koma")
                licenses {
                    license {
                        name.set("MIT")
                        url.set("https://github.com/valderman/4koma/blob/main/LICENSE")
                    }
                }
                developers {
                    developer {
                        id.set("valderman")
                        name.set("Anton Ekblad")
                        email.set("anton@ekblad.cc")
                    }
                }
            }
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
        mustRunAfter("runKtlintCheckOverMainSourceSet")
    }

    generateTestGrammarSource {
        mustRunAfter("runKtlintCheckOverTestSourceSet")
    }

    compileKotlin {
        dependsOn("generateGrammarSource")
        kotlinOptions {
            jvmTarget = kotlinJvmTarget
            freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
    compileTestKotlin {
        dependsOn("generateTestGrammarSource")
        kotlinOptions {
            jvmTarget = kotlinJvmTarget
            freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
        }
    }
    compileJava { options.release.set(kotlinJavaRelease) }
    compileTestJava { options.release.set(kotlinJavaRelease) }

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

        // CSV report for coverage badge
        reports.csv.required.set(true)

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
