package com.moshy.drugcalc.types

import com.moshy.drugcalc.types.calccommand.Config
import com.moshy.drugcalc.types.calccommand.CycleDescription
import com.moshy.drugcalc.types.calccommand.CycleResult
import com.moshy.drugcalc.types.calccommand.DecodeSpec
import com.moshy.drugcalc.types.calccommand.XYList
import com.moshy.drugcalc.types.dataentry.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.time.*

internal class CalcTypesTest {
    @Test
    fun `decodeTimeTickScaling duration`() {
        val encoded: CycleResult<XYList.OfRaw> = CycleResult(mapOf(
            "a" to XYList.OfRaw.pointPlot(listOf(1, 2), listOf(1.0, 2.0))
        ))
        val expected: CycleResult<XYList.OfDuration> = CycleResult(mapOf(
            "a" to XYList.OfDuration(XYList.PlotType.POINT,
                listOf(1, 2).map { it.toDuration(DurationUnit.HOURS) },
                listOf(1.0, 2.0)
            )
        ))
        val tConfig = Config(tickDuration = (1).toDuration(DurationUnit.HOURS))

        val decoder = DecodeSpec.ToDuration()

        val decoded = decoder.decodeGivenConfig(encoded, tConfig)
        assertEquals(expected, decoded)
    }

    @Test
    fun `decodeTimeTickScaling none`() {
        val encoded: CycleResult<XYList.OfRaw> = CycleResult(mapOf(
            "a" to XYList.OfRaw.pointPlot(listOf(1, 2), listOf(1.0, 2.0))
        ))
        val expected: CycleResult<XYList.OfRaw> = CycleResult(mapOf(
            "a" to XYList.OfRaw.pointPlot(listOf(1, 2), listOf(1.0, 2.0))
        ))
        val tConfig = Config(tickDuration = (1).toDuration(DurationUnit.HOURS))

        val decoder = DecodeSpec.None()

        val decoded = decoder.decodeGivenConfig(encoded, tConfig)
        assertEquals(expected, decoded)
    }

    @Test
    fun `decodeTimeTickScaling scaling`() {
        val encoded: CycleResult<XYList.OfRaw> = CycleResult(mapOf(
            "a" to XYList.OfRaw.pointPlot(listOf(1, 2), listOf(1.0, 2.0))
        ))
        val expected: CycleResult<XYList.OfDay> = CycleResult(mapOf(
            "a" to XYList.OfDay(XYList.PlotType.POINT, listOf(0.5, 1.0), listOf(1.0, 2.0))
        ))
        val tConfig = Config(tickDuration = (1).toDuration(DurationUnit.HOURS))

        val decoder = DecodeSpec.Scaled(2.toDuration(DurationUnit.HOURS))
        val decoded = decoder.decodeGivenConfig(encoded, tConfig)
        assertEquals(expected, decoded)
    }

    @Test
    fun `CycleDescription accepts valid compound`() {
        assertDoesNotThrow {
            CycleDescription(
                compoundOrBlend = "testosterone",
                variantOrTransformer = "enanthate",
                dose = 100.0,
                start = Duration.ZERO,
                duration = 7.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
    }

    @Test
    fun `CycleDescription accepts valid blend`() {
        assertDoesNotThrow {
            CycleDescription(
                prefix = CycleDescription.PREFIX_BLEND,
                compoundOrBlend = "sustanon 250",
                dose = 1.0,
                start = 1.0.toDuration(DurationUnit.DAYS),
                duration = 1.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every other day")
            )
        }
    }

    @Test
    fun `CycleDescription accepts valid transformer without dose`() {
        assertDoesNotThrow {
            CycleDescription(
                prefix = CycleDescription.PREFIX_TRANSFORMER,
                compoundOrBlend = "ligma",
                variantOrTransformer = "median",
                start = 1.0.toDuration(DurationUnit.DAYS),
                duration = 1.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every week")
            )
        }
    }

    @Test
    fun `CycleDescription rejects empty compound name`() {
        val ex = assertThrows<IllegalArgumentException> {
            CycleDescription(
                compoundOrBlend = "",
                dose = 1.0,
                start = Duration.ZERO,
                duration = 1.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
        assertEquals("empty compoundName", ex.message)
    }

    @Test
    fun `CycleDescription rejects blend with non-empty variant`() {
        val ex = assertThrows<IllegalArgumentException> {
            CycleDescription(
                prefix = CycleDescription.PREFIX_BLEND,
                compoundOrBlend = "x",
                variantOrTransformer = "foo",
                dose = 1.0,
                start = Duration.ZERO,
                duration = 1.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
        assertEquals("expected empty variant for blend", ex.message)
    }

    @Test
    fun `CycleDescription rejects transformer with empty variant`() {
        val ex = assertThrows<IllegalArgumentException> {
            CycleDescription(
                prefix = CycleDescription.PREFIX_TRANSFORMER,
                compoundOrBlend = "x",
                variantOrTransformer = "",
                start = Duration.ZERO,
                duration = 1.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
        assertEquals("expected non-empty transformer", ex.message)
    }

    @Test
    fun `CycleDescription rejects missing dose for compound`() {
        val ex = assertThrows<IllegalArgumentException> {
            CycleDescription(
                compoundOrBlend = "x",
                start = Duration.ZERO,
                duration = 1.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
        assertEquals("missing or nonpositive dose", ex.message)
    }

    @Test
    fun `CycleDescription rejects nonpositive dose for compound`() {
        val ex = assertThrows<IllegalArgumentException> {
            CycleDescription(
                compoundOrBlend = "x",
                dose = 0.0,
                start = Duration.ZERO,
                duration = 1.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
        assertEquals("missing or nonpositive dose", ex.message)
    }

    @Test
    fun `CycleDescription rejects negative dose for compound`() {
        val ex = assertThrows<IllegalArgumentException> {
            CycleDescription(
                compoundOrBlend = "x",
                dose = -5.0,
                start = Duration.ZERO,
                duration = 1.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
        assertEquals("missing or nonpositive dose", ex.message)
    }

    @Test
    fun `CycleDescription rejects missing dose for blend`() {
        val ex = assertThrows<IllegalArgumentException> {
            CycleDescription(
                prefix = CycleDescription.PREFIX_BLEND,
                compoundOrBlend = "x",
                start = Duration.ZERO,
                duration = 1.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
        assertEquals("missing or nonpositive dose", ex.message)
    }

    @Test
    fun `CycleDescription rejects negative start`() {
        val ex = assertThrows<IllegalArgumentException> {
            CycleDescription(
                compoundOrBlend = "x",
                dose = 1.0,
                start = (-1.0).toDuration(DurationUnit.DAYS),
                duration = 1.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
        assertEquals("negative start", ex.message)
    }

    @Test
    fun `CycleDescription rejects nonpositive duration`() {
        val ex = assertThrows<IllegalArgumentException> {
            CycleDescription(
                compoundOrBlend = "x",
                dose = 1.0,
                start = Duration.ZERO,
                duration = 0.0.toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
        assertEquals("nonpositive duration", ex.message)
    }

    @Test
    fun `CycleDescription rejects negative duration`() {
        val ex = assertThrows<IllegalArgumentException> {
            CycleDescription(
                compoundOrBlend = "x",
                dose = 1.0,
                start = Duration.ZERO,
                duration = (-1.0).toDuration(DurationUnit.DAYS),
                freqName = FrequencyName("every day")
            )
        }
        assertEquals("nonpositive duration", ex.message)
    }
}