package com.moshy.drugcalc.calc.calc

import com.moshy.drugcalc.types.calccommand.*
import com.moshy.drugcalc.commontest.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertAll
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.*
import kotlin.test.assertNotNull
import kotlin.time.*

private val ONE_DAY = 1.toDuration(DurationUnit.DAYS)
private val DAY = 2
private val config = Config(ONE_DAY / 2, 0.01, false)

private fun Config.decodeFreqs(freqs: List<Duration>) = freqs.map { (it / tickDuration).toInt() }


internal class CycleTest {

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForEvaluateDecodedCycle")
    fun testEvaluateDecodedCycle(name: String, a: CheckArg<DecodedCycleResult, CycleCalculation>) =
        a.invoke { evaluateDecodedCycle(this, config) }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForEvaluateAndReduceCompoundCycles")
    fun testEvaluateAndReduceCompoundCycles(name: String, a: CheckArg<CycleResult, EvaluateAndReduceCompoundCyclesP>) =
        a.invoke { evaluateAndReduceCompoundCycles(cycle, config).mapKeys { (k, _) -> k.value } }

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

    internal companion object {

        @JvmStatic
        fun testArgsForEvaluateDecodedCycle() =
            listOf(
                arrayOf(
                    "compound + transformer",
                    CheckArg.nothrow<DecodedCycleResult, _>(
                        CycleCalculation(
                            listOf(
                                DecodedCycle(
                                    "foo", 75.0, DAY * 2.0,
                                    0, DAY * 7,
                                    listOf(DAY)
                                )
                            ),
                            listOf(
                                DecodedCycle(
                                    "foo",
                                    start = DAY, duration = DAY * 6,
                                    freqs = listOf(DAY * 2),
                                    transformer = "median"
                                )
                            )
                        )
                    ) { result ->
                        assertAll({
                            assertTrue(
                                result["foo"]!!
                                    .x
                                    .zipWithNext()
                                    .all { (a, b) ->
                                        (b - a) == 1
                                    },
                                "decodeCompound"
                            )
                        }, {
                            assertTrue(
                                result["foo:median"]!!
                                    .x
                                    .zipWithNext()
                                    .all { (a, b) ->
                                        (b - a) == DAY * 2
                                    }, "decodeTransformer"
                            )
                        })
                    }
                ),
                arrayOf(
                    "1x2 compound + transformer",
                    CheckArg.nothrow<DecodedCycleResult, _>(
                        CycleCalculation(
                            compounds = listOf(
                                DecodedCycle(
                                    "foo", dose = 100.0, halfLife = 2.0,
                                    start = 0, duration = 7, freqs = listOf(1)
                                ),
                            ),
                            transformers = listOf(
                                DecodedCycle(
                                    "foo", start = 0, duration = 7, freqs = listOf(1),
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
                arrayOf(
                    "missing transformer",
                    CheckArg.throws<IllegalArgumentException, _>(
                        CycleCalculation(
                            // we need a dummy compound because empty list is an automatically invalid CycleCalculation
                            compounds = listOf(DecodedCycle("foo", 1.0, 1.0, 0, 1, listOf(1))),
                            transformers = listOf(
                                DecodedCycle(
                                    "foo", start = 0, duration = 7, freqs = listOf(1),
                                    transformer = "fred"
                                )
                            )
                        ), "transformer not found: fred"
                    )
                ),
                arrayOf(
                    "transformer not evaluated",
                    CheckArg.throws<IllegalArgumentException, _>(
                        CycleCalculation(
                            // we need a dummy compound because empty list is an automatically invalid CycleCalculation
                            compounds = listOf(DecodedCycle("bar", 1.0, 1.0, 0, 1, listOf(1))),
                            transformers = listOf(
                                DecodedCycle(
                                    "foo", start = 0, duration = 7, freqs = listOf(1),
                                    transformer = "median"
                                )
                            )
                        ), "compound <foo> not evaluated prior to transformer <median>"
                    )
                ),
                arrayOf(
                    "transformer out of range",
                    CheckArg.throws<IllegalArgumentException, _>(
                        CycleCalculation(
                            compounds = listOf(
                                DecodedCycle(
                                    "foo", dose = 100.0, halfLife = 1.0,
                                    start = 0, duration = 1, freqs = listOf(1)
                                ),
                            ),
                            transformers = listOf(
                                DecodedCycle(
                                    "foo",
                                    start = 100,
                                    duration = 7,
                                    freqs = listOf(1),
                                    transformer = "median"
                                )
                            )
                        ), "the transformer foo:median is invalid because there is no data for it to transform"
                    )
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
                arrayOf(
                    "compoundCycle(1) !lambdaDoseCorrection",
                    CheckArg.nothrow<CycleResult, _>(
                        EvaluateAndReduceCompoundCyclesP(
                            listOf(
                                DecodedCycle(
                                    "foo",
                                    100.0,
                                    durationToTicksF(ONE_DAY, config.tickDuration),
                                    2 /* 1d */,
                                    durationToTicks(ONE_DAY * 7, config.tickDuration),
                                    durationToTicks(listOf(ONE_DAY * 2), config.tickDuration)
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
                arrayOf(
                    "compoundCycle(1) lambdaDoseCorrection",
                    CheckArg.nothrow<CycleResult, _>(
                        EvaluateAndReduceCompoundCyclesP(
                            listOf(
                                DecodedCycle(
                                    // for halfLife, we need a number != ONE_DAY to verify scaling
                                    "foo", 100.0, durationToTicksF(
                                        ONE_DAY * 2,
                                        config.tickDuration
                                    ),
                                    0, durationToTicks(ONE_DAY * 7, config.tickDuration),
                                    durationToTicks(listOf(ONE_DAY * 2), config.tickDuration)
                                )
                            ),
                            config.copy(doLambdaDoseCorrection = true)
                        )
                    ) {
                        assertEquals(1, it.size)
                        assertLess(100.0, it["foo"]?.get(0), "original dose / lambda")
                    }
                ),
                arrayOf(
                    "compoundCycle(1x2)",
                    CheckArg.nothrow<CycleResult, _>(
                        EvaluateAndReduceCompoundCyclesP(
                            listOf(
                                DecodedCycle(
                                    "foo",
                                    100.0,
                                    durationToTicksF(ONE_DAY, config.tickDuration),
                                    0 /* 0 */,
                                    durationToTicks(ONE_DAY * 7, config.tickDuration),
                                    durationToTicks(listOf(ONE_DAY * 2), config.tickDuration)
                                ),
                                DecodedCycle(
                                    "foo",
                                    100.0,
                                    durationToTicksF(ONE_DAY, config.tickDuration),
                                    4 /* 1d */,
                                    durationToTicks(ONE_DAY * 7, config.tickDuration),
                                    durationToTicks(listOf(ONE_DAY * 2), config.tickDuration)
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
                arrayOf(
                    "compoundCycle(2x1)",
                    CheckArg.nothrow<CycleResult, _>(
                        EvaluateAndReduceCompoundCyclesP(
                            listOf(
                                DecodedCycle(
                                    "foo",
                                    100.0,
                                    durationToTicksF(ONE_DAY, config.tickDuration),
                                    0 /* 1d */,
                                    durationToTicks(ONE_DAY * 7, config.tickDuration),
                                    durationToTicks(listOf(ONE_DAY * 2), config.tickDuration)
                                ),
                                DecodedCycle(
                                    "bar",
                                    100.0,
                                    durationToTicksF(ONE_DAY, config.tickDuration),
                                    4 /* dd */,
                                    durationToTicks(ONE_DAY * 7, config.tickDuration),
                                    durationToTicks(listOf(ONE_DAY * 2), config.tickDuration)
                                )
                            ), config
                        )
                    ) {
                        assertEquals(setOf("foo", "bar"), it.keys)
                        assertAll(
                            "cycle of two compounds",
                            { assertEquals(100.0, it["foo"]!![0]) },
                            { assertLess(200.0, it["foo"]!![4]) },
                            { assertEquals(100.0, it["bar"]!![4]) }
                        )
                    }
                ),
                arrayOf(
                    "compoundCycle(0)",
                    CheckArg.nothrow<CycleResult, _>(
                        EvaluateAndReduceCompoundCyclesP(emptyList(), config)
                    ) {
                        assertTrue(it.isEmpty())
                    }
                )
            )
                .map { Arguments.of(*it) }

    }
}

private fun durationToTicksF(d: Duration, tick: Duration): Double = d / tick
private fun durationToTicks(d: Duration, tick: Duration): Int = (d / tick).toInt()
private fun durationToTicks(ds: List<Duration>, tick: Duration): List<Int> =
    ds.map { (it / tick).toInt() }

private typealias CycleResult = Map<String, List<Double>>
private typealias DecodedCycleResult = Map<String, XYList>
