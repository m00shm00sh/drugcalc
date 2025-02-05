package com.moshy.drugcalc.repo

import com.moshy.drugcalc.io.FullData
import com.moshy.drugcalc.io.FullDiff
import com.moshy.drugcalc.io.FullDiffData
import kotlin.math.absoluteValue
import kotlinx.datetime.Instant

internal inline fun FullData.allProps(predicate: (Map<String, Any>) -> Boolean): Boolean =
    listOf(compounds, blends, frequencies).all(predicate)

internal inline fun <R> FullDiffData.mapAll(predicate: (FullDiff<out Any>) -> R): List<R> =
    listOf(compounds, blends, frequencies).map(predicate)

internal fun areInstantsEqual(time1: Instant, time2: Instant, tolerance: Long): Boolean {
    val timeDelta = (time1.toEpochMilliseconds() - time2.toEpochMilliseconds()).absoluteValue
    println("RepoTest: TIMING: $timeDelta (tol=$tolerance)")
    return timeDelta <= tolerance
}