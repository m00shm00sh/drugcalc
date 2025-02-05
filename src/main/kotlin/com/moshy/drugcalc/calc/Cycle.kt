@file:Suppress("NOTHING_TO_INLINE")

package com.moshy.drugcalc.calc

import com.moshy.drugcalc.calcdata.DataStore
import com.moshy.drugcalc.misc.XYList
import com.moshy.drugcalc.internaltypes.RangeValue
import com.moshy.drugcalc.misc.toXYList
import com.moshy.containers.circularIterator
import com.moshy.drugcalc.misc.DecodedXYList
import com.moshy.drugcalc.misc.decodeTimeTickScaling
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.time.DurationUnit
import kotlin.time.toDuration

/**
 * Evaluate a cycle of multiple compounds.
 *
 * @param cycleCompounds list of cycles to evaluate
 * @param data session data store
 * @return a map {activeCompound to release}, where release is a list of x values and y values
 * @see CycleDescription
 * @throws IllegalArgumentException if a transformer is specified multiple times
 */
@Suppress("unused")
fun multiCycle(cycleCompounds: List<CycleDescription>, data: DataStore, config: Config): Map<String, DecodedXYList> {
    val decoded = decodeCycles(cycleCompounds, data, config)
    return evaluateDecodedCycle(decoded, config).decodeTimeTickScaling(config)
}

internal fun evaluateDecodedCycle(cycle: CycleCalculation, config: Config): Map<String, XYList> {
    val (expandedCycles, lateHandling) = cycle

    val reducedCycles: Map<String, @TimeTickIndexed List<Double>> =
        evaluateAndReduceCompoundCycles(expandedCycles, config)

    val transformVals: Map<String, List<RangeValue<Double, @TimeTickScaled Int>>> =
        lateHandling.associate { (activeCompound, _, _, start, duration, freqs, transformerName) ->
            checkNotNull(transformerName)
            val transformerFn = requireNotNull(transformers[transformerName]) {
                // if using DecodedCycle.withRawValues for builder, we can pass in an unvalidated transformer
                "transformer not found: $transformerName"
            }.second
            val cycleVals = requireNotNull(reducedCycles[activeCompound]) {
                "base <$activeCompound> not evaluated prior to transformer <$transformerName>"
            }
            require(start < cycleVals.size) {
                "the transformer $activeCompound:$transformerName is invalid because there is no data for it to transform"
            }
            "$activeCompound:$transformerName" to transformerFn(cycleVals, start, duration, freqs)
        }

    return reducedCycles.mapValues { it.value.toXYList() } +
            transformVals.mapValues { it.value.toXYList() }

}

internal fun Map<String, XYList>.decodeTimeTickScaling(config: Config): Map<String, DecodedXYList> =
    mapValues { it.value.decodeTimeTickScaling(config.tickDuration) }

/** Decode a list of [CycleDescription]s given a [DataStore] to a [CycleCalculation].*/
internal fun decodeCycles(compounds: List<CycleDescription>, data: DataStore, config: Config): CycleCalculation {
    val compoundBuilder = DecodedCycle.WithConfig(config)

    val transformerCycles: MutableList<DecodedCycle> = mutableListOf()
    val compoundCycles = buildList {
        for ((compoundName, dose, start, duration, freqName) in compounds) {
            val freqVals =
                requireNotNull(data.frequencies[freqName]) {
                    "no match for frequency with name \"$freqName\""
                }
            val common =
                compoundBuilder.withCommon(start, duration, freqVals)
            run {
                data.compounds[compoundName]?.let {
                    add(
                        DecodedCycle.build(
                            common,
                            compoundBuilder.withCompound(it.activeCompound, dose * it.pctActive, it.halfLife)
                        )
                    )
                    // FIXME: see https://youtrack.jetbrains.com/issue/KT-1436
                    return@run
                }
                data.blends[compoundName]?.components?.let {
                    for ((multiplier, cInfo) in it) {
                        add(
                            DecodedCycle.build(
                                common,
                                compoundBuilder.withBlendComponent(dose * multiplier, cInfo)
                            )
                        )
                    }
                    // FIXME: see https://youtrack.jetbrains.com/issue/KT-1436
                    return@run
                }
                // TODO: move the parser here
                data.getTransformer(compoundName)?.let { (active, transformer) ->
                    // the active compound might exist in DataStore but it has not been specified in this cycle
                    requireNotNull(find { active == (it as DecodedCycle).active }) {
                        "transformer \"$transformer\" refers to compound \"$active\"" +
                        ", which was not directly or otherwise specified"
                    }
                    val transformerCompound =
                        DecodedCycle.build(
                            common,
                            compoundBuilder.withTransformer(active, transformer)
                        )
                    require(transformerCompound !in transformerCycles) {
                        "transformer <$transformerCompound> already specified"
                    }
                    transformerCycles.add(transformerCompound)
                    // FIXME: see https://youtrack.jetbrains.com/issue/KT-1436
                    return@run
                }
                require(false) { "no match for compound with name or spec \"$compoundName\"" }
            }
        }
    }
    return CycleCalculation(compoundCycles, transformerCycles)
}

/**
 * Using [DecodedCycle] data from [cycles], evaluate each cycle given [Config] then return
 * m: Map<String, List<Double>>, where the key is the active compound name and the value is a list where each index is
 * time past zero and the value at that index is the total dose at that time.
 */
internal fun evaluateAndReduceCompoundCycles(
    cycles: List<DecodedCycle>,
    config: Config,
): Map<String, @TimeTickIndexed List<Double>> {
    val allCycles: Map<String, MutableList<OffsetList<Double>>> =
        buildMap {
            for ((activeCompound, dose, halfLife, start, duration, freqs) in cycles) {
                check(dose > 0.0 && duration > 0) // should be unreachable
                val correctedDose =
                    if (config.doLambdaDoseCorrection) {
                        /* The original formula with the lambda correction of dose scales halfLife to days instead of
                         * tickDurations. Rescale halfLife from @TimeTickScaled to days to reproduce "traditional"
                         * behavior.
                         */
                        val lambda = ln(2.0) /
                                (halfLife * (config.tickDuration / (1).toDuration(DurationUnit.DAYS)))
                        dose * min(lambda, 1.0)
                    } else
                        dose
                val cycleVals =
                    compoundCycle(correctedDose, halfLife, duration, freqs, config.cutoff)
                        .toOffsetList(start)
                getOrPut(activeCompound) { mutableListOf() }.add(cycleVals)
            }
        }

    return  allCycles.mapValues { (_, cycles) -> reducer(cycles) }
}

/**
 * Evaluate a cycle of one compound given [dose], [halfLife], [duration], [redosingFrequencies] and generation params.
 *
 * For testing, it is adequate to call [evaluateAndReduceCompoundCycles] with just one cycle having start==0.
 *
 * Returns an empty list when [dose] is 0.
 *
 * @param cutoff minimum dose after which to stop evaluating a single administration
 */
private fun compoundCycle(
    dose: @Positive Double,
    halfLife: @Positive @TimeTickScaled Double,
    duration: @Positive @TimeTickScaled Int,
    redosingFrequencies: List<@Positive @TimeTickScaled Int>,
    cutoff: @Positive Double
): @TimeTickIndexed List<Double> {
    // evaluate exponential decay until the released dose is below the cutoff
    val lastRelease: @Positive @TimeTickScaled Int = ceil(releaseInv(dose, cutoff, halfLife)).toInt()

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
 * @param dose dosage, in milligrams
 * @param t time, in time ticks, after administration
 * @param halfLife compound half life, in time ticks
 * @return mg released [t] time ticks after administration of [dose] mg of compound given [halfLife]
 */
private inline fun release(
    dose: @Positive Double,
    t: @Positive @TimeTickScaled Int,
    halfLife: @Positive @TimeTickScaled Double
): @Positive Double {
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
 * @return time in time ticks after administration such that active dose is {mgReleased}
 */
private inline fun releaseInv(
    dose: @Positive Double,
    mgReleased: @Positive Double,
    halfLife: @Positive @TimeTickScaled Double
): @Positive @TimeTickScaled Double {
    val lambda: Double = ln(2.0) / halfLife
    return ln(mgReleased / dose) / -lambda
}