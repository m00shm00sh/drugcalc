package com.moshy.drugcalc.calc.datacontroller

import com.moshy.drugcalc.calc.calc.CycleCalculation
import com.moshy.drugcalc.calc.calc.DecodedCycle
import com.moshy.drugcalc.commontest.CheckArg
import com.moshy.drugcalc.types.calccommand.*
import com.moshy.drugcalc.types.dataentry.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
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
        return decoder.decode(cycle)[0] ?: throw NoSuchElementException("group: 0")
    }

    private fun doDecodeGroups(
        cycle: List<CycleDescription>,
        config: Config,
        data: Data,
        groups: Set<Int>? = null,
        transformerAuxF: Map<FrequencyName, FrequencyValue> = emptyMap()
    ): Map<Int, CycleCalculation> {
        val decoder = Decoder(config, data, transformerAuxF)
        val grouped = decoder.decode(cycle)
        if (groups == null)
            return grouped
        return grouped.filterKeys { it in groups }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgsForDecodeCyclesValidation")
    fun testDecoder(name: String, a: CheckArg<CycleCalculation, DecoderP>) =
        a.invoke { doDecode(cycle, config, data, transformerAuxF) }

    @Test
    fun `testDecoder splits identical entries by group`() {
        val tick = C.CONFIG.tickDuration
        val result = doDecodeGroups(
            listOf(
                // compound group 0
                CycleDescription(
                    compoundOrBlend = "foo", variantOrTransformer = "bar", dose = 50.0,
                    start = C.ZERO_DUR, duration = C.ONE_WEEK,
                    freqName = FrequencyName("f2"), group = 0
                ),
                // compound group 1 - identical except group
                CycleDescription(
                    compoundOrBlend = "foo", variantOrTransformer = "bar", dose = 50.0,
                    start = C.ZERO_DUR, duration = C.ONE_WEEK,
                    freqName = FrequencyName("f2"), group = 1
                ),
                // transformer group 0
                CycleDescription(
                    CycleDescription.PREFIX_TRANSFORMER,
                    "foo", "median",
                    start = C.ONE_DAY, duration = C.ONE_WEEK - C.ONE_DAY,
                    freqName = FrequencyName("f1"), group = 0
                ),
                // transformer group 1 - identical except group
                CycleDescription(
                    CycleDescription.PREFIX_TRANSFORMER,
                    "foo", "median",
                    start = C.ONE_DAY, duration = C.ONE_WEEK - C.ONE_DAY,
                    freqName = FrequencyName("f1"), group = 1
                )
            ),
            C.CONFIG, C.DATA
        )

        assertEquals(setOf(0, 1), result.keys)

        val expectedCompound = DecodedCycle(
            "foo",
            50 * C.C1_PCT_ACTIVE,
            durationToTicksF(C.COMPOUND_1.second.halfLife, tick),
            0,
            durationToTicks(C.ONE_WEEK, tick),
            C.F2_IN_TICKS
        )

        val expectedTransformer = DecodedCycle(
            "foo",
            start = durationToTicks(C.ONE_DAY, tick),
            duration = durationToTicks(C.ONE_WEEK - C.ONE_DAY, tick),
            freqs = C.F1_IN_TICKS,
            transformer = "median"
        )

        val expectedCalc = CycleCalculation(
            compounds = listOf(expectedCompound),
            transformers = listOf(expectedTransformer)
        )

        assertEquals(expectedCalc, result[0])
        assertEquals(expectedCalc, result[1])
    }

    @Test
    fun `testDecoder expands doseRamp into constant-dose groups`() {
        val tick = C.CONFIG.tickDuration
        val result = doDecode(
            listOf(
                CycleDescription(
                    compoundOrBlend = "foo", variantOrTransformer = "bar", dose = 10.0,
                    start = C.ZERO_DUR, duration = C.ONE_DAY * 7,
                    freqName = FrequencyName("f1"),
                    doseRamp = RampedDose(5.0, FrequencyName("f2"))
                )
            ),
            C.CONFIG, C.DATA
        )

        val halfLife = durationToTicksF(C.COMPOUND_1.second.halfLife, tick)
        val oneDay = durationToTicks(C.ONE_DAY, tick)
        val twoDays = durationToTicks(C.ONE_DAY * 2, tick)
        val base = 10.0 * C.C1_PCT_ACTIVE
        val add = 5.0 * C.C1_PCT_ACTIVE
        val expected = CycleCalculation(
            compounds = listOf(
                DecodedCycle("foo", base, halfLife, 0, oneDay, listOf(oneDay)),
                DecodedCycle("foo", base + add, halfLife, twoDays, oneDay, listOf(oneDay)),
                DecodedCycle("foo", base + 2 * add, halfLife, twoDays * 2, oneDay, listOf(oneDay)),
                DecodedCycle("foo", base + 3 * add, halfLife, twoDays * 3, oneDay, listOf(oneDay)),
            )
        )
        assertEquals(expected, result)
    }

    @Test
    fun `testDecoder doseRamp down drops nonpositive doses`() {
        val tick = C.CONFIG.tickDuration
        val result = doDecode(
            listOf(
                CycleDescription(
                    compoundOrBlend = "foo", variantOrTransformer = "bar", dose = 10.0,
                    start = C.ZERO_DUR, duration = C.ONE_DAY * 7,
                    freqName = FrequencyName("f1"),
                    doseRamp = RampedDose(-10.0, FrequencyName("f1"))
                )
            ),
            C.CONFIG, C.DATA
        )

        val expected = CycleCalculation(
            compounds = listOf(
                DecodedCycle(
                    "foo", 10.0 * C.C1_PCT_ACTIVE,
                    durationToTicksF(C.COMPOUND_1.second.halfLife, tick),
                    0, 1, listOf(2)
                )
            )
        )
        assertEquals(expected, result)
    }

    @Test
    fun `testDecoder doseRamp down stops before nonpositive dose`() {
        val tick = C.CONFIG.tickDuration
        val result = doDecode(
            listOf(
                CycleDescription(
                    compoundOrBlend = "foo", variantOrTransformer = "bar", dose = 10.0,
                    start = C.ZERO_DUR, duration = C.ONE_DAY * 8,
                    freqName = FrequencyName("f1"),
                    doseRamp = RampedDose(-3.0, FrequencyName("f2"))
                )
            ),
            C.CONFIG, C.DATA
        )

        val halfLife = durationToTicksF(C.COMPOUND_1.second.halfLife, tick)
        val oneDay = durationToTicks(C.ONE_DAY, tick)
        val base = 10.0 * C.C1_PCT_ACTIVE
        val add = -3.0 * C.C1_PCT_ACTIVE
        val expected = CycleCalculation(
            compounds = listOf(
                DecodedCycle("foo", base, halfLife, 0, oneDay, listOf(oneDay)),
                DecodedCycle("foo", base + add, halfLife, oneDay * 2, oneDay, listOf(oneDay)),
                DecodedCycle("foo", base + 2 * add, halfLife, oneDay * 4, oneDay, listOf(oneDay)),
                DecodedCycle("foo", base + 3 * add, halfLife, oneDay * 6, oneDay, listOf(oneDay)),
            )
        )
        assertEquals(expected, result)
    }

    @Test
    fun `testDecoder scales blend doseRamp by component share`() {
        val tick = C.CONFIG.tickDuration
        val result = doDecode(
            listOf(
                CycleDescription(
                    prefix = CycleDescription.PREFIX_BLEND,
                    compoundOrBlend = "fred",
                    dose = 300.0,
                    start = C.ZERO_DUR, duration = C.ONE_DAY * 3,
                    freqName = FrequencyName("f1"),
                    doseRamp = RampedDose(30.0, FrequencyName("f2"))
                )
            ),
            C.CONFIG, C.DATA
        )

        val oneDay = durationToTicks(C.ONE_DAY, tick)
        val twoDays = durationToTicks(C.ONE_DAY * 2, tick)
        val halfLife1 = durationToTicksF(C.COMPOUND_1.second.halfLife, tick)
        val halfLife3 = durationToTicksF(C.COMPOUND_3.second.halfLife, tick)
        val base1 = 300.0 * (2.0 / 3) * C.C1_PCT_ACTIVE
        val add1 = 30.0 * (2.0 / 3) * C.C1_PCT_ACTIVE
        val base3 = 300.0 * (1.0 / 3)
        val add3 = 30.0 * (1.0 / 3)
        val expected = CycleCalculation(
            compounds = listOf(
                DecodedCycle("foo", base1, halfLife1, 0, oneDay, listOf(oneDay)),
                DecodedCycle("foo", base1 + add1, halfLife1, twoDays, oneDay, listOf(oneDay)),
                DecodedCycle("foo", base3, halfLife3, 0, oneDay, listOf(oneDay)),
                DecodedCycle("foo", base3 + add3, halfLife3, twoDays, oneDay, listOf(oneDay)),
            )
        )
        assertEquals(expected, result)
    }

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

