package com.moshy.drugcalc.calc.datacontroller

import com.moshy.containers.ListAsSortedSet
import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.*
import com.sksamuel.aedile.core.LoadingCache as AeLoadingCache
import kotlin.time.Duration

internal fun <K, V> newCache(policy: CacheEvictionPolicy) =
    Caffeine
        .newBuilder()
        .apply { policy.accessTime?.let(::expireAfterAccess) }
        .apply { policy.writeTime?.let(::expireAfterWrite) }
        .asCache<K, V>()

internal typealias LoadingCache<K, V> = AeLoadingCache<K, ListAsSortedSet<V>>

internal fun <K, V : Comparable<V>> newLoadingCache(
    policy: CacheEvictionPolicy,
    compute: suspend (K) -> ListAsSortedSet<V>
): LoadingCache<K, V> =
    Caffeine
        .newBuilder()
        .apply { policy.accessTime?.let(::expireAfterAccess) }
        .apply { policy.writeTime?.let(::expireAfterWrite) }
        .asLoadingCache(compute)

internal typealias UnitLoadingCache<V> = LoadingCache<Unit, V>

internal fun <V : Comparable<V>> newUnitLoadingCache(
    policy: CacheEvictionPolicy,
    compute: suspend (Unit) -> ListAsSortedSet<V>
): UnitLoadingCache<V> =
    Caffeine
        .newBuilder()
        .apply { maximumSize(1) }
        .apply { policy.accessTime?.let(::expireAfterAccess) }
        .apply { policy.writeTime?.let(::expireAfterWrite) }
        .asLoadingCache(compute)

data class CacheEvictionPolicy(
    val accessTime: Duration? = null,
    val writeTime: Duration? = null,
)

