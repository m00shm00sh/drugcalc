package com.moshy.drugcalc.calc

import com.moshy.drugcalc.calcdata.*
import com.moshy.drugcalc.calcdata.BlendValue
import com.moshy.drugcalc.calcdata.CompoundsMap
import com.moshy.drugcalc.misc.DecodedXYList
import com.moshy.drugcalc.misc.XYList
import com.moshy.drugcalc.misc.fillActiveCompounds
import com.moshy.drugcalc.testutil.CheckArg
import com.moshy.drugcalc.testutil.assertGreater
import com.moshy.drugcalc.testutil.assertLess
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

private val ONE_DAY = (1.0).toDuration(DurationUnit.DAYS)

private const val c1PctActive = 0.75
private const val c2PctActive = 0.9

// two of the compounds have the same base to test that reducing on the base compound name functions
private val compound1 = "foo bar" to CompoundInfo(ONE_DAY * 2, c1PctActive)
private val compound2 = "bar baz" to CompoundInfo(ONE_DAY, c2PctActive)
private val compound3 = "foo baz" to CompoundInfo(ONE_DAY / 2, 1.0)
private val _compounds: CompoundsMap = mapOf(compound1, compound2, compound3).fillActiveCompounds()
private val blend: Pair<String, BlendValue> = "fred" to mapOf(
    "foo bar" to 100.0,
    "foo baz" to 50.0
)
private val _blends: BlendsMap = mapOf(blend)
private val freq1 = "f1" to listOf(ONE_DAY)
private val freq2 = "f2" to listOf(ONE_DAY * 2)
private val freq3 = "f_1_2" to listOf(ONE_DAY, ONE_DAY * 2)
private val _freqs: FrequenciesMap = mapOf(freq1, freq2, freq3)

private val config = Config(ONE_DAY / 2, 0.01, false)

private val data = runBlocking {
    DataStore().apply {
        addCompounds(_compounds)
        addBlends(_blends)
        addFrequencies(_freqs)
        freeze()
    }
}

