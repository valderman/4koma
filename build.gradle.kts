import java.nio.file.Paths
import kotlin.collections.listOf

plugins {
    kotlin("jvm") version "1.6.0"
    id("org.jetbrains.dokka") version "1.5.31"
    id("org.jlleitschuh.gradle.ktlint") version "10.2.0"
    `maven-publish`
    antlr
    jacoco
}

group = "cc.ekblad"
version = "0.3.0-SNAPSHOT"
val kotlinJvmTarget = "1.8"

repositories {
    mavenCentral()
}

val dokkaHtml by tasks.getting(org.jetbrains.dokka.gradle.DokkaTask::class)

val javadocJar: TaskProvider<Jar> by tasks.registering(Jar::class) {
    dependsOn(dokkaHtml)
    archiveClassifier.set("javadoc")
    from(dokkaHtml.outputDirectory)
}

publishing {
    publications {
        create<MavenPublication>("4koma") {
            groupId = "cc.ekblad"
            artifactId = "4koma"
            version = project.version.toString()
            from(components["kotlin"])
            artifact(javadocJar)
            artifact(tasks.kotlinSourcesJar)
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
    val kotlinVersion = "1.6.0"
    implementation("org.jetbrains.kotlin:kotlin-stdlib:$kotlinVersion")
    implementation("org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    antlr("org.antlr:antlr4:4.9.3")

    testImplementation("org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
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

    listOf(compileJava, compileTestJava).map { task ->
        task {
            sourceCompatibility = kotlinJvmTarget
            targetCompatibility = kotlinJvmTarget
        }
    }

    listOf(compileKotlin, compileTestKotlin).map { task ->
        val generateGrammarSourceInfix = if (task === compileTestKotlin) "Test" else ""
        task {
            dependsOn("generate${generateGrammarSourceInfix}GrammarSource")
            kotlinOptions {
                jvmTarget = kotlinJvmTarget
                freeCompilerArgs = listOf("-Xopt-in=kotlin.RequiresOptIn")
            }
        }
    }

    kotlinSourcesJar {
        dependsOn("generateGrammarSource")
    }

    test {
        useJUnitPlatform()
        finalizedBy(jacocoTestReport)
    }

    build {
        dependsOn("javadocJar")
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
        classDirectories.setFrom(
            files(
                classDirectories.files.filter { !it.path.contains("build/classes/java") }.map {
                    fileTree(it).exclude {
                        it.name.contains("special\$\$inlined")
                    }
                }
            )
        )
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
