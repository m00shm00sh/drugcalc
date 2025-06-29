package com.moshy.drugcalc.types

import com.moshy.drugcalc.commontest.CheckArg
import com.moshy.drugcalc.types.calccommand.CycleDescription
import com.moshy.drugcalc.types.dataentry.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.time.*

internal class DataTypesTest {
    @Test
    fun `test Data isNotEmpty`() {
        assertFalse(Data().isNotEmpty())
        assertTrue(
            Data(
                compounds = mapOf(
                    CompoundName(CompoundBase("a")) to CompoundInfo(1.toDuration(DurationUnit.DAYS))
                )
            ).isNotEmpty())
        assertTrue(
            Data(
                blends = mapOf(
                    BlendName("a") to BlendValue(
                        mapOf(
                            CompoundName(CompoundBase("b")) to 1.0,
                            CompoundName(CompoundBase("c")) to 1.0
                        )
                    )
                )
            ).isNotEmpty())
        assertTrue(
            Data(
                frequencies = mapOf(
                    FrequencyName("a") to FrequencyValue(listOf(1.toDuration(DurationUnit.DAYS)))
                )
            ).isNotEmpty())
    }

    @Test
    fun `test CompoundName`() {
        assertDoesNotThrow {
            CompoundName(CompoundBase("a"), ".1+1=2")
        }
    }

    @Test
    fun `test CompoundName compare`() {
        val a = CompoundName(CompoundBase("a"), "b")
        val b = CompoundName(CompoundBase("a"), "b")
        val ac = CompoundName(CompoundBase("a"), "c")
        val bb = CompoundName(CompoundBase("b"), "b")
        val b0 = CompoundName("b")
        assertNotSame(a, b)
        assertEquals(a, b)
        assertNotEquals(ac, a)
        assertNotEquals(bb, a)
        assertNotEquals(b0, bb)
        assertTrue(a < ac)
        assertTrue(a < bb)
        assertTrue(b0 > b)
    }

    @Test
    fun `test CompoundBase`() {
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "empty").invoke {
            CompoundBase("")
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "starts with '.'").invoke {
            CompoundBase(CycleDescription.PREFIX_BLEND)
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "contains '='").invoke {
            CompoundBase("a=b")
        }
        assertDoesNotThrow {
            CompoundBase("hello, it crowd?")
        }
    }

    @Test
    fun `test CompoundBase compare`() {
        val a = CompoundBase("a")
        val b = CompoundBase("a")
        val c = CompoundBase("b")
        assertNotSame(a, b)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `test CompoundInfo`() {
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "nonpositive halfLife").invoke {
            CompoundInfo(((-1).toDuration(DurationUnit.DAYS)))
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "nonpositive halfLife").invoke {
            CompoundInfo(0.toDuration(DurationUnit.DAYS))
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "pctActive").invoke {
            CompoundInfo(1.toDuration(DurationUnit.DAYS), pctActive = 0.0)
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "pctActive").invoke {
            CompoundInfo(1.toDuration(DurationUnit.DAYS), pctActive = 1.1)
        }
        assertDoesNotThrow {
            CompoundInfo(1.toDuration(DurationUnit.DAYS))
        }
        assertDoesNotThrow {
            CompoundInfo(1.toDuration(DurationUnit.DAYS), pctActive = 0.1)
        }
        assertDoesNotThrow {
            CompoundInfo(1.toDuration(DurationUnit.DAYS), pctActive = 1.0)
        }
    }

    @Test
    fun `test BlendName`() {
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "empty").invoke {
            BlendName("")
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "starts with '.'").invoke {
            BlendName(CycleDescription.PREFIX_BLEND)
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "contains '='").invoke {
            BlendName("a=b")
        }
        assertDoesNotThrow {
            BlendName("where's waldo?")
        }
    }

    @Test
    fun `test BlendName compare`() {
        val a = BlendName("a")
        val b = BlendName("a")
        val c = BlendName("b")
        assertNotSame(a, b)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `test BlendValue`() {
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "2+ components required").invoke {
            BlendValue(emptyMap())
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "2+ components required"
        ).invoke {
            BlendValue(mapOf(CompoundName(CompoundBase("a")) to 1.0))
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "non-positive component value 0.0 for key CompoundName(b)"
        ).invoke {
            BlendValue(
                mapOf(
                    CompoundName(CompoundBase("a")) to 1.0,
                    CompoundName(CompoundBase("b")) to 0.0,
                )
            )
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "non-positive component value -1.0 for key CompoundName(b)"
        ).invoke {
            BlendValue(
                mapOf(
                    CompoundName(CompoundBase("a")) to 1.0,
                    CompoundName(CompoundBase("b")) to -1.0,
                )
            )
        }
       assertDoesNotThrow {
           BlendValue(
               mapOf(
                   CompoundName(CompoundBase("a")) to 1.0,
                   CompoundName(CompoundBase("a"), "b") to 2.0,
               )
           )
       }
    }

    @Test
    fun `test FrequencyName`() {
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "empty").invoke {
            FrequencyName("")
        }
        assertDoesNotThrow {
            FrequencyName("here's johnny!")
        }
    }

    @Test
    fun `test FrequencyName compare`() {
        val a = FrequencyName("a")
        val b = FrequencyName("a")
        val c = FrequencyName("b")
        assertNotSame(a, b)
        assertEquals(a, b)
        assertNotEquals(a, c)
    }

    @Test
    fun `test FrequencyValue`() {
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "empty list").invoke {
            FrequencyValue(emptyList())
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "1: nonpositive duration 0").invoke {
            FrequencyValue(
                listOf(
                    1.toDuration(DurationUnit.DAYS),
                    0.toDuration(DurationUnit.DAYS)
                )
            )
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "1: nonpositive duration -1d").invoke {
            FrequencyValue(
                listOf(
                    1.toDuration(DurationUnit.DAYS),
                    (-1).toDuration(DurationUnit.DAYS)
                )
            )
        }
        assertDoesNotThrow {
            FrequencyValue(
                listOf(
                    1.toDuration(DurationUnit.DAYS),
                    1.toDuration(DurationUnit.DAYS)
                )
            )
        }
    }
}