internal class CycleTest {
    @Test
    fun multiCycle() = runTest {
        val description = listOf(
            // compound
            CycleDescription("foo bar", 100.0, ONE_DAY * 0, ONE_DAY * 7, "f1"),
            // transformer
            CycleDescription("foo:median", 0.0, ONE_DAY, ONE_DAY * 6, "f2")
        )
        val result = multiCycle(description, data, config)
        // we used [evaluateDecodedCycle to check calculation; we check decoding here
        assertAll({
            assertTrue(
                result["foo"]
                !!
                    .x
                    .zipWithNext()
                    .all { (a, b) ->
                        (b - a) == ONE_DAY / 2
                    },
                "decodeCompound"
            )
        }, {
            assertTrue(
                result["foo:median"]
                !!
                    .x
                    .zipWithNext()
                    .all { (a, b) ->
                        (b - a) == ONE_DAY * 2
                    }, "decodeTransformer"
            )
        })
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForEvaluateDecodedCycle")
    fun testEvaluateDecodedCycle(name: String, a: CheckArg<DecodedCycleResult, CycleCalculation>) =
        a.invoke { evaluateDecodedCycle(this, config) }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForEvaluateAndReduceCompoundCycles")
    fun testEvaluateAndReduceCompoundCycles(name: String, a: CheckArg<CycleResult, EvaluateAndReduceCompoundCyclesP>) =
        a.invoke { evaluateAndReduceCompoundCycles(cycle, config) }

    @Test
    fun decodeTimeTickScaling() {
        val encoded: Map<String, XYList> = mapOf(
            "a" to XYList.pointPlot(listOf(1, 2), listOf(1.0, 2.0))
        )
        val expected = mapOf(
            "a" to DecodedXYList.pointPlot(listOf(1, 2).map { it.toDuration(DurationUnit.HOURS) }, listOf(1.0, 2.0))
        )
        val tConfig = config.copy(tickDuration = (1).toDuration(DurationUnit.HOURS))
        val decoded = encoded.decodeTimeTickScaling(tConfig)
        assertEquals(expected, decoded)
    }

    @Test
    fun decodeCycles() {
        val quarterDay = ONE_DAY / 4
        val halfWeek = ONE_DAY * 3.5
        val oneWeek = ONE_DAY * 7
        val f1InTicks = durationToTicks(freq1.second, quarterDay)
        val f2InTicks = durationToTicks(freq2.second, quarterDay)
        val f3InTicks = durationToTicks(freq3.second, quarterDay)
        val thisConfig = config.copy(tickDuration = quarterDay)
        val description = listOf(
            // compound
            CycleDescription("foo bar", 50.0, quarterDay * 0, oneWeek, "f2"),
            CycleDescription("bar baz", 100.0, ONE_DAY, halfWeek, "f1"),
            // blend
            CycleDescription("fred", 300.0, quarterDay * 2, oneWeek, "f_1_2"),
            // transformer
            CycleDescription("foo:median", 0.0, ONE_DAY, oneWeek - ONE_DAY, "f1")
        )
        val expected = CycleCalculation(
            cycles = listOf(
                DecodedCycleBuilder(
                    "foo", 50 * c1PctActive, durationToTicksF(ONE_DAY * 2, quarterDay),
                    0, durationToTicks(oneWeek, quarterDay), f2InTicks
                ),
                DecodedCycleBuilder(
                    "bar", 100 * c2PctActive, durationToTicksF(ONE_DAY, quarterDay),
                    durationToTicks(ONE_DAY, quarterDay), durationToTicks(halfWeek, quarterDay), f1InTicks
                ),
                DecodedCycleBuilder(
                    "foo", 300.0 * (2.0 / 3) * c1PctActive, durationToTicksF(ONE_DAY * 2, quarterDay),
                    durationToTicks(ONE_DAY / 2, quarterDay), durationToTicks(oneWeek, quarterDay), f3InTicks
                ),
                DecodedCycleBuilder(
                    "foo", 300.0 * (1.0 / 3), durationToTicksF(ONE_DAY / 2, quarterDay),
                    durationToTicks(ONE_DAY / 2, quarterDay), durationToTicks(oneWeek, quarterDay), f3InTicks
                ),
            ),
            transformers = listOf(
                DecodedCycleBuilder(
                    "foo",
                    start = durationToTicks(ONE_DAY, quarterDay),
                    duration = durationToTicks(oneWeek - ONE_DAY, quarterDay),
                    freqs = f1InTicks, transformer = "median"
                ),
            )
        )

        val decoded = decodeCycles(description, data, thisConfig)
        assertEquals(expected, decoded)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForDecodeCyclesValidation")
    fun validateDecodeCycles(name: String, a: CheckArg<Unit, List<CycleDescription>>) =
        a.invoke { decodeCycles(this, data, config) }

    internal companion object {
        val halfDay = ONE_DAY / 2
        val quarterDay = ONE_DAY / 4
        val oneWeek = ONE_DAY * 7
        val dayInTicksF = durationToTicksF(ONE_DAY, halfDay)
        val dayInTicks = dayInTicksF.toInt()
        val f1InTicks = durationToTicks(freq1.second, halfDay)

        @JvmStatic
        fun testArgsForEvaluateDecodedCycle() =
            listOf(
                arrayOf("1x2 compound + transformer",
                    CheckArg.nothrow<DecodedCycleResult, _>(
                        CycleCalculation(
                            cycles = listOf(
                                DecodedCycleBuilder(
                                    "foo", dose = 100.0, halfLife = dayInTicksF,
                                    start = 0, duration = dayInTicks * 7, freqs = f1InTicks
                                ),
                                DecodedCycleBuilder(
                                    "foo", dose = 100.0, halfLife = dayInTicksF * 2,
                                    start = 0, duration = dayInTicks * 7, freqs = f1InTicks
                                )
                            ),
                            transformers = listOf(
                                DecodedCycleBuilder(
                                    "foo", start = 0, duration = dayInTicks * 7, freqs = f1InTicks,
                                    transformer = "median"
                                )
                            )
                        )
                    ) {
                        assertAll(
                            // check that the compounds and transformers entries didn't overwrite each other
                            { assertEquals(setOf("foo", "foo:median"), it.keys) },
                            { assertEquals(XYList.PlotType.POINT, it["foo"]!!.type) },
                            { assertEquals(XYList.PlotType.BAR, it["foo:median"]!!.type) }
                        )
                    }
                ),
                arrayOf("missing transformer",
                    CheckArg.throws<IllegalArgumentException, _>(
                        CycleCalculation(
                            cycles = emptyList(),
                            transformers = listOf(
                                DecodedCycleBuilder(
                                    "foo", start = 0, duration = dayInTicks * 7, freqs = f1InTicks,
                                    transformer = "fred"
                                )
                            )
                        ), "transformer not found: fred")
                ),
                arrayOf("transformer not evaluated",
                    CheckArg.throws<IllegalArgumentException, _>(
                        CycleCalculation(
                            cycles = emptyList(),
                            transformers = listOf(
                                DecodedCycleBuilder(
                                    "foo", start = 0, duration = dayInTicks * 7, freqs = f1InTicks,
                                    transformer = "median"
                                )
                            )
                        ), "base <foo> not evaluated prior to transformer <median>")
                ),
                arrayOf("transformer out of range",
                    CheckArg.throws<IllegalArgumentException, _>(
                        CycleCalculation(
                            cycles = listOf(
                                DecodedCycleBuilder(
                                    "foo", dose = 100.0, halfLife = 1.0,
                                    start = 0, duration = dayInTicks, freqs = f1InTicks
                                ),
                            ),
                            transformers = listOf(
                                DecodedCycleBuilder(
                                    "foo",
                                    start = dayInTicks * 100,
                                    duration = dayInTicks * 7,
                                    freqs = f1InTicks,
                                    transformer = "median"
                                )
                            )
                        ), "the transformer foo:median is invalid because there is no data for it to transform")
                )
            )
                .map { Arguments.of(*it) }

        class EvaluateAndReduceCompoundCyclesP(
            val cycle: List<DecodedCycle>,
            val config: Config
        )
        @JvmStatic
        fun testArgsForEvaluateAndReduceCompoundCycles() =
            listOf(
                // [0] = name: String, [1] = a: CheckArg<CycleResult>
                arrayOf("compoundCycle(1) !lambdaDoseCorrection",
                    CheckArg.nothrow<CycleResult, _>(
                        EvaluateAndReduceCompoundCyclesP(
                            listOf(
                                DecodedCycleBuilder(
                                    "foo",
                                    100.0,
                                    durationToTicksF(ONE_DAY, halfDay),
                                    2 /* 1d */,
                                    durationToTicks(ONE_DAY * 7, halfDay),
                                    durationToTicks(freq2.second, halfDay)
                                )
                            ),
                            config
                        )
                    ) {
                        assertEquals(1, it.size)
                        val /* @TimeTickIndexed */ data = assertNotNull(it["foo"])
                        assertAll(
                            { assertEquals(0.0, data[0], "start") },
                            { assertEquals(0.0, data[1], "start") },
                            { assertEquals(data[2], data[4] * 2, "one half life") },
                            { assertEquals(100.0, data[2], "original dose") },
                            { assertGreater(greaterThan = data[2], data[6], "shift and reduce") },
                            { assertLess(lessThan = data[14], data[18], "last redose") },
                            { assertTrue(data.last() >= 0.01) } // cutoff
                        )
                    }
                ),
                arrayOf("compoundCycle(1) lambdaDoseCorrection",
                    CheckArg.nothrow<CycleResult, _>(
                        EvaluateAndReduceCompoundCyclesP(
                            listOf(
                                DecodedCycleBuilder(
                                    // for halfLife, we need a number != ONE_DAY to verify scaling
                                    "foo", 100.0, durationToTicksF(ONE_DAY * 2,
                                        halfDay
                                    ),
                                    0, durationToTicks(ONE_DAY * 7, halfDay), durationToTicks(freq2.second,
                                        halfDay
                                    )
                                )
                            ),
                            config.copy(doLambdaDoseCorrection = true)
                        )
                    ) {
                        assertEquals(1, it.size)
                        assertLess(100.0, it["foo"]?.get(0), "original dose / lambda")
                    }
                ),
                arrayOf("compoundCycle(1x2)",
                    CheckArg.nothrow<CycleResult, _>(
                        EvaluateAndReduceCompoundCyclesP(
                            listOf(
                                DecodedCycleBuilder(
                                    "foo",
                                    100.0,
                                    durationToTicksF(ONE_DAY, halfDay),
                                    0 /* 0 */,
                                    durationToTicks(ONE_DAY * 7, halfDay),
                                    durationToTicks(freq2.second, halfDay)
                                ),
                                DecodedCycleBuilder(
                                    "foo",
                                    100.0,
                                    durationToTicksF(ONE_DAY, halfDay),
                                    4 /* 1d */,
                                    durationToTicks(ONE_DAY * 7, halfDay),
                                    durationToTicks(freq2.second, halfDay)
                                )
                            ), config
                        )
                    ) {
                        assertEquals(1, it.size)
                        val /* @TimeTickIndexed */ data = assertNotNull(it["foo"])
                        assertAll(
                            { assertGreater(200.0, data[4], ">0 redoses + original dose") },
                            { assertGreater(0.001, data.last(), "cutoff") }
                        )
                    }
                ),
                arrayOf("compoundCycle(2x1)",
                    CheckArg.nothrow<CycleResult, _>(
                        EvaluateAndReduceCompoundCyclesP(
                            listOf(
                                DecodedCycleBuilder(
                                    "foo",
                                    100.0,
                                    durationToTicksF(ONE_DAY, halfDay),
                                    0 /* 1d */,
                                    durationToTicks(ONE_DAY * 7, halfDay),
                                    durationToTicks(freq2.second, halfDay)
                                ),
                                DecodedCycleBuilder(
                                    "bar",
                                    100.0,
                                    durationToTicksF(ONE_DAY, halfDay),
                                    4 /* dd */,
                                    durationToTicks(ONE_DAY * 7, halfDay),
                                    durationToTicks(freq2.second, halfDay)
                                )
                            ), config
                        )
                    ) {
                        assertEquals(setOf("foo", "bar"), it.keys)
                        assertAll("cycle of two compounds",
                            { assertEquals(100.0, it["foo"]!![0]) },
                            { assertLess(200.0, it["foo"]!![4]) },
                            { assertEquals(100.0, it["bar"]!![4]) }
                        )
                    }
                ),
                arrayOf("compoundCycle(0)",
                    CheckArg.nothrow<CycleResult, _>(EvaluateAndReduceCompoundCyclesP(emptyList(), config)
                    ) {
                        assertTrue(it.isEmpty())
                    }
                )
            )
                .map { Arguments.of(*it) }

        @JvmStatic
        fun testArgsForDecodeCyclesValidation() =
            listOf(
                arrayOf("transformer already specified",
                    CheckArg.throws<IllegalArgumentException, _>(
                        listOf(
                            // compound
                            CycleDescription(
                                "foo bar", 50.0,
                                quarterDay * 0, oneWeek, "f2"
                            ),
                            // transformer
                            CycleDescription(
                                "foo:median", 0.0,
                                ONE_DAY, oneWeek - ONE_DAY, "f1"
                            ),
                            CycleDescription(
                                "foo:median", 0.0,
                                ONE_DAY, oneWeek - ONE_DAY, "f1"
                            )
                        ),
                        Regex("transformer <.*?foo,.*\\bmedian\\)?> already specified")
                    )
                ),
                arrayOf("no match for compound",
                    CheckArg.throws<IllegalArgumentException, _>(
                        listOf(
                            CycleDescription("waldo", 1.0, quarterDay * 0, oneWeek, "f2"),
                        ),"no match for compound with name or spec \"waldo\""
                    )
                ),
                arrayOf("no match for frequency",
                    CheckArg.throws<IllegalArgumentException, _>(
                        listOf(
                            CycleDescription("foo bar", 1.0, quarterDay * 0, oneWeek, "z"),
                        ), "no match for frequency with name \"z\""
                    )
                ),
                arrayOf("transformer refers to missing compound",
                    CheckArg.throws<IllegalArgumentException, _>(
                        listOf(
                            CycleDescription("foo:median", 0.0, quarterDay * 0, oneWeek, "f1"),
                        ), "transformer \"median\" refers to compound \"foo\""
                    )
                ),
            )
                .map { Arguments.of(*it) }
    }
}

private fun durationToTicksF(d: Duration, tick: Duration): @TimeTickScaled Double = d / tick
private fun durationToTicks(d: Duration, tick: Duration): @TimeTickScaled Int = (d / tick).toInt()
private fun durationToTicks(ds: List<Duration>, tick: Duration): List<@TimeTickScaled Int> =
    ds.map { (it / tick).toInt() }

private typealias CycleResult = Map<String, @TimeTickIndexed List<Double>>
private typealias DecodedCycleResult = Map<String, XYList>
