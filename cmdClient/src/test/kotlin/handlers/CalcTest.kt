package com.moshy.drugcalc.cmdclient.handlers

import com.moshy.drugcalc.types.calccommand.CycleDescription
import com.moshy.drugcalc.types.calccommand.RampedDose
import com.moshy.drugcalc.types.dataentry.FrequencyName
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.Duration.Companion.days

internal class CalcTest {
    private val base = mapOf(
        "cb" to "foo",
        "d" to "10",
        "s" to "1d",
        "t" to "2d",
        "fn" to "f1",
    )

    @Test
    fun `parseCycleDescription parses doseRamp token`() {
        val cycle = parseCycleDescription(base + ("dr" to "5@f2"), null)
        assertEquals(RampedDose(5.0, FrequencyName("f2")), cycle.doseRamp)
        assertEquals(FrequencyName("f1"), cycle.freqName)
        assertEquals(1.days, cycle.start)
        assertEquals(2.days, cycle.duration)
    }

    @Test
    fun `parseCycleDescription parses negative doseRamp token`() {
        val cycle = parseCycleDescription(base + ("dr" to "-5@f2"), null)
        assertEquals(RampedDose(-5.0, FrequencyName("f2")), cycle.doseRamp)
    }

    @Test
    fun `parseCycleDescription rejects doseRamp token without separator`() {
        val ex = assertThrows<IllegalArgumentException> {
            parseCycleDescription(base + ("dr" to "5"), null)
        }
        assertEquals("expected <add-dose>@<add-frequency>, got: 5", ex.message)
    }

    @Test
    fun `parseCycleDescription applies doseRamp to existing cycle`() {
        val old = CycleDescription(
            compoundOrBlend = "foo",
            dose = 10.0,
            start = 0.days,
            duration = 2.days,
            freqName = FrequencyName("f1"),
        )
        val cycle = parseCycleDescription(mapOf("dr" to "5@f2"), old)
        assertEquals(RampedDose(5.0, FrequencyName("f2")), cycle.doseRamp)
    }
}
