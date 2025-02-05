package com.moshy.drugcalc.calcdata

import com.moshy.drugcalc.calc.Positive
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Duration


/** Compound info.
 * @property halfLife half-life
 * @property pctActive percent active dose; value in (0.0, 1.0]
 * @property activeCompound active compound
 */
@Serializable
data class CompoundInfo(
    val halfLife: @Contextual @Positive Duration,
    val pctActive: @Positive Double = 1.0,
    val activeCompound: String = "" // this can be set in a .copy() so don't fail on empty string
) {
    init {
        require(halfLife.isPositive()) {
            "nonpositive halfLife"
        }
        require(pctActive > 0.0 && pctActive <= 1.0) {
            "pctActive not in (0.0, 1.0]"
        }
    }
}