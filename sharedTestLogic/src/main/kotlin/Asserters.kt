package com.moshy.drugcalc.commontest

import com.moshy.containers.ListAsSortedSet
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.jupiter.api.AssertionFailureBuilder.assertionFailure
import org.junit.jupiter.api.Assertions
import org.opentest4j.MultipleFailuresError
import kotlin.reflect.KClass

/* Junit5-style assertContains.
 * NOTE: Junit uses (expected, actual), which is the *reverse* of fluent-style arguments of
 *       assertMatches(actual, expected) (or even builder pattern of assertThat(actual).matches(expected)).
 *       We preserve these semantics for compatibility, even for logical nonsense like assertGreater/assertLess.
 * NOTE 2: It's a shame we can't do typeclass generics
 */
fun assertContains(expected: String, actual: String?, message: String? = null) {
    if (actual?.contains(expected) != true)
        fail(expected, actual, message)
}
fun assertContains(expected: Regex, actual: String?, message: String? = null) {
    if (actual?.contains(expected) != true)
        fail("Regex($expected)", actual, message)
}
fun <T> assertContains(collection: Collection<T>, value: T?, message: String? = null) {
    if (value !in collection)
        fail(
            object { override fun toString() = "in $collection" },
            value,
            message
        )
}
fun <K> assertContains(map: Map<K, *>, key: K, message: String? = null) =
    assertContains(map.keys, key, message)
fun <T> assertDoesNotContain(collection: Collection<T>, value: T?, message: String? = null) {
    if (value in collection)
        fail(
            object { override fun toString() = "not in $collection" },
            value, message
        )
}

fun <K> assertDoesNotContain(map: Map<K, *>, key: K, message: String? = null) =
    assertDoesNotContain(map.keys, key, message)

// Set makes no assumptions on ordering; use set difference to figure out inequality
fun <T> assertSetsAreEqual(expected: Set<T>, actual: Set<T>, message: String? = null) {
    val missing = expected - actual
    val extra = actual - expected
    if (missing.isNotEmpty() || extra.isNotEmpty()) {
        fail(
            expected,
            object { override fun toString() = "missing: $missing, extra: $extra" },
            message
        )
    }
}
fun <K, V> assertMapsAreEqual(expected: Map<K, V>, actual: Map<K, V>?, message: String? = null) {
    val denulledActual = actual ?: emptyMap()
    val missing = expected - denulledActual.keys
    val extra = denulledActual - expected.keys
    val commonKeys = expected.keys.intersect(denulledActual.keys)
    val unequal = buildMap(commonKeys.size) {
        for (k in commonKeys) {
            val v = denulledActual[k]
            if (expected[k] != v)
                put(k, v)
        }
    }
    if (missing.isNotEmpty() || extra.isNotEmpty() || unequal.isNotEmpty())
        fail(
            expected,
            object { override fun toString() = "missing: $missing, extra: $extra, unequal: $unequal" },
            message
        )
}
fun <T> assertSubset(expectedSuperset: Set<T>, actualSubset: Set<T>?, message: String? = null) {
    val denulledActual = actualSubset ?: emptySet()
    val extra = denulledActual - expectedSuperset
    if (extra.isNotEmpty()) {
        fail(
            expectedSuperset,
            object { override fun toString() = "extra: $extra" },
            message
        )
    }
}
fun <T> assertSuperset(expectedSubset: Set<T>, actualSuperset: Set<T>?, message: String? = null) {
    val denulledActual = actualSuperset ?: emptySet()
    val missing = expectedSubset - denulledActual
    if (missing.isNotEmpty()) {
        fail(
            expectedSubset,
            object { override fun toString() = "missing: $missing" },
            message
        )
    }
}

fun <T: Comparable<T>> assertGreater(greaterThan: T, actual: T?, message: String? = null) {
    if  (actual == null || actual <= greaterThan)
        fail(
            object { override fun toString() = "> $greaterThan" },
            actual, message
        )
}
fun <T: Comparable<T>> assertLess(lessThan: T, actual: T?, message: String? = null) {
    if  (actual == null || actual >= lessThan)
        fail(
            object { override fun toString() = "< $lessThan" },
            actual, message
        )
}

fun <T> assertEquals(expected: ListAsSortedSet<T>, actual: Collection<T>, message: String? = null) {
    Assertions.assertEquals(expected, actual, message)
}

private fun fail(expected: Any?, actual: Any?, message: String?): Nothing =
    /* Kotlin can't deduce that return type of AssertionFailureBuilder.buildAndThrow() is `Nothing` so
     * throw the exception ourselves.
     */
    throw
        assertionFailure()
        .apply { if (message != null) message(message) }
        .expected(expected)
        .actual(actual)
        .build()


/* JUnit5 api.AssertAll reimplementation to handle coroutines */
suspend fun assertAll(vararg executables: suspend () -> Unit) =
    assertAll(null, executables.toList())
suspend fun assertAll(executables: Collection<suspend () -> Unit>) =
    assertAll(null, executables)
suspend fun assertAll(message: String?, vararg executables: suspend () -> Unit) =
    assertAll(message, executables.toList())

suspend fun assertAll(message: String?, executables: Collection<suspend () -> Unit>) {
    lateinit var tasks: List<Job>
    // use a ChannelFlow so we can evaluate each executable concurrently (vs serial Flow and its serial emit())
    val failuresFlow = channelFlow {
        tasks =
            executables.map {
                launch {
                    try {
                        it()
                    } catch (t: Throwable) {
                        send(t)
                }
            }
        }
    }

    val failed = failuresFlow.toList()
    tasks.joinAll()

    if (failed.isNotEmpty()) {
        val multipleFailures = MultipleFailuresError(message, failed)
        for (f in failed)
            multipleFailures.addSuppressed(f)
        throw multipleFailures
    }
}

/* Junit5 AssertionsKt `inline fun <reified T: Throwable> assertThrows` doesn't help us because we already have
 * the exception type, so add an overload for assertThrows ourselves.
 */
suspend fun assertThrows(
    exceptionType: KClass<out Throwable>,
    message: String = "",
    executable: suspend () -> Unit
): Throwable {
    val throwable: Throwable? = try {
        executable()
    } catch (caught: Throwable) {
        caught
    } as? Throwable

    fun thrower() {
        if (throwable != null)
            throw throwable
    }
    return Assertions.assertThrows(exceptionType.java, ::thrower, message)
}
