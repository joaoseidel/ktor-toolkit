package com.github.joaoseidel.ktor.toolkit.cache.runtime

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
internal annotation class UnreachableBytecode
