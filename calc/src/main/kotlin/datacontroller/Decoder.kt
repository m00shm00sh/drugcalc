package com.moshy.drugcalc.calc.datacontroller

import com.moshy.containers.circularIterator
import com.moshy.drugcalc.calc.calc.*
import com.moshy.drugcalc.types.calccommand.*
import com.moshy.drugcalc.types.dataentry.*
import kotlin.collections.iterator
import kotlin.math.*
import kotlin.time.Duration

/**
 * Decodes a symbolic cycle given [config], [names], and [transformerFreqs] into something
 * [evaluateDecodedCycle] can work with.
 */
class Decoder(
    config: Config,
    private val names: Data,
    /** frequency names reserved for transformers, like "." for every time tick. */
    transformerFreqs: Map<FrequencyName, FrequencyValue> = emptyMap()
) {
    private val tickDuration = config.tickDuration
    private val cutoffMilligrams = config.cutoffMilligrams

    private val blendSums = names.blends.mapValues { (_, v) -> v.values.sum() }

    private val transformerFreqs = generateTransformerFreqs(config) +
            transformerFreqs.mapKeys { (k, _) -> k.clean() }

    /** Decode [cycle] into a map of group id to [CycleCalculation].
     * Each [CycleDescription] is routed to the list for its [CycleDescription.group] value
     * (defaulting to 0) and decoded independently.
     * @throw IllegalStateException on name resulution because invalid [names] was supplied
     */
    fun decode(cycle: List<CycleDescription>): Map<Int, CycleCalculation> {
        return cycle
            .groupBy { it.group }
            .mapValues { (_, groupCycles) ->
                decodeGroup(groupCycles)
            }
    }

    private fun decodeGroup(cycle: List<CycleDescription>): CycleCalculation {
        val transformerCycles: MutableList<CycleDescription> = mutableListOf()

        // pass 1: expand blends and resolve names
        val expandedCycles = buildList {
            for (compoundOrTransformer in cycle) {
                val name = compoundOrTransformer.compoundOrBlend
                val variant = compoundOrTransformer.variantOrTransformer
                when (compoundOrTransformer.prefix) {
                    CycleDescription.PREFIX_TRANSFORMER -> {
                        transformerCycles.add(compoundOrTransformer)
                    }

                    CycleDescription.PREFIX_BLEND -> {
                        val bn = BlendName(name).clean()
                        val entry = names.blends[bn]
                        checkNotNull(entry) {
                            "unresolved blend $bn"
                        }
                        // blendSums is generated from names.blends so assert non-null
                        val blendSum = blendSums[bn]!!
                        for ((cName, cDose) in entry) {
                            check(cName in names.compounds) {
                                "the blend $name contains an unresolved compound $cName"
                            }
                            val scale = cDose / blendSum
                            val doseRamp = compoundOrTransformer.doseRamp?.let {
                                RampedDose(it.addDose * scale, it.addFreqName)
                            }
                            add(
                                CycleDescriptionWithValidatedName(
                                    cName,
                                    compoundOrTransformer.dose!! * scale,
                                    doseRamp,
                                    compoundOrTransformer
                                )
                            )
                        }
                    }

                    CycleDescription.PREFIX_COMPOUND -> {
                        val cn = CompoundName(CompoundBase(name), variant).clean()
                        check(cn in names.compounds) {
                            "unresolved compound $cn"
                        }
                        add(
                            CycleDescriptionWithValidatedName(
                                cn,
                                compoundOrTransformer.dose!!,
                                compoundOrTransformer.doseRamp,
                                compoundOrTransformer
                            )
                        )
                    }
                }
            }
        }

        // pass 2: decode compounds
        val decodedCycles = buildList {
            for ((cName, dose, start, duration, fName, doseRamp) in expandedCycles) {
                // if this throws, some expansion transformation let an unvalidated compound name slip by
                val cInfo = checkNotNull(names.compounds[cName]) {
                    "unresolved compound $cName"
                }
                if (doseRamp == null) {
                    val fVals = resolveFrequency(fName)
                    val (tcStart, tcDuration, tcFreqVals) = transcodeTimes(start, duration, fVals)
                    add(
                        DecodedCycle(
                            cName.compound.value,
                            dose * cInfo.pctActive, cInfo.halfLife / tickDuration,
                            tcStart, tcDuration, tcFreqVals
                        )
                    )
                } else {
                    addAll(decodeRampedCycle(cName, dose, start, duration, fName, doseRamp, cInfo))
                }
            }
        }

        require(decodedCycles.isNotEmpty()) {
            "cycle doesn't have any compounds"
        }

        // pass 3: decode transformers
        val decodedTransformerCycles = buildList {
            for ((prefix, cName, transformer, _, start, duration, fName) in transformerCycles) {
                // the strings were untouched in the compounds & blends pass so clean now
                val cName = cName.clean()
                val transformer = transformer.clean()

                check(prefix == CycleDescription.PREFIX_TRANSFORMER) {
                    "unexpected non-transformer entry"
                }
                val fName = fName.clean()
                val fVals = names.frequencies[fName]
                    ?: transformerFreqs[fName]
                    ?: throw IllegalArgumentException("unresolved frequency $fName")
                val (tcStart, tcDuration, tcFreqVals) = transcodeTimes(start, duration, fVals)
                add(
                    DecodedCycle(
                        cName, dose = null, halfLife = null, tcStart, tcDuration, tcFreqVals, transformer
                    )
                )
            }
        }

        return CycleCalculation(decodedCycles, decodedTransformerCycles)
    }

    private fun resolveFrequency(name: FrequencyName): FrequencyValue =
        checkNotNull(names.frequencies[name] ?: names.frequencies[name.clean()]) {
            "unresolved frequency $name"
        }

    /**
     * Expand a ramped cycle into a list of constant-dose [DecodedCycle]s.
     *
     * The expansion preserves the original administration times: each run of consecutive
     * administrations having the same dose becomes one [DecodedCycle] whose frequency values are
     * the exact deltas between those administration times.
     */
    private fun decodeRampedCycle(
        compoundName: CompoundName,
        baseDose: Double,
        start: Duration,
        duration: Duration,
        freqName: FrequencyName,
        doseRamp: RampedDose,
        cInfo: CompoundInfo,
    ): List<DecodedCycle> {
        val tcStart = start.transcode("start")
        val tcDuration = duration.transcode("duration")
        val freqTicks = resolveFrequency(freqName).mapIndexed { i, v ->
            v.transcode("freqsVals[$i]")
        }
        val doseRampFreqTicks = resolveFrequency(doseRamp.addFreqName).mapIndexed { i, v ->
            v.transcode("doseRamp.freqsVals[$i]")
        }

        val scaledBase = baseDose * cInfo.pctActive
        val scaledAdd = doseRamp.addDose * cInfo.pctActive

        // administration times, relative to start
        val administrationTimes = buildList {
            var t = 0
            val freqIter = freqTicks.circularIterator()
            while (t <= tcDuration) {
                add(t)
                t += freqIter.next()
            }
        }

        data class Group(val k: Int, val times: MutableList<Int> = mutableListOf())

        val groups = mutableListOf<Group>()
        var completedIntervals = 0
        var nextDoseRampBoundary = doseRampFreqTicks.first()
        var doseRampFreqIdx = 0
        for (t in administrationTimes) {
            while (nextDoseRampBoundary <= t) {
                ++completedIntervals
                ++doseRampFreqIdx
                nextDoseRampBoundary += doseRampFreqTicks[
                    doseRampFreqIdx % doseRampFreqTicks.size
                ]
            }
            val dose = scaledBase + completedIntervals * scaledAdd
            // DecodedCycle requires a positive dose; doses at or below the cutoff produce no data.
            if (dose <= cutoffMilligrams) {
                // A nonpositive ramp step is monotonic, so once the dose reaches zero (or the
                // cutoff) it can never become positive again. Stop instead of emitting
                // zero/negative doses.
                if (scaledAdd <= 0.0)
                    break
                continue
            }
            val last = groups.lastOrNull()
            if (last != null && last.k == completedIntervals)
                last.times.add(t)
            else
                groups.add(Group(completedIntervals).apply { times.add(t) })
        }

        val halfLife = cInfo.halfLife / tickDuration
        return groups.map { (k, times) ->
            val groupDose = scaledBase + k * scaledAdd
            val groupStart = tcStart + times.first()
            if (times.size == 1) {
                // A single dose still needs a positive duration and frequency; [2] prevents a redose.
                DecodedCycle(
                    compoundName.compound.value, groupDose, halfLife,
                    groupStart, 1, listOf(2)
                )
            } else {
                DecodedCycle(
                    compoundName.compound.value, groupDose, halfLife,
                    groupStart,
                    times.last() - times.first(),
                    times.zipWithNext { a, b -> b - a }
                )
            }
        }
    }

    private data class TranscodedDurations(
        val start: Int,
        val duration: Int,
        val freqs: List<Int>,
    )

    private fun transcodeTimes(
        start: Duration,
        duration: Duration,
        freqs: List<Duration>
    ): TranscodedDurations =
        TranscodedDurations(
            start.transcode("start"),
            duration.transcode("duration"),
            freqs.mapIndexed { i, v -> v.transcode("freqsVals[$i]") }
        )

    private fun Duration.transcode(context: String): Int {
        val div = this / tickDuration
        val contextStr by lazy { "$context=$this" }
        require(floor(div) == ceil(div)) {
            "$contextStr not compatible with tickDuration=$tickDuration"
        }
        require(div.absoluteValue < Int.MAX_VALUE) {
            "$contextStr out of range for Int"
        }
        return div.toInt()
    }

}

/** Intermediate step in decoding - the name has been validated but the durations have yet to be handled. */
private data class CycleDescriptionWithValidatedName(
    val compoundName: CompoundName,
    val dose: Double,
    val start: Duration,
    val duration: Duration,
    val freqName: FrequencyName,
    val doseRamp: RampedDose?,
) {
    constructor(
        compoundName: CompoundName,
        dose: Double,
        doseRamp: RampedDose?,
        durations: CycleDescription
    ) : this(
        compoundName, dose, durations.start, durations.duration, durations.freqName, doseRamp
    )
}

