package com.moshy.drugcalc.calc.datacontroller

import com.moshy.containers.*
import kotlin.math.*

/** Specify a pagination clause with a limit of [seekAfter] and seek value of [seek] subject to [direction].
 *
 * 1. When [direction] is [Direction.AFTER], [seekAfter] is the last element of the previous page.
 * 2. When [direction] is [Direction.BEFORE], [seekAfter] is the first element of the next page.
 * */
data class PaginationSpecifier<T>(
    val limit: Int = 20,
    val seekAfter: T? = null,
    val direction: Direction? = null
) {
    init {
        require((direction == null) || (limit > 0)) {
            "count must be positive"
        }
        require(!((direction != null) xor (seekAfter != null))) {
            "non-null direction must be tied with non-null limit"
        }
    }

    enum class Direction {
        AFTER,
        BEFORE,
    }
}

/** Returns a paginated slice view of a sorted list.
 *
 * @see PaginationSpecifier
 */
internal fun <E : Comparable<E>> ListAsSortedSet<E>.paginateList(
    paginationSpecifier: PaginationSpecifier<E>
): ListAsSortedSet<E> {
    fun sizeMin(i: Int) = min(i, size)
    fun sizeMax(i: Int) = max(i, 0)
    val l = list
    return when (paginationSpecifier.direction) {
        null ->
            // no sense in limit if direction doesn't exist
            l
        PaginationSpecifier.Direction.AFTER -> {
            val i = paginationSpecifier.seekAfter?.let { l.binarySearch(paginationSpecifier.seekAfter) } ?: -1
            if (i >= 0)
                l.subList(sizeMin(i + 1), sizeMin(i + 1 + paginationSpecifier.limit))
            else {
                val insertionPoint = -(i + 1)
                l.subList(sizeMin(insertionPoint), sizeMin(insertionPoint + paginationSpecifier.limit))
            }
        }

        PaginationSpecifier.Direction.BEFORE -> {
            val i = paginationSpecifier.seekAfter?.let { l.binarySearch(paginationSpecifier.seekAfter) } ?: -1
            if (i >= 0)
                l.subList(sizeMax(i - paginationSpecifier.limit), i)
            else {
                val insertionPoint = -(i + 1)
                l.subList(sizeMax(insertionPoint - paginationSpecifier.limit), insertionPoint)
            }
        }
    }.assertIsSortedSet()
}
