package com.moshy.drugcalc.cmdclient.helpers

import com.moshy.containers.*
import com.moshy.drugcalc.common.*

internal suspend fun <V> OneToManyUnitLoadingCache<V>.get() = get(Unit)
internal suspend fun <V> OneToManyUnitLoadingCache<V>.updateIfModified(modify: MutableSet<V>.() -> Boolean): Boolean {
    var modified = false
    getIfPresent(Unit)?.run {
        buildCopy {
            modified = modify()
        }.let {
            if (modified)
                put(Unit, it)
        }
    }
    return modified
}
internal suspend fun <K : Comparable<K>, V>
        LoadingCache<K, ListAsSortedSet<V>>.updateIfModified(key: K, modify: MutableSet<V>.() -> Boolean): Boolean {
    var modified = false
    getIfPresent(key)?.run {
        buildCopy {
            modified = modify()
        }.let {
            if (modified)
                put(key, it)
        }
    }
    return modified
}
