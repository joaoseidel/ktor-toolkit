package com.github.joaoseidel.ktor.toolkit.validator.support

import com.github.joaoseidel.ktor.toolkit.validator.api.PropertyValidator
import com.github.joaoseidel.ktor.toolkit.validator.api.ValidationContext
import com.github.joaoseidel.ktor.toolkit.validator.api.data.ValidationError

data class Holder<V>(
    val value: V,
)

fun <V> errorsOf(
    value: V,
    block: PropertyValidator<Holder<V>, V>.() -> Unit,
): List<ValidationError> =
    ValidationContext(Holder(value))
        .apply { property(Holder<V>::value, block) }
        .errors

fun <V> messagesOf(
    value: V,
    block: PropertyValidator<Holder<V>, V>.() -> Unit,
): List<String> = errorsOf(value, block).map { it.message }
