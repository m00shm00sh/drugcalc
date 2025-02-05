package com.moshy.drugcalc.calc

import kotlin.time.Duration
import kotlinx.serialization.Serializable

import com.moshy.drugcalc.calcdata.CompoundInfo
import com.moshy.drugcalc.misc.isDivisibleBy
import kotlinx.serialization.Contextual
import kotlin.math.absoluteValue
import kotlin.math.ceil

/* These annotations are only for summary documentation. There is no active compiler plugin to convert these
 * into value classes.
 */
@Target(AnnotationTarget.TYPE)
internal annotation class Positive
@Target(AnnotationTarget.TYPE)
internal annotation class Nonnegative
@Target(AnnotationTarget.TYPE)
internal annotation class TimeTickScaled
@Target(AnnotationTarget.TYPE)
internal annotation class TimeTickIndexed

/** Describes a cycle of a compound.
 *
 * @property compoundName compound name
 * @property dose dose, in milligrams; value ignored if [compoundName] is a transformer
 * @property start start time
 * @property duration duration
 * @property freqName frequency name; must exist in {frequencies}
 *
 * @throws IllegalArgumentException if values are invalid
 */
@Serializable
data class CycleDescription(
    val compoundName: String,
    val dose: @Positive Double,
    val start: @Contextual @Nonnegative Duration,
    val duration: @Contextual @Positive Duration,
    val freqName: String
)
{
    init {
        duration.isPositive()
        require(compoundName.isNotEmpty()) {
            "empty compoundName"
        }
        require(dose >= 0.0) {
            "negative dose"
        }
        require(!start.isNegative()) {
            "negative start"
        }
        require(duration.isPositive()) {
            "nonpositive duration"
        }
        require(freqName.isNotEmpty()) {
            "empty freqName"
        }
    }
}

/**
 * Single compound to evaluate a cycle of.
 *
 * Constructor is private to require use of incremental builders.
 *
 * The following properties are scaled against [Config.tickDuration]:
 * [halfLife], [start], [duration], [freqs]
 *
 * Int is used instead of Long to ensure everything fits inside one contiguous array.
 *
 * @property active compound name; nonempty
 * @property dose compound dose (unused when [transformer] != null)
 * @property halfLife compound half life (unused when [transformer] != null)
 * @property start start time
 * @property duration duration
 * @property freqs frequency values
 * @property transformer transformer name; null for regular compounds
 *
 * @see withRawValues
 * @see CycleCalculation
 */
@ConsistentCopyVisibility
@Serializable
data class DecodedCycle private constructor (
    val active: String,
    val dose: @Positive Double = 0.0,
    val halfLife: @Positive @TimeTickScaled Double = 0.0,
    val start: @Nonnegative @TimeTickScaled Int,
    val duration: @Positive @TimeTickScaled Int,
    val freqs: List<@Positive @TimeTickScaled Int>, // each element is number of tickDuration's to increment by
    val transformer: String? = null
) {
    fun interface DCBuilder {
        fun incrementallyBuild(decodedCycle: DecodedCycle): DecodedCycle
    }

    /**
     * Begin building given value(s).
     */
    class WithConfig(c: Config) {
        private val tickDuration = c.tickDuration

        private fun Duration.divCheck(context: String) =
            require(this.isDivisibleBy(tickDuration)) {
                "$context=$this not compatible with tickDuration=$tickDuration"
            }

        /**
         * Validate and save common (to both compound and transformer) values.
         */
        fun withCommon(
            start: @Positive Duration,
            duration: @Positive Duration,
            freqVals: List<@Positive Duration>
        ): DCBuilder {
            val newStart = start.apply {
                require(!isNegative()) {
                    "negative start"
                }
                divCheck("start")
            }.run {
                (this / tickDuration).tryToInt("start")
            }
            val newDuration = duration.apply {
                require(isPositive()) {
                    "nonpositive duration"
                }
                divCheck("duration")
            }.run {
                ceil(this / tickDuration).tryToInt("duration")
            }
            val newFreqVals = freqVals.run {
                require(isNotEmpty()) {
                    "empty freqVals"
                }
                withIndex().map { (i, fVal) ->
                    fVal.divCheck("freqVals[$i]")
                    require(fVal.isPositive()) {
                        "nonpositive freqVals[$i]"
                    }
                    (fVal / tickDuration).tryToInt("freqVal[$i]")
                }
            }
            return DCBuilder {
                it.copy(
                    start = newStart,
                    duration = newDuration,
                    freqs = newFreqVals
                )
            }
        }

        fun withCompound(activeCompound: String, dose: @Positive Double, halfLife: @Positive Duration): DCBuilder {
            require(activeCompound.isNotEmpty()) {
                "empty activeCompound"
            }
            require(dose > 0.0) {
                "nonpositive dose"
            }
            require(halfLife.isPositive()) {
                "nonpositive halfLife"
            }
            return DCBuilder {
                it.copy(active = activeCompound, dose = dose, halfLife = halfLife / tickDuration)
            }
        }
        fun withBlendComponent(componentDose: @Positive Double, cInfo: CompoundInfo): DCBuilder {
            require(componentDose > 0.0) {
                "nonpositive dose"
            }
            require(cInfo.activeCompound.isNotEmpty()) {
                "empty active compound"
            }
            return DCBuilder {
                it.copy(
                    active = cInfo.activeCompound,
                    dose = componentDose,
                    halfLife = cInfo.halfLife / tickDuration
                )
            }
        }
        fun withTransformer(activeCompound: String, transformerName: String) : DCBuilder {
            require(activeCompound.isNotEmpty()) {
                "empty activeCompound"
            }
            require(transformerName in transformers.keys) {
                "unrecognized transformer"
            }
            return DCBuilder {
                it.copy(
                    active = activeCompound,
                    transformer = transformerName
                )
            }
        }
    }

    private fun checkValid() {
        require(active.isNotEmpty()) {
            "empty active"
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
        for ((i, f) in freqs.withIndex()) {
            require(f > 0) {
                "nonpositive freq[$i]"
            }
        }
        require((dose > 0.0 && halfLife > 0.0) || !transformer.isNullOrEmpty()) {
            "empty dose or halflife without transformer"
        }
    }

    companion object {
        /**
         * Like a constructor but use the build pattern for late validation.
         * @see DecodedCycle
         */
        internal fun withRawValues(
            active: String,
            dose: @Positive Double = 0.0,
            halfLife: @Positive @TimeTickScaled Double = 0.0,
            start: @Nonnegative @TimeTickScaled Int,
            duration: @Positive @TimeTickScaled Int,
            freqs: List<@Positive @TimeTickScaled Int>, // each element is number of tickDuration's to increment by
            transformer: String? = null
        ) =
            DCBuilder { _ -> DecodedCycle(active, dose, halfLife, start, duration, freqs, transformer) }

        fun build(vararg builders: DCBuilder): DecodedCycle {
            var obj = DecodedCycle(active = "", start = -1, duration = -1, freqs = emptyList())
            for (builder in builders)
                obj = builder.incrementallyBuild(obj)
            obj.checkValid()
            return obj
        }
    }
}

/** All the data needed to evaluate a cycle. */
@Serializable
internal data class CycleCalculation(
    val cycles: List<DecodedCycle>,
    val transformers: List<DecodedCycle>
)

private fun Double.tryToInt(context: String? = null) =
    if (this.absoluteValue < Int.MAX_VALUE)
        this.toInt()
    else
        throw IllegalArgumentException("${context?.plus(" ") ?: ""} out of range for Int")