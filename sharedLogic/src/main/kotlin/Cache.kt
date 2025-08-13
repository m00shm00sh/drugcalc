package com.moshy.drugcalc.common

import com.moshy.containers.ListAsSortedSet
import com.github.benmanes.caffeine.cache.Caffeine
import com.sksamuel.aedile.core.*
import com.sksamuel.aedile.core.LoadingCache as AeLoadingCache
import kotlin.time.Duration

fun <K, V> newCache(policy: CacheEvictionPolicy) =
    Caffeine
        .newBuilder()
        .apply { policy.accessTime?.let(::expireAfterAccess) }
        .apply { policy.writeTime?.let(::expireAfterWrite) }
        .asCache<K, V>()

typealias LoadingCache<K, V> = AeLoadingCache<K, V>

fun <K, V> newLoadingCache(
    policy: CacheEvictionPolicy,
    compute: suspend (K) -> V
): LoadingCache<K, V> =
    Caffeine
        .newBuilder()
        .apply { policy.accessTime?.let(::expireAfterAccess) }
        .apply { policy.writeTime?.let(::expireAfterWrite) }
        .asLoadingCache(compute)

typealias OneToManyUnitLoadingCache<V> = LoadingCache<Unit, ListAsSortedSet<V>>

fun <V : Comparable<V>> newOneToManyUnitLoadingCache(
    policy: CacheEvictionPolicy,
    compute: suspend (Unit) -> ListAsSortedSet<V>
): OneToManyUnitLoadingCache<V> =
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

