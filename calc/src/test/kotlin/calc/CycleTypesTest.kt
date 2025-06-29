package com.moshy.drugcalc.calc.calc


import com.moshy.drugcalc.commontest.CheckArg
import com.moshy.ProxyMap
import com.moshy.proxymap.plus
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

internal class CycleTypesTest {
    @Test
    fun `test DecodedCycle`() {
        assertDoesNotThrow("transformer") {
            DecodedCycle(compound = "a", start = 0, duration = 1, freqs = listOf(1), transformer = "b")
        }
        val dcCompound = assertDoesNotThrow("compound") {
            DecodedCycle(compound = "a", dose = 1.0, halfLife = 1.0, start = 0, duration = 1, freqs = listOf(1))
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "empty compound").invoke {
            dcCompound.withFields("compound" to "")
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "negative start").invoke {
            dcCompound.withFields("start" to -1)
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "nonpositive duration").invoke {
            dcCompound.withFields("duration" to 0)
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "nonpositive duration").invoke {
            dcCompound.withFields("duration" to -1)
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "empty freqs").invoke {
            dcCompound.withFields("freqs" to emptyList<Int>())
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "freqs[1]: nonpositive duration").invoke {
            dcCompound.withFields("freqs" to listOf(1, 0))
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "freqs[1]: nonpositive duration").invoke {
            dcCompound.withFields("freqs" to listOf(1, -1))
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "nonpositive dose or halfLife without transformer").invoke {
            dcCompound.withFields(
                "dose" to null,
                "halfLife" to null
            )
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "nonpositive dose or halfLife without transformer").invoke {
            dcCompound.withFields("halfLife" to null)
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "nonpositive dose or halfLife without transformer").invoke {
            dcCompound.withFields("halfLife" to 0.0)
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "nonpositive dose or halfLife without transformer").invoke {
            dcCompound.withFields("dose" to null)
        }
        CheckArg.throws<IllegalArgumentException, _>(Unit,
            "nonpositive dose or halfLife without transformer").invoke {
            dcCompound.withFields("dose" to 0.0)
        }
    }

    companion object {
        private fun DecodedCycle.withFields(vararg fieldKVs: Pair<String, Any?>) =
            this + ProxyMap<DecodedCycle>(*fieldKVs)
    }
}