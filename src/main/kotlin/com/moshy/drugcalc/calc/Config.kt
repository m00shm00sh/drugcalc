package com.moshy.drugcalc.calc

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/** Calculator configurables. */
@Serializable
data class Config(
    val tickDuration: @Contextual @Positive Duration,
    val cutoff: @Positive Double,
    /* The canonical iteration of the formula online has the lambda correction of *dose* and not just time;
     * if the mathematically correct value is desired, set this to False.
     */
    val doLambdaDoseCorrection: Boolean

) {
    init {
        require(tickDuration.isPositive()) {
            "nonpositive tickDuration"
        }
        /* TODO: when negative cutoff for "N half lives" instead of minimum dose is implemented, change this from
         *       `> 0.0` to `> 0 || isNegativeWholeNumber`
         */
        require(cutoff > 0.0) {
            "nonpositive cutoff"
        }
    }
}