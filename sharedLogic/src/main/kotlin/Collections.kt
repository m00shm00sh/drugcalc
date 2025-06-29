package com.moshy.drugcalc.common

import com.moshy.containers.assertIsSortedSet

/** Get the submap whose key set is the intersection of the receiver's keys and the [keys] parameter.
 */
operator fun <K, V> Map<K, V>.get(keys: Collection<K>): Map<K, V> =
    buildMap {
        for (k in keys) {
            if (k in this)
                continue
            val v = this@get[k]
            v?.let { put(k, v) }
        }
    }

/** A singleton set, enriched with the sorted set property. */
fun <T: Comparable<T>> oneOf(value: T) =
    listOf(value).assertIsSortedSet()

inline fun <T> List<T>.checkValues(predicate: (T) -> Boolean, message: (Int, T) -> String) {
    for ((i, v) in withIndex()) {
        require(predicate(v)) {
            message(i, v)
        }
    }

}

