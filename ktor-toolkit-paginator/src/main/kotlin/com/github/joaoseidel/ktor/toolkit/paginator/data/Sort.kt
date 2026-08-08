package com.github.joaoseidel.ktor.toolkit.paginator.data

import kotlinx.serialization.Serializable
import kotlin.reflect.KProperty1

/**
 * One `ORDER BY` term: what to sort on, and which way.
 *
 * A sort is carried as a property *name*, so it survives the trip from a query string to a
 * repository without either end depending on the other's types. That also means the name is
 * unchecked until it reaches a data source: `toExposedQueryExpression` and `toMongoSortExpression`
 * resolve it against an allow-list and reject anything else, which is what stops a client-supplied
 * `?sortBy=` naming a column the endpoint never meant to expose.
 *
 * @property property The name of the property to sort on.
 * @property direction Which way to sort.
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

    /** Which way a [Sort] orders its property. */
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
             */
            fun fromString(token: String): Direction = if (token.startsWith("-")) DESC else ASC
        }
    }

    companion object {
        /**
         * Reads a sort token, as it arrives in `?sortBy=`.
         *
         * The token is taken as the property name whatever it says — a name no data source knows
         * is rejected later, when the sort is resolved against an allow-list, not here.
         *
         * @param token A sort token. A leading `-` means descending; anything else is ascending.
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
