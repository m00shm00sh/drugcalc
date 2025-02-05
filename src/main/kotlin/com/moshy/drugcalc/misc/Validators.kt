package com.moshy.drugcalc.misc

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.time.Duration

/**
 * For durations, check that the parameter evenly divides the receiver.
 */
internal fun Duration.isDivisibleBy(other: Duration): Boolean {
    val quot = this / other
    return floor(quot) == ceil(quot)
}
