package cc.ekblad.toml.util

import kotlin.reflect.KClass
import kotlin.reflect.KType
import kotlin.reflect.KTypeParameter
import kotlin.reflect.KTypeProjection
import kotlin.reflect.full.createType

private fun KTypeProjection.subst(substitutions: Map<KTypeParameter, KType>) = KTypeProjection(
    variance = variance,
    type = type?.subst(substitutions)
)

/**
 * Substitute any type parameters for concrete types, assuming that the parameters in question exist in the given
 * substitution.
 */
internal fun KType.subst(substitutions: Map<KTypeParameter, KType>): KType =
    when (val kClassifier = classifier) {
        is KClass<*> -> kClassifier.createType(
            arguments = arguments.map { it.subst(substitutions) },
            nullable = isMarkedNullable,
            annotations = annotations
        )
        else -> substitutions[kClassifier] ?: this
    }
