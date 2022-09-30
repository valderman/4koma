import java.nio.file.Paths
import kotlin.collections.listOf
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.inputStream

plugins {
    kotlin("jvm") version "1.7.20-RC"
    id("org.jetbrains.dokka") version "1.7.10"
    id("org.jlleitschuh.gradle.ktlint") version "11.0.0"
    id("com.github.ben-manes.versions") version "0.42.0"
    `maven-publish`
    jacoco
}

group = "cc.ekblad"
version = "1.1.0"
val kotlinJvmTarget = "11"

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
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
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("cc.ekblad.konbini", "konbini", "0.1.3")
    testImplementation(kotlin("test"))
}

ktlint {
    version.set("0.45.2")
}

data class DependencyVersion(val module: String, val version: String)

val excludedVersions: Set<Pair<String, String>> = setOf(
    // Ktlint >=0.46 doesn't work with the ktlint plugin.
    "ktlint" to "0.46.0",
    "ktlint" to "0.46.1",
    "ktlint" to "0.47.0",
    "ktlint" to "0.47.1",
)

tasks {
    dependencyUpdates {
        rejectVersionIf {
            (candidate.module to candidate.version) in excludedVersions
        }
    }

    val dependencyUpdateSentinel = register<DependencyUpdateSentinel>("dependencyUpdateSentinel") {
        dependsOn(dependencyUpdates)
    }

    listOf(compileJava, compileTestJava).map { task ->
        task {
            sourceCompatibility = kotlinJvmTarget
            targetCompatibility = kotlinJvmTarget
        }
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
        dependsOn(dependencyUpdateSentinel)
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

abstract class DependencyUpdateSentinel : DefaultTask() {
    @ExperimentalPathApi
    @org.gradle.api.tasks.TaskAction
    fun check() {
        val updateIndicator = "The following dependencies have later milestone versions:"
        Paths.get("build", "dependencyUpdates", "report.txt").inputStream().bufferedReader().use { reader ->
            if (reader.lines().anyMatch { it == updateIndicator }) {
                throw GradleException("Dependency updates are available.")
            }
        }
    }
}
