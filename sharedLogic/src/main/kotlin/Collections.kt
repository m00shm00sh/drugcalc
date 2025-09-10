package com.moshy.drugcalc.common

import com.moshy.containers.assertIsSortedSet
import kotlin.reflect.KProperty

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

/** StringBuilder wrapper that conditionally appends only when container is non-null and non-empty.
 *
 * Use case:
 * ```
 *  data class C(
 *      val a: List<X> = emptyList()),
 *      val b: List<Y> = emptyList())
 *  ) {
 *      override fun toString() = buildString {
 *          val b = conditional()
 *          append("C(")
 *          b.appendIfNotNullOrEmpty(::a, a)
 *          b.appendIfNotNullOrEmpty(::b, b)
 *          append(")")
 *      }
 *  }
 *  // =>
 *  C() => "C()"
 *  C(a=listOf(x)) => "C(a=[x])"
 *  C(b=listOf(y)) => C(b=[y])"
 *  C(a=listOf(x), b=listOf(y)) => "C(a=[x], b=[y])"
 *  ```
 * */
class ConditionalStringBuilder(
    private val b: StringBuilder,
    private val kv: String = "=",
    private val sep: String = ", "
): Appendable by b {
    private var did1 = false

    fun <E> appendIfNotNullOrEmpty(prop: KProperty<Collection<E>?>, obj: Collection<E>?): ConditionalStringBuilder {
        if (obj.isNullOrEmpty())
            return this
        if (did1)
            append(sep)
        append(prop.name)
        append(kv)
        append(obj.toString())
        did1 = true
        return this
    }

    fun <K, V> appendIfNotNullOrEmpty(prop: KProperty<Map<K, V>?>, obj: Map<K, V>?): ConditionalStringBuilder {
        if (obj.isNullOrEmpty())
            return this
        if (did1)
            append(sep)
        append(prop.name)
        append(kv)
        append(obj.toString())
        did1 = true
        return this
    }
}

fun StringBuilder.conditional() = ConditionalStringBuilder(this)
