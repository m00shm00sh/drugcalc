package com.moshy.drugcalc.calc.datacontroller

import com.moshy.drugcalc.calc.calc.CycleCalculation
import com.moshy.drugcalc.calc.calc.DecodedCycle
import com.moshy.drugcalc.commontest.CheckArg
import com.moshy.drugcalc.types.calccommand.*
import com.moshy.drugcalc.types.dataentry.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.*
import kotlin.time.*


private fun splitCN(s: String) =
    s.split(' ').let {
        require(it.size == 2)
        CompoundName(CompoundBase(it[0]), it[1])
    }

private fun durationToTicksF(d: Duration, tick: Duration): Double = d / tick
private fun durationToTicks(d: Duration, tick: Duration): Int = (d / tick).toInt()
private fun durationToTicks(ds: List<Duration>, tick: Duration): List<Int> = ds.map { (it / tick).toInt() }

private object C {
    val ONE_DAY = (1.0).toDuration(DurationUnit.DAYS)
    val HALF_DAY = ONE_DAY / 2
    val QUARTER_DAY = ONE_DAY / 4
    val HALF_WEEK = ONE_DAY * 3.5
    val ONE_WEEK = ONE_DAY * 7
    val ZERO_DUR = ONE_DAY * 0

    val CONFIG = Config(QUARTER_DAY, 0.01, false)

    const val C1_PCT_ACTIVE = 0.75
    const val C2_PCT_ACTIVE = 0.9

    val COMPOUND_1 = splitCN("foo bar") to CompoundInfo(ONE_DAY * 2, C1_PCT_ACTIVE)
    val COMPOUND_2 = splitCN("bar baz") to CompoundInfo(ONE_DAY, C2_PCT_ACTIVE)
    val COMPOUND_3 = splitCN("foo baz") to CompoundInfo(ONE_DAY / 2, 1.0)

    val BLEND_1: Pair<BlendName, BlendValue> = BlendName("fred") to BlendValue(
        mapOf(
            splitCN("foo bar") to 100.0,
            splitCN("foo baz") to 50.0
        )
    )

    val FREQ_1 = FrequencyName("f1") to FrequencyValue(listOf(ONE_DAY))
    val F1_IN_TICKS = durationToTicks(FREQ_1.second, CONFIG.tickDuration)
    val FREQ_2 = FrequencyName("f2") to FrequencyValue(listOf(ONE_DAY * 2))
    val F2_IN_TICKS = durationToTicks(FREQ_2.second, CONFIG.tickDuration)
    val FREQ_3 = FrequencyName("f_1_2") to FrequencyValue(listOf(ONE_DAY, ONE_DAY * 2))
    val F3_IN_TICKS = durationToTicks(FREQ_3.second, CONFIG.tickDuration)
    val FREQ_X = FrequencyName("fX") to FrequencyValue(listOf(HALF_DAY))
    val FX_IN_TICKS = durationToTicks(FREQ_X.second, CONFIG.tickDuration)

    val DATA = Data(
        compounds = mapOf(
            COMPOUND_1, COMPOUND_2, COMPOUND_3
        ),
        blends = mapOf(
            BLEND_1
        ),
        frequencies = mapOf(
            FREQ_1, FREQ_2, FREQ_3
        )
    )

    val XF_FREQS = mapOf(
        FREQ_X
    )
}

