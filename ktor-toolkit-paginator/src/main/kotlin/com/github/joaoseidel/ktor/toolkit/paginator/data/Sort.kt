package com.github.joaoseidel.ktor.toolkit.paginator.data

import kotlinx.serialization.Serializable
import kotlin.reflect.KProperty1

/**
 * Represents a sorting configuration for a specific property in a dataset.
 *
 * The [Sort] class is used to define sorting criteria for data, specifying the
 * target property and the direction of sorting (ascending or descending).
 *
 * @property property The property to be used as the sorting key.
 * @property direction The sorting direction, either ascending ([Direction.ASC]) or descending ([Direction.DESC]).
 */
@Serializable
data class Sort(
    val property: String,
    val direction: Direction,
) {
    /**
     * Sorts by a property reference, so a rename cannot leave a stale sort key behind.
     *
     * @param property The property to sort by. Its name becomes the sort key.
     * @param direction The sorting direction. Defaults to [Direction.ASC].
     */
    constructor(
        property: KProperty1<*, *>,
        direction: Direction = Direction.ASC,
    ) : this(property.name, direction)

    /**
     * Defines the direction of sorting, either ascending or descending.
     *
     * The [Direction] enum is used to specify the order in which data should be sorted.
     * It offers two values:
     * - [ASC]: Represents ascending order.
     * - [DESC]: Represents descending order.
     *
     * @see Sort
     */
    @Serializable
    enum class Direction {
        ASC,
        DESC,
        ;

        companion object {
            /**
             * Reads the direction off a sort token such as `createdAt` or `-createdAt`.
             *
             * @param token A sort token. A leading `-` means descending; anything else is ascending.
             * @return [DESC] if [token] starts with `-`, otherwise [ASC].
             */
            fun fromString(token: String): Direction = if (token.startsWith("-")) DESC else ASC
        }
    }

    companion object {
        /**
         * Creates a [Sort] instance from a string representation.
         *
         * The input string specifies a property name and optionally includes
         * a prefix (`-`) to indicate sorting order. If the string starts with
         * `-`, the sorting direction is descending; otherwise, it is ascending.
         *
         * @param token The sort token, e.g. `createdAt` or `-createdAt`.
         * @return A [Sort] object with the specified property and sorting direction.
         */
        fun fromString(token: String): Sort = Sort(token.removePrefix("-"), Direction.fromString(token))
    }
}

/**
 * Builds a list of sorting criteria, in order of precedence.
 *
 * Property references keep the keys in step with the model:
 *
 * ```kotlin
 * val ordering = sortBy {
 *     desc(Book::publishedAt)
 *     asc(Book::title)
 * }
 * ```
 *
 * @param block Declares the criteria, most significant first.
 */
fun sortBy(block: SortBuilder.() -> Unit): List<Sort> = SortBuilder().apply(block).build()

/** Collects the criteria of a [sortBy] block. */
@PaginationDsl
class SortBuilder internal constructor() {
    private val criteria = mutableListOf<Sort>()

    /** Sorts by [property], ascending. */
    fun asc(property: KProperty1<*, *>) {
        criteria += Sort(property, Sort.Direction.ASC)
    }

    /** Sorts by the property named [property], ascending. */
    fun asc(property: String) {
        criteria += Sort(property, Sort.Direction.ASC)
    }

    /** Sorts by [property], descending. */
    fun desc(property: KProperty1<*, *>) {
        criteria += Sort(property, Sort.Direction.DESC)
    }

    /** Sorts by the property named [property], descending. */
    fun desc(property: String) {
        criteria += Sort(property, Sort.Direction.DESC)
    }

    internal fun build(): List<Sort> = criteria.toList()
}
