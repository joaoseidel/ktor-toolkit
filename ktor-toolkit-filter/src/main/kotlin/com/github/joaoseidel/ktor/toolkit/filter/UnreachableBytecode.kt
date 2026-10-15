package com.github.joaoseidel.ktor.toolkit.filter

/**
 * Marks a declaration whose bytecode a test cannot execute, so the coverage gate leaves it out.
 *
 * This is for code that is unreachable *by construction*, never for code that is merely untested. The members carrying
 * it are the reified `field` declarations of `filterableFields { }`: the compiler emits a non-inlined copy of a public
 * inline function for the declaration itself, and every caller inlines the body instead, so that copy can never run.
 *
 * The `report` module names this annotation in its Kover filter.
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
internal annotation class UnreachableBytecode
