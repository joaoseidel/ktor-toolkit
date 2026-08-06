package com.luizalabs.ktor.toolkit.validator

import com.luizalabs.ktor.toolkit.validator.data.ValidationError
import io.ktor.server.plugins.requestvalidation.ValidationResult
import kotlin.reflect.KProperty1

/**
 * The root of the validation DSL: the object under validation, and the errors found on it.
 *
 * Rules are attached to properties by reference, so a rename is a compile error rather than a
 * silently dead rule:
 *
 * ```kotlin
 * ValidationContext(request).apply {
 *     property(CreateBookRequest::title) {
 *         should notBe blank()
 *         should be size(min = 3, max = 200)
 *     }
 *     nested(CreateBookRequest::publisher) {
 *         property(Publisher::name) { should notBe blank() }
 *     }
 *     invariant("should not end before it starts") { it.endsAt > it.startsAt }
 * }
 * ```
 *
 * Nested contexts share one error list and prefix their paths, so an error found under
 * `publisher.name` or `authors[0].email` reports the full path back to the caller.
 *
 * @param T The type of the object being validated.
 * @property target The object being validated. Exposed so a rule can depend on a sibling field or
 * on the object as a whole, as in `whenever(target.isPublished) { … }`.
 */
@ValidationDsl
class ValidationContext<T> internal constructor(
    val target: T,
    private val basePath: String,
    private val collected: MutableList<ValidationError>,
) {
    /**
     * Creates a root context for [target].
     *
     * @param target The object being validated.
     */
    constructor(target: T) : this(target, basePath = "", collected = mutableListOf())

    /** Every validation error collected so far, in the order it was found. */
    val errors: List<ValidationError> get() = collected

    /** Whether at least one validation error has been recorded. */
    val hasErrors: Boolean get() = collected.isNotEmpty()

    /**
     * Validates a single property of [target].
     *
     * @param prop The property to validate.
     * @param block The rules to assert on it.
     * @return The [PropertyValidator] the rules were asserted on.
     */
    fun <V> property(
        prop: KProperty1<T, V>,
        block: PropertyValidator<T, V>.() -> Unit,
    ): PropertyValidator<T, V> = PropertyValidator(target, pathOf(prop.name), prop.get(target), collected).apply(block)

    /**
     * Validates the object held by a nested property, recursively.
     *
     * A null nested value is an error in itself — there is nothing to descend into — so it is
     * reported as [nullMessage] and [block] is skipped.
     *
     * @param R The type of the nested object.
     * @param prop The nested property to descend into.
     * @param nullMessage The error recorded when the nested property is absent.
     * @param block The rules to assert on the nested object.
     */
    fun <R : Any> nested(
        prop: KProperty1<T, R?>,
        nullMessage: String = "should not be null",
        block: ValidationContext<R>.() -> Unit,
    ) {
        val path = pathOf(prop.name)
        val value = prop.get(target)

        if (value == null) {
            collected += ValidationError(path, nullMessage)
            return
        }

        ValidationContext(value, path, collected).apply(block)
    }

    /**
     * Validates every element of a collection property, as values.
     *
     * Errors are reported per element, at `tags[0]`, `tags[1]` and so on. A null collection is
     * skipped — require it separately with `property(Book::tags) { should notBe nil() }`.
     *
     * ```kotlin
     * each(CreateBookRequest::tags) { should notBe blank() }
     * ```
     *
     * @param E The element type.
     * @param prop The collection property whose elements to validate.
     * @param block The rules to assert on each element.
     */
    fun <E> each(
        prop: KProperty1<T, Collection<E>?>,
        block: PropertyValidator<T, E>.() -> Unit,
    ) {
        val path = pathOf(prop.name)
        prop.get(target)?.forEachIndexed { index, element ->
            PropertyValidator(target, "$path[$index]", element, collected).apply(block)
        }
    }

    /**
     * Validates every element of a collection property, as objects.
     *
     * The counterpart of [each] for elements with properties of their own: errors are reported at
     * `authors[0].email`. A null collection is skipped.
     *
     * ```kotlin
     * eachNested(CreateBookRequest::authors) {
     *     property(Author::email) { should be email() }
     * }
     * ```
     *
     * @param E The element type.
     * @param prop The collection property whose elements to validate.
     * @param block The rules to assert on each element.
     */
    fun <E : Any> eachNested(
        prop: KProperty1<T, Collection<E>?>,
        block: ValidationContext<E>.() -> Unit,
    ) {
        val path = pathOf(prop.name)
        prop.get(target)?.forEachIndexed { index, element ->
            ValidationContext(element, "$path[$index]", collected).apply(block)
        }
    }

    /**
     * Applies [block] only when [condition] holds.
     *
     * The condition is an ordinary expression over [target], so a whole group of rules can be made
     * to depend on the shape of the request:
     *
     * ```kotlin
     * whenever(target.kind == PHYSICAL) {
     *     property(CreateBookRequest::weightGrams) { should notBe nil() }
     * }
     * ```
     *
     * @param condition Whether the rules in [block] apply at all.
     * @param block The rules to assert when [condition] holds.
     */
    fun whenever(
        condition: Boolean,
        block: ValidationContext<T>.() -> Unit,
    ) {
        if (condition) block()
    }

    /**
     * Asserts a condition over the object as a whole, for rules that no single property owns.
     *
     * ```kotlin
     * invariant("should not end before it starts") { it.endsAt > it.startsAt }
     * ```
     *
     * @param message The error recorded when [predicate] does not hold.
     * @param path The property path to record the error under. Empty by default, which reports the
     * error against the object itself.
     * @param predicate The condition to check, applied to [target].
     */
    fun invariant(
        message: String,
        path: String = "",
        predicate: (T) -> Boolean,
    ) {
        if (!predicate(target)) collected += ValidationError(pathOf(path), message)
    }

    /**
     * Converts the collected errors into the result Ktor's `RequestValidation` plugin expects.
     *
     * @return [ValidationResult.Valid] when nothing was found, otherwise a
     * [ValidationResult.Invalid] listing every error.
     */
    fun toValidationResult(): ValidationResult {
        val found = collected.ifEmpty { return ValidationResult.Valid }
        return ValidationResult.Invalid(reasons = found.map(ValidationError::toString))
    }

    /** Qualifies [name] with the path of the enclosing context, if there is one. */
    private fun pathOf(name: String): String =
        when {
            name.isEmpty() -> basePath
            basePath.isEmpty() -> name
            else -> "$basePath.$name"
        }
}
