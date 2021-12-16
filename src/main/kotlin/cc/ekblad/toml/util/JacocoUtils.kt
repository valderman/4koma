// ktlint-disable filename
package cc.ekblad.toml.util

/**
 * Tells JaCoCo to disregard the annotated item when reporting code coverage.
 * Needed to avoid false negative coverage scares as JaCoCo doesn't understand Kotlin inline functions.
 */
internal annotation class Generated
