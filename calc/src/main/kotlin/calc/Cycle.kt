package com.moshy.drugcalc.calc.calc

import com.moshy.drugcalc.types.calccommand.*
import com.moshy.drugcalc.types.dataentry.CompoundBase
import com.moshy.containers.circularIterator
import kotlin.math.*
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/** Evaluate a cycle of compounds and transformers on the compound results.
 *
 * @param cycle decoded cycle
 * @param config calculator config
 * @see CycleCalculation
 * @see Config
 * @throws IllegalArgumentException if a transformer is invalid
 */
fun evaluateDecodedCycle(cycle: CycleCalculation, config: Config): CycleResult {
    val (expandedCycles, lateHandling) = cycle

    val reducedCycles: Map<CompoundBase, List<Double>> =
        evaluateAndReduceCompoundCycles(expandedCycles, config)

    val transformVals: Map<String, List<RangeValue<Double, Int>>> =
        lateHandling.associate { (activeCompound, _, _, start, duration, freqs, transformerName) ->
            checkNotNull(transformerName)
            val transformerFn = requireNotNull(transformerEntries[transformerName]) {
                // if using DecodedCycle.withRawValues for builder, we can pass in an unvalidated transformer
                "transformer not found: $transformerName"
            }.evaluator
            "$activeCompound:$transformerName" to transformerFn(reducedCycles, activeCompound, start, duration, freqs)
        }

    return reducedCycles.entries.associate { (k, v) -> k.value to v.toXYList() } +
            transformVals.mapValues { it.value.toXYList() }

}

fun CycleResult.decodeTimeTickScaling(config: Config): DecodedCycleResult =
    mapValues { it.value.decodeTimeTickScaling(config.tickDuration) }


/**
 * Using [DecodedCycle] data from [cycles], evaluate each cycle given [Config] then return
 * m: Map<String, List<Double>>, where the key is the active compound name and the value is a list where each index is
 * time past zero and the value at that index is the total dose at that time.
 */
internal fun evaluateAndReduceCompoundCycles(cycles: List<DecodedCycle>, config: Config)
: Map<CompoundBase, List<Double>> {
    val allCycles: Map<CompoundBase, MutableList<OffsetList<Double>>> =
        buildMap {
            for ((activeCompound, dose, halfLife, start, duration, freqs) in cycles) {
                checkNotNull(halfLife != null)
                checkNotNull(dose != null)
                val correctedDose =
                    if (config.doLambdaDoseCorrection) {
                        /* The original formula with the lambda correction of dose scales halfLife to days instead of
                         * tickDurations. Rescale halfLife from @TimeTickScaled to days to reproduce "traditional"
                         * behavior.
                         */
                        val lambda = ln(2.0) /
                                (halfLife!! * (config.tickDuration / (1).toDuration(DurationUnit.DAYS)))
                        dose!! * min(lambda, 1.0)
                    } else
                        dose!!
                @Suppress("UNCHECKED_CAST") val cycleVals =
                    compoundCycle(
                        correctedDose, halfLife!!, duration, freqs,
                        config.cutoffMilligrams
                    )
                        .toOffsetList(start)
                getOrPut(CompoundBase(activeCompound)) { mutableListOf() }.add(cycleVals)
            }
        }

    return allCycles.mapValues { (_, cycles) -> reducer(cycles) }
}

/**
 * Evaluate a cycle of one compound given [dose], [halfLife], [duration], [redosingFrequencies] and generation params.
 *
 * For testing, it is adequate to call [evaluateAndReduceCompoundCycles] with just one cycle having start==0.
 *
 * Returns an empty list when [dose] is 0.
 *
 * @param dose dosage, in milligrams; positive
 * @param halfLife compound half life, in time ticks
 * @param duration
 * @param cutoff minimum dose after which to stop evaluating a single administration
 */
private fun compoundCycle(
    dose: Double,
    halfLife: Double,
    duration: Int,
    redosingFrequencies: List<Int>,
    cutoff: Double
): List<Double> {
    // evaluate exponential decay until the released dose is below the cutoff
    val lastRelease: Int = ceil(releaseInv(dose, cutoff, halfLife)).toInt()

    // evaluate the values for one iteration and then reference them during shift and sum
    val oneIter = List(lastRelease) { i -> release(dose, i, halfLife) }

    val toReduce = buildList {
        val freqIter = redosingFrequencies.circularIterator()
        var timeAfterStart = 0
        while (timeAfterStart <= duration) {
            add(OffsetList(timeAfterStart, oneIter))
            timeAfterStart += freqIter.next()
        }
    }

    return reducer(toReduce, toReduce.last().offset + lastRelease)
}

/** Evaluate mg_released in mg of a compound given dose, time after administration, and compound info.
 *
 * @param dose dosage, in milligrams; positive
 * @param t time, in time ticks, after administration; nonnegative
 * @param halfLife compound half life, in time ticks
 * @return mg released [t] time ticks after administration of [dose] mg of compound given [halfLife]
 */
private fun release(dose: Double, t: Int, halfLife: Double): Double {
    val lambda = ln(2.0) / halfLife
    return dose * exp(-t * lambda)
}

/** Calculate inverse of release for `t` parameter.
 *
 * Let R(t) = release(dose, t, compound).
 * This function will solve for some t = R_inv(release) such that R(R_inv(release)) == release.
 *
 * @param dose dosage, in milligrams; positive
 * @param mgReleased dosage released at `t` to be found, in milligrams; positive
 * @param halfLife compound half life, in time ticks
 * @return time in time ticks after administration such that active dose is [mgReleased]
 */
private fun releaseInv(dose: Double, mgReleased: Double, halfLife: Double): Double {
    val lambda: Double = ln(2.0) / halfLife
    return ln(mgReleased / dose) / -lambda
}