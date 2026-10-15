package com.github.joaoseidel.ktor.toolkit.statemachine.runtime

internal class Guard<in C>(
    val reason: String,
    val test: suspend (C) -> Boolean,
)
