package com.moshy.drugcalc.calc


import com.moshy.drugcalc.calcdata.CompoundInfo
import com.moshy.drugcalc.testutil.CheckArg

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.times
import kotlin.time.toDuration

private val ONE_DAY = (1.0).toDuration(DurationUnit.DAYS)
private val NEGATIVE_MSEC = (-1.0).toDuration(DurationUnit.MILLISECONDS)
private val ONE_MSEC = (1).toDuration(DurationUnit.MILLISECONDS)
private val ZERO_MSEC = (0).toDuration(DurationUnit.MILLISECONDS)
private val f1 = listOf(ONE_DAY)

internal class ConfigInvariantsTest {
    // we only care about the ones with invariants verified in Config.<init>
    private class ConfigP(
        val tickDuration: Duration = ONE_DAY,
        val cutoff: Double = 1.0,
    )

    @ParameterizedTest(name = "{0}")
    @MethodSource("testArgs")
    fun test(name: String, a: CheckArg<Any?, Any>) =
        a.invoke {
            this as ConfigP
            Config(tickDuration, cutoff, true)
        }

    private companion object {
        @JvmStatic
        fun testArgs() =
            listOf(
                arrayOf("success",
                    CheckArg.nothrow(ConfigP())
                ),
                arrayOf("fail tickDuration",
                    CheckArg.throws<IllegalArgumentException, _>(
                        ConfigP(tickDuration = ZERO_MSEC), "nonpositive tickDuration"
                    )
                ),
                arrayOf("fail cutoff",
                    CheckArg.throws<IllegalArgumentException, _>(
                        ConfigP(cutoff = 0.0), "nonpositive cutoff"
                    )
                ),
            ).map { Arguments.of(*it) }
    }
}

internal class CycleDescriptionValidationTest {

    @ParameterizedTest
    @MethodSource("testArgsForCycleDescriptionCtorTest")
    fun testCtor(a: CheckArg<Unit, CtorP>) =
        a.invoke { CycleDescription(compoundName, dose, start, duration, freqName) }

    internal companion object {
        class CtorP(
            val compoundName: String = "a",
            val dose: Double = 1.0,
            val start: Duration = ONE_DAY,
            val duration: Duration = ONE_DAY,
            val freqName: String = "b"
        )
        @JvmStatic
        fun testArgsForCycleDescriptionCtorTest() =
            listOf(
                CheckArg.nothrow(CtorP()),
                CheckArg.throws<IllegalArgumentException, _>(CtorP(compoundName = ""), "compoundName"),
                CheckArg.throws<IllegalArgumentException, _>(CtorP(dose = -1.0), "dose"),
                CheckArg.throws<IllegalArgumentException, _>(CtorP(start = NEGATIVE_MSEC), "start"),
                CheckArg.throws<IllegalArgumentException, _>(CtorP(duration = ZERO_MSEC), "duration"),
                CheckArg.throws<IllegalArgumentException, _>(CtorP(freqName = ""), "freqName")
            )
                .map { Arguments.of(it) }
    }
}

internal class CycleTypesTest {
    @ParameterizedTest
    @MethodSource("testArgsForWithCommon")
    fun testWithCommon(a: CheckArg<Unit, WithCommonP>) =
        a.invoke { ConfigBuilder.withCommon(start, duration, freqVals) }

    @ParameterizedTest
    @MethodSource("testArgsForWithCompound")
    fun testWithCompound(a: CheckArg<Unit, WithCompoundP>) =
        a.invoke { ConfigBuilder.withCompound(activeCompound, dose, halfLife) }

    @ParameterizedTest
    @MethodSource("testArgsForWithBlendC")
    fun testWithBlendC(a: CheckArg<Unit, WithBlendComponentP>) =
        a.invoke { ConfigBuilder.withBlendComponent(componentDose, cInfo) }

    @ParameterizedTest
    @MethodSource("testArgsForWithTransformer")
    fun testWithTransformer(a: CheckArg<Unit, WithTransformerP>) =
        a.invoke { ConfigBuilder.withTransformer(activeCompound, transformerName) }

    @ParameterizedTest
    @MethodSource("testArgsForCheckValid")
    fun testCheckValid(a: CheckArg<Unit, DecodedCycleBP>) =
        a.invoke { DecodedCycleBuilder(active, dose, halfLife, start, duration, freqs, transformer) }

    internal companion object {
        val config = Config(ONE_DAY / 2, 0.01, false)
        val ConfigBuilder = DecodedCycle.WithConfig(config)

        class WithCommonP(
            val start: Duration = 0 * ONE_DAY,
            val duration: Duration = ONE_DAY,
            val freqVals: List<Duration> = f1
        )
        class WithCompoundP(
            val activeCompound: String = "a",
            val dose: Double = 1.0,
            val halfLife: Duration = ONE_MSEC
        )
        class WithBlendComponentP(
            val componentDose: Double = 1.0,
            val cInfo: CompoundInfo = CompoundInfo(ONE_DAY, activeCompound = "a")
        )
        class WithTransformerP(
            val activeCompound: String = "a",
            val transformerName: String = "median"
        )
        // don't need hashability and equality but do need copy
        data class DecodedCycleBP(
            val active: String = "a",
            val dose: Double = 0.0,
            val halfLife: Double = 0.0,
            val start: Int = 0,
            val duration: Int = 1,
            val freqs: List<Int> = listOf(1),
            val transformer: String? = null
        )
        val compoundDCBP = DecodedCycleBP(dose = 1.0, halfLife = 1.0)
        val transformerDCBP = DecodedCycleBP(transformer = "b")

        @JvmStatic
        fun testArgsForWithCommon() =
            listOf(
                CheckArg.nothrow(WithCommonP()),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithCommonP(start = -ONE_DAY), "negative start"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithCommonP(start = ONE_MSEC), Regex("start.*not compatible")
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithCommonP(duration = 0 * ONE_DAY), "nonpositive duration"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithCommonP(duration = ONE_MSEC), Regex("duration.*not compatible")
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithCommonP(freqVals = listOf(-1 * ONE_DAY)), "nonpositive freqVals[0"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithCommonP(freqVals = listOf(ONE_MSEC)), Regex("freqVals\\[0].*not compatible")
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithCommonP(freqVals = emptyList()), "empty freqVals"
                )
            )
                .map { Arguments.of(it) }

        @JvmStatic
        fun testArgsForWithCompound() =
            listOf(
                CheckArg.nothrow(WithCompoundP()),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithCompoundP(activeCompound = ""), "empty active"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithCompoundP(dose = 0.0), "nonpositive dose"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithCompoundP(halfLife = 0 * ONE_MSEC), "nonpositive halfLife"
                ),
            )
                .map { Arguments.of(it) }

        @JvmStatic
        fun testArgsForWithBlendC() =
            listOf(
                CheckArg.nothrow(WithBlendComponentP()),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithBlendComponentP(componentDose = 0.0), "nonpositive dose"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithBlendComponentP(cInfo = CompoundInfo(ONE_DAY, activeCompound = "")), "empty active"
                )
            )
                .map { Arguments.of(it) }

        @JvmStatic
        fun testArgsForWithTransformer() =
            listOf(
                CheckArg.nothrow(WithTransformerP()),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithTransformerP(activeCompound = ""),"empty active"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    WithTransformerP(transformerName = "fred"), "unrecognized transformer"
                )
            )
                .map { Arguments.of(it) }

        @JvmStatic
        fun testArgsForCheckValid() =
            listOf(
                CheckArg.nothrow(compoundDCBP),
                CheckArg.nothrow(transformerDCBP),
                CheckArg.throws<IllegalArgumentException, _>(DecodedCycleBP(), ""
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    compoundDCBP.copy(active = ""), "empty active"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    compoundDCBP.copy(start = -1), "negative start"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    compoundDCBP.copy(duration = 0), "nonpositive duration"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    compoundDCBP.copy(freqs = emptyList()), "empty freqs"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    compoundDCBP.copy(freqs = listOf(0)), "nonpositive freq[0]"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    compoundDCBP.copy(dose = 0.0), "empty dose or halflife without transformer"
                ),
                CheckArg.throws<IllegalArgumentException, _>(
                    compoundDCBP.copy(halfLife = 0.0), "empty dose or halflife without transformer"
                )
            )
                .map { Arguments.of(it) }
    }
}