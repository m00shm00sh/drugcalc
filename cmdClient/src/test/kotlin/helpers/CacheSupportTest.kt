package com.moshy.drugcalc.cmdclient.helpers

import com.moshy.containers.ListAsSortedSet
import com.moshy.containers.assertIsSortedSet
import com.moshy.drugcalc.common.CacheEvictionPolicy
import com.moshy.drugcalc.common.newLoadingCache
import com.moshy.drugcalc.common.newOneToManyUnitLoadingCache
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

internal class CacheSupportTest {
    @Test
    fun `unit cache updateIfModified puts a modified copy`() = runTest {
        val cache = newOneToManyUnitLoadingCache<String>(CacheEvictionPolicy()) {
            emptyList<String>().assertIsSortedSet()
        }
        cache.get()

        val modified = cache.updateIfModified { add("a") }

        assertTrue(modified)
        assertEquals(setOf("a"), cache.getIfPresent(Unit))
    }

    @Test
    fun `unit cache updateIfModified leaves an unmodified cache alone`() = runTest {
        val cache = newOneToManyUnitLoadingCache<String>(CacheEvictionPolicy()) {
            emptyList<String>().assertIsSortedSet()
        }
        cache.get()
        val original = cache.getIfPresent(Unit)

        val modified = cache.updateIfModified { false }

        assertFalse(modified)
        assertSame(original, cache.getIfPresent(Unit))
    }

    @Test
    fun `keyed cache updateIfModified puts a modified copy`() = runTest {
        val cache = newLoadingCache<String, ListAsSortedSet<Int>>(CacheEvictionPolicy()) {
            emptyList<Int>().assertIsSortedSet()
        }
        cache.get("k")

        val modified = cache.updateIfModified("k") { add(1) }

        assertTrue(modified)
        assertEquals(setOf(1), cache.getIfPresent("k"))
    }

    @Test
    fun `keyed cache updateIfModified ignores absent keys`() = runTest {
        val cache = newLoadingCache<String, ListAsSortedSet<Int>>(CacheEvictionPolicy()) {
            emptyList<Int>().assertIsSortedSet()
        }

        val modified = cache.updateIfModified("missing") { add(1) }

        assertFalse(modified)
        assertNull(cache.getIfPresent("missing"))
    }
}
