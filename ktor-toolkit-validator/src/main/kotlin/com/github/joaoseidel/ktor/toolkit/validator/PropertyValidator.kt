package com.github.joaoseidel.ktor.toolkit.validator

import com.github.joaoseidel.ktor.toolkit.validator.data.ValidationError

/**
 * The receiver of every validation rule: one value, at one path, within the object being validated.
 *
 * Instances are created by [ValidationContext.property] and [ValidationContext.each]; the type
 * itself is public because rules are declared as extensions on it. A rule states the property types
 * it accepts through its receiver — `fun PropertyValidator<*, String?>.email()` — and [V] is
 * covariant, so `PropertyValidator<Book, String>` matches that receiver while
 * `PropertyValidator<Book, Int>` does not. Asserting a rule on a property it cannot apply to is
 * therefore an unresolved reference rather than a runtime type error.
 *
 * @param T The type of the object that owns the value.
 * @param V The type of the value being validated.
 * @property target The object that owns the value. Exposed so a rule can be made conditional on a
 * sibling field, as in `should be after(target.startsAt)`.
 * @property path The property path errors are recorded under, such as `title`, `publisher.name` or
 * `tags[0]`.
 * @property value The value being validated.
 */
@ValidationDsl
class PropertyValidator<T, out V> internal constructor(
    val target: T,
    val path: String,
    val value: V,
    private val collected: MutableList<ValidationError>,
) {
    /** The errors recorded so far, shared with the owning [ValidationContext]. */
    val errors: List<ValidationError> get() = collected

    /**
     * The entry point for validation rules, letting them read as `should be email()` or
     * `should notBe nil()`. Any violation is recorded on this validator.
     */
    val should: ShouldScope<T, V> = ShouldScope(this)

    /** Records [message] against this property and returns the index it landed at. */
    internal fun addError(message: String): Int {
        collected += ValidationError(path, message)
        return collected.lastIndex
    }

    /** Rewords an already recorded error, backing `describedAs` on a [RuleOutcome]. */
    internal fun replaceError(
        index: Int,
        message: String,
    ) {
        collected[index] = collected[index].copy(message = message)
    }
}
