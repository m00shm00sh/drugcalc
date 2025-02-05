package com.moshy.drugcalc.calcdata

import com.moshy.drugcalc.testutil.CheckArg
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.times
import kotlin.time.toDuration

internal class DataStoreTest {
    private companion object {
        val DAY = (1.0).toDuration(DurationUnit.DAYS)

        /* DataStore instances for test; initial states */
        val emptyDS = DataStore()
        val dsForAddCompounds = emptyDS.buildCopy()
        val dsForGetTransformers = emptyDS.buildAndFreezeCopy {
            addCompounds(mapOf(
                    "foo bar" to CompoundInfo(2 * DAY, 0.9, "foo"),
                    "foob a" to CompoundInfo(2 * DAY, 0.9, "foob a")
            ))
        }
        val dsForAddBlends = emptyDS.buildCopy().apply {
            addCompounds(mapOf(
                    "foo bar" to CompoundInfo(2 * DAY, 0.25, "foo"),
                    "foo baz" to CompoundInfo(DAY, 0.5, "foo"),
                    "bar baz" to CompoundInfo(0.5 * DAY, activeCompound = "bar")
            ))
        }
        val dsForAddFrequencies = emptyDS.buildCopy()
        val dsForRemoveCompounds = dsForAddBlends.buildCopy().apply {
            addBlends(mapOf(
                    "b" to mapOf(
                        "foo bar" to 1.0,
                        "bar baz" to 1.0,
                    )
            ))
        }
        val dsForRemoveBlends = dsForRemoveCompounds.buildCopy()
        val dsForRemoveFrequencies = emptyDS.buildCopy().apply {
            addFrequencies(mapOf(
                    "f" to listOf(DAY)
            ))
        }

        /* values of importance */
        val blendB =  mapOf(
            "foo bar" to 100.0,
            "foo baz" to 50.0,
            "bar baz" to 50.0
        )
        val blendBComponents = listOf(
            BlendComponent(0.25 * 0.5, CompoundInfo(2 * DAY, 0.25, "foo")),
            BlendComponent(0.5 * 0.25, CompoundInfo(DAY, 0.5, "foo")),
            BlendComponent(1.0 * 0.25, CompoundInfo(0.5 * DAY, 1.0, "bar"))
        )
        val emptyBlend = emptyMap<String, Double>()

        val emptyFreqVal = emptyList<Duration>()
        val oneDay = listOf(DAY)

        /* parameter sources */
        @JvmStatic
        fun testArgsForAddCompounds() =
            listOf(
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("foob ar" to CompoundInfo(2 * DAY)),
                    "compounds: \"foob ar\" has empty activeCompound"
                ),
                CheckArg.nothrow(mapOf("foo bar" to CompoundInfo(2 * DAY, 0.9, "foob")))
                    {
                        assertEquals(
                            "foob", dsForAddCompounds.compounds["foo bar"]?.activeCompound,
                        )
                    },
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("foo bar" to CompoundInfo(DAY)), "compounds: \"foo bar\" already exists"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("a:b" to CompoundInfo(DAY)), "compounds: forbidden character: ':'"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("" to CompoundInfo(DAY)), "compounds: empty key"
                )
            )
                .map { Arguments.of(it) }

        @JvmStatic
        fun testArgsForGetTransformer() =
            listOf(
                CheckArg.nothrow<TransformerResult, _>("FOO:median")
                    { assertNotNull(it, "transformer") },
                CheckArg.nothrow(":median")
                    { assertNull(it, "transformer:syntax") },
                CheckArg.nothrow("xxx:median")
                    { assertNull(it, "transformer:compound") },
                CheckArg.nothrow("xxx:fred")
                    { assertNull(it, "transformer:transformer") }
            )
                .map { Arguments.of(it) }

        @JvmStatic
        fun testArgsForAddBlends() =
            listOf(
                CheckArg.nothrow(mapOf("b" to blendB))
                    { assertAll(
                        { assertEquals(blendBComponents, dsForAddBlends.blends["b"]?.components) },
                        { assertEquals(blendB, dsForAddBlends.getBlends()["b"]) }
                    ) },
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("b" to emptyBlend),
                    "blends: \"b\" already exists"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("a:b" to emptyBlend),
                    "blends: forbidden character: ':'"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("" to emptyBlend),
                    "blends: empty key"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("c" to mapOf("foo bar" to 100.0)),
                    "blend \"c\": 2 or more components"),
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("d" to mapOf(
                        "foo bar" to 100.0,
                        "foo baz" to 0.0
                    )), "blend \"d\", component \"foo baz\": zero dose"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("d" to mapOf(
                        "fooz" to 100.0,
                        "foo baz" to 1.0
                    )), "blend \"d\": unrecognized component \"fooz\""
                )
            )
                .map { Arguments.of(it) }

        @JvmStatic
        fun testArgsForAddFrequencies() =
                listOf(
                    CheckArg.nothrow(mapOf("f" to oneDay))
                        { assertIterableEquals(dsForAddFrequencies.frequencies["f"], oneDay) },
                    CheckArg.throws<IllegalArgumentException, _>(
                        mapOf("f" to emptyFreqVal), "frequencies: \"f\" already exists"
                    ),
                    CheckArg.throws<IllegalArgumentException, _>(
                        mapOf("i" to emptyFreqVal), "frequency \"i\": empty list"
                    ),
                    CheckArg.throws<IllegalArgumentException, _>(
                        mapOf("i" to oneDay.map { it * 0 }), "frequency \"i\": value 0s@[0]"
                    )
                )
                    .map { Arguments.of(it) }

        @JvmStatic
        fun testArgsForRemoveCompounds() =
            listOf(
                CheckArg.nothrow(setOf("foo baz")),
                CheckArg.throws<IllegalArgumentException, _>(
                    setOf("b"), "missing compound \"b\""
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    setOf("bar baz"), "deleting compound \"bar baz\" would orphan blend \"b\""
                )
            )
                .map { Arguments.of(it) }

        @JvmStatic
        fun testArgsForRemoveBlends() =
            listOf(
                CheckArg.nothrow(setOf("b")),
                CheckArg.throws<IllegalArgumentException, _>(setOf("c"), "missing blend \"c\"")
            )
                .map { Arguments.of(it) }

        @JvmStatic
        fun testArgsForRemoveFrequencies() =
            listOf(
                CheckArg.nothrow(setOf("f")),
                CheckArg.throws<IllegalArgumentException, _>(setOf("g"), "missing frequency \"g\"")
            )
                .map { Arguments.of(it) }
    }

    @ParameterizedTest
    @MethodSource("testArgsForAddCompounds")
    fun testAddCompounds(a: CheckArg<Unit, CompoundsMap>) =
        a.invoke { dsForAddCompounds.addCompounds(this) }

    @ParameterizedTest
    @MethodSource("testArgsForGetTransformer")
    fun testGetTransformer(a: CheckArg<TransformerResult, String>) =
        a.invoke { dsForGetTransformers.getTransformer(this) }

    @ParameterizedTest
    @MethodSource("testArgsForAddBlends")
    fun testAddBlends(a: CheckArg<Unit, BlendsMap>) =
        a.invoke { dsForAddBlends.addBlends(this) }

    @ParameterizedTest
    @MethodSource("testArgsForAddFrequencies")
    fun testAddFrequencies(a: CheckArg<Unit, FrequenciesMap>) =
        a.invoke { dsForAddFrequencies.addFrequencies(this) }

    @ParameterizedTest
    @MethodSource("testArgsForRemoveCompounds")
    fun testRemoveCompounds(a: CheckArg<Unit, Set<String>>) =
        a.invoke { dsForRemoveCompounds.removeCompounds(this) }

    @ParameterizedTest
    @MethodSource("testArgsForRemoveBlends")
    fun testRemoveBlends(a: CheckArg<Unit, Set<String>>) =
        a.invoke { dsForRemoveBlends.removeBlends(this) }

    @ParameterizedTest
    @MethodSource("testArgsForRemoveFrequencies")
    fun testRemoveFrequencies(a: CheckArg<Unit, Set<String>>) =
        a.invoke { dsForRemoveFrequencies.removeFrequencies(this) }

    /* These test cases can't be parameterized because it's not a linear sequence of operations on just one object
     * without making an unnecessarily complex parameterized test function.
     */

    @Test
    fun testIntersectionForAdd() {
        val ds = dsForRemoveBlends.buildCopy()
        assertAll("intersection",
            { CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("b" to ds.compounds["foo bar"]!!),
                    "\"b\" already exists in blends"
                ).invoke {
                    ds.addCompounds(this)
                }
            },
            { CheckArg.throws<IllegalArgumentException, _>(
                    mapOf("foo bar" to ds.getBlends()["b"]!!),
                    "\"foo bar\" already exists in compounds"
                ).invoke {
                    ds.addBlends(this)
                }
            }
        )
    }
    @Test
    fun testCopy() {
        val ds = DataStore()
        ds.addCompounds(mapOf("foo bar" to CompoundInfo(DAY, activeCompound = "foo")))
        val ds2 = ds.buildAndFreezeCopy {
            assertDoesNotThrow {
                addCompounds(mapOf("foob" to CompoundInfo(DAY, activeCompound = "foob")))
            }
        }
        ds.addCompounds(mapOf("barb" to CompoundInfo(DAY, activeCompound = "barb")))
        assertAll("copy",
            { assertFalse("foob" in ds.compounds) },
            { assertFalse("barb" in ds2.compounds) }
        )
        assertThrows<UnsupportedOperationException> { ds2.addCompounds(emptyMap()) }
    }
}

private typealias TransformerResult = Pair<String, String>?

private typealias BlendComponent = DataStore.BlendEntry.Component