internal class DecoderTest {
    private fun doDecode(
        cycle: List<CycleDescription>,
        config: Config,
        data: Data,
        transformerAuxF: Map<FrequencyName, FrequencyValue> = emptyMap()
    ): CycleCalculation {
        val decoder = Decoder(config, data, transformerAuxF)
        return decoder.decode(cycle)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForDecodeCyclesValidation")
    fun testDecoder(name: String, a: CheckArg<CycleCalculation, DecoderP>) =
        a.invoke { doDecode(cycle, config, data, transformerAuxF) }

    data class DecoderP(
        val cycle: List<CycleDescription>,
        val config: Config = C.CONFIG,
        val data: Data = C.DATA,
        val transformerAuxF: Map<FrequencyName, FrequencyValue> = emptyMap()
    )

    internal companion object {

        @JvmStatic
        fun testArgsForDecodeCyclesValidation() =
            listOf(
                arrayOf(
                    "unresolved compound",
                    CheckArg.throws<IllegalStateException, _>(
                        DecoderP(
                            listOf(
                                CycleDescription(
                                    compoundOrBlend = "waldo", dose = 1.0,
                                    start = C.QUARTER_DAY * 0, duration = C.ONE_WEEK,
                                    freqName = FrequencyName("f2")
                                )
                            )
                        ), "unresolved compound ${CompoundName(CompoundBase("waldo"))}"
                    )
                ),
                arrayOf(
                    "unresolved blend",
                    CheckArg.throws<IllegalStateException, _>(
                        DecoderP(
                            listOf(
                                CycleDescription(
                                    CycleDescription.PREFIX_BLEND, "waldo", dose = 1.0,
                                    start = C.QUARTER_DAY * 0, duration = C.ONE_WEEK,
                                    freqName = FrequencyName("f2")
                                )
                            )
                        ), "unresolved blend waldo"
                    )
                ),
                arrayOf(
                    "unresolved frequency",
                    CheckArg.throws<IllegalStateException, _>(
                        DecoderP(
                            listOf(
                                CycleDescription(
                                    compoundOrBlend = "foo", variantOrTransformer = "bar", dose = 1.0,
                                    start = C.QUARTER_DAY * 0, duration = C.ONE_WEEK, freqName = FrequencyName("z")
                                ),
                            )
                        ), "unresolved frequency z"
                    )
                ),
                arrayOf(
                    "valid",
                    CheckArg.nothrow<CycleCalculation, _>(
                        DecoderP(
                            listOf(
                                // compound
                                CycleDescription(
                                    compoundOrBlend = "foo", variantOrTransformer = "bar", dose = 50.0,
                                    start = C.ZERO_DUR, duration = C.ONE_WEEK,
                                    freqName = FrequencyName("f2")
                                ),
                                CycleDescription(
                                    compoundOrBlend = "bar", variantOrTransformer = "baz", dose = 100.0,
                                    start = C.ONE_DAY, duration = C.HALF_WEEK, freqName = FrequencyName("f1")
                                ),
                                // blend
                                CycleDescription(
                                    CycleDescription.PREFIX_BLEND, "fred",
                                    dose = 300.0,
                                    start = C.HALF_DAY, duration = C.ONE_WEEK,
                                    freqName = FrequencyName("f_1_2")
                                ),
                                // transformer
                                CycleDescription(
                                    CycleDescription.PREFIX_TRANSFORMER,
                                    "foo", "median",
                                    start = C.ONE_DAY, duration = C.ONE_WEEK - C.ONE_DAY,
                                    freqName = FrequencyName("f1")
                                )
                            )
                        )
                    ) {
                        val tick = C.CONFIG.tickDuration
                        val expected = CycleCalculation(
                            compounds = listOf(
                                DecodedCycle(
                                    "foo",
                                    50 * C.C1_PCT_ACTIVE,
                                    durationToTicksF(C.COMPOUND_1.second.halfLife, tick),
                                    0,
                                    durationToTicks(C.ONE_WEEK, tick),
                                    C.F2_IN_TICKS
                                ),
                                DecodedCycle(
                                    "bar",
                                    100 * C.C2_PCT_ACTIVE,
                                    durationToTicksF(C.COMPOUND_2.second.halfLife, tick),
                                    durationToTicks(C.ONE_DAY, tick),
                                    durationToTicks(C.HALF_WEEK, tick),
                                    C.F1_IN_TICKS
                                ),
                                // next two from blend
                                DecodedCycle(
                                    "foo",
                                    300 * (2.0 / 3) * C.C1_PCT_ACTIVE,
                                    durationToTicksF(C.COMPOUND_1.second.halfLife, tick),
                                    durationToTicks(C.HALF_DAY, tick),
                                    durationToTicks(C.ONE_WEEK, tick),
                                    C.F3_IN_TICKS
                                ),
                                DecodedCycle(
                                    "foo",
                                    300.0 * (1.0 / 3),
                                    durationToTicksF(C.COMPOUND_3.second.halfLife, tick),
                                    durationToTicks(C.HALF_DAY, tick),
                                    durationToTicks(C.ONE_WEEK, tick),
                                    C.F3_IN_TICKS
                                )
                            ),
                            transformers = listOf(
                                DecodedCycle(
                                    "foo",
                                    start = durationToTicks(C.ONE_DAY, tick),
                                    duration = durationToTicks(C.ONE_WEEK - C.ONE_DAY, tick),
                                    freqs = C.F1_IN_TICKS,
                                    transformer = "median"
                                ),
                            )
                        )
                        assertEquals(expected, it)
                    }
                ),
                arrayOf(
                    "transformer freq",
                    CheckArg.nothrow<CycleCalculation, _>(
                        DecoderP(
                            listOf(
                                // compound
                                CycleDescription(
                                    compoundOrBlend = "foo", variantOrTransformer = "bar", dose = 50.0,
                                    start = C.ZERO_DUR, duration = C.ONE_WEEK,
                                    freqName = FrequencyName("f2")
                                ),
                                // transformer
                                CycleDescription(
                                    CycleDescription.PREFIX_TRANSFORMER,
                                    "foo", "median",
                                    start = C.ONE_DAY, duration = C.ONE_WEEK - C.ONE_DAY,
                                    freqName = FrequencyName("fX")
                                )
                            ),
                            transformerAuxF = C.XF_FREQS
                        )
                    ) {
                        val tick = C.CONFIG.tickDuration
                        val expected = CycleCalculation(
                            compounds = listOf(
                                DecodedCycle(
                                    "foo",
                                    50 * C.C1_PCT_ACTIVE,
                                    durationToTicksF(C.COMPOUND_1.second.halfLife, tick),
                                    0,
                                    durationToTicks(C.ONE_WEEK, tick),
                                    C.F2_IN_TICKS
                                ),
                            ),
                            transformers = listOf(
                                DecodedCycle(
                                    "foo",
                                    start = durationToTicks(C.ONE_DAY, tick),
                                    duration = durationToTicks(C.ONE_WEEK - C.ONE_DAY, tick),
                                    freqs = C.FX_IN_TICKS,
                                    transformer = "median"
                                ),
                            )
                        )
                        assertEquals(expected, it)
                    }
                ),
            )
                .map { Arguments.of(*it) }
    }
}

