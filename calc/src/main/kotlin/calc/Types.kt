package com.moshy.drugcalc.calc.calc

import com.moshy.drugcalc.common.checkValues
import com.moshy.drugcalc.types.calccommand.TransformerInfo
import com.moshy.drugcalc.types.dataentry.CompoundBase
import kotlinx.serialization.Serializable

/**
 * Single compound to evaluate a cycle of.
 *
 * Int is used instead of Long to ensure everything fits inside one contiguous array.
 *
 * @property compound compound name; nonempty
 * @property dose compound dose (null when [transformer] != null)
 * @property halfLife compound half life (null when [transformer] != null)
 * @property start start time
 * @property duration duration
 * @property freqs frequency values
 * @property transformer transformer name; null for regular compounds
 *
 * @see CycleCalculation
 */
@Serializable
data class DecodedCycle(
    val compound: String,
    val dose: Double? = null,
    val halfLife: Double? = null,
    val start: Int,
    val duration: Int,
    val freqs: List<Int>,
    val transformer: String? = null
) {
    init {
        require(compound.isNotEmpty()) {
            "empty compound"
        }
        require(!transformer.isNullOrEmpty()
                || (dose?.takeIf { it > 0.0 } != null && halfLife?.takeIf { it > 0.0 } != null)
        ) {
            "nonpositive dose or halfLife without transformer"
        }
        require(start >= 0) {
            "negative start"
        }
        require(duration > 0) {
            "nonpositive duration"
        }
        require(freqs.isNotEmpty()) {
            "empty freqs"
        }
        freqs.checkValues({ it > 0 }, { i, _ -> "freqs[$i]: nonpositive duration" })
    }
}

/** All the data needed to evaluate a cycle. */
@Serializable
data class CycleCalculation(
    val compounds: List<DecodedCycle>,
    val transformers: List<DecodedCycle> = emptyList()
) {
    init {
        require(compounds.isNotEmpty()) {
            "empty compounds"
        }
    }
}

internal typealias TransformerFn =
            (Map<CompoundBase, List<Double>>,
             String,
             Int,
             Int,
             List<Int>,
        ) -> List<RangeValue<Double, Int>>


internal data class TransformerEntry(
    val info: TransformerInfo,
    val evaluator: TransformerFn
)