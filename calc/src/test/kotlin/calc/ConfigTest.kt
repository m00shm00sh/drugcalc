package com.moshy.drugcalc.calc.calc


import com.moshy.drugcalc.commontest.CheckArg
import com.moshy.drugcalc.types.calccommand.Config

import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
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
