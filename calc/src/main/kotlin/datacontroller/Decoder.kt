package com.moshy.drugcalc.calc.datacontroller

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

    private val blendSums = names.blends.mapValues { (_, v) -> v.values.sum() }

    private val transformerFreqs = generateTransformerFreqs(config) +
            transformerFreqs.mapKeys { (k, _) -> k.clean() }

    /** Decode [cycle].
     * @throw IllegalStateException on name resulution because invalid [names] was supplied
     */
    fun decode(cycle: List<CycleDescription>): CycleCalculation {
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
                            add(
                                CycleDescriptionWithValidatedName(
                                    cName,
                                    compoundOrTransformer.dose!! * (cDose / blendSum),
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
                                compoundOrTransformer
                            )
                        )
                    }
                }
            }
        }

        // pass 2: decode compounds
        val decodedCycles = buildList {
            for ((cName, dose, start, duration, fName) in expandedCycles) {
                // if this throws, some expansion transformation let an unvalidated compound name slip by
                val cInfo = checkNotNull(names.compounds[cName]) {
                    "unresolved compound $cName"
                }
                val fVals = checkNotNull(names.frequencies[fName]) {
                    "unresolved frequency $fName"
                }
                val (tcStart, tcDuration, tcFreqVals) = transcodeTimes(start, duration, fVals)
                add(
                    DecodedCycle(
                        cName.compound.value,
                        dose * cInfo.pctActive, cInfo.halfLife / tickDuration,
                        tcStart, tcDuration, tcFreqVals
                    )
                )
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
                    ?: throw IllegalArgumentException("the frequency $fName did not resolve to a sequence of durations")
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
    val freqName: FrequencyName
) {
    constructor(compoundName: CompoundName, dose: Double, durations: CycleDescription)
            : this(compoundName, dose, durations.start, durations.duration, durations.freqName)
}

