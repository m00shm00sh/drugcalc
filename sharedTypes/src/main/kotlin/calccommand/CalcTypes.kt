package com.moshy.drugcalc.types.calccommand

import com.moshy.ProxyMap
import com.moshy.drugcalc.types.dataentry.Data
import com.moshy.drugcalc.types.dataentry.FrequencyName
import kotlinx.serialization.*
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Serializable
data class CycleRequest(
    val data: Data? = null,
    val config: ProxyMap<Config>? = null,
    val cycle: List<CycleDescription>
) {
    init {
        require(cycle.isNotEmpty()) {
            "empty cycle"
        }
    }
}

typealias CycleResult = Map<String, XYList>
typealias DecodedCycleResult = Map<String, DecodedXYList>

/** Describes a cycle of a compound.
 *
 * @property prefix optional prefix; see PREFIX_XXX
 * @property compoundOrBlend compound or blend name
 * @property variantOrTransformer optional compound variant or transformer name
 * @property dose dose, in milligrams; value ignored if [compoundOrBlend] is a transformer
 * @property start start time
 * @property duration duration
 * @property freqName frequency name; must exist in {frequencies}
 *
 * @throws IllegalArgumentException if values are invalid
 */
@Serializable
data class CycleDescription(
    val prefix: String? = null,
    val compoundOrBlend: String,
    val variantOrTransformer: String = "",
    val dose: Double? = null,
    @Contextual
    val start: Duration,
    @Contextual
    val duration: Duration,
    val freqName: FrequencyName
) {
    init {
        when (prefix) {
            PREFIX_COMPOUND -> {}
            PREFIX_BLEND -> {
                require(variantOrTransformer.isEmpty()) {
                    "expected empty variant for blend"
                }
            }

            PREFIX_TRANSFORMER -> {
                require(variantOrTransformer.trim().isNotEmpty()) {
                    "expected non-empty transformer"
                }
            }
        }
        require(compoundOrBlend.isNotEmpty()) {
            "empty compoundName"
        }
        when (prefix) {
            PREFIX_COMPOUND, PREFIX_BLEND ->
                require(dose?.takeIf { it > 0.0 } != null) {
                    "nonpositive dose"
                }
            PREFIX_TRANSFORMER -> {}
        }

        require(!start.isNegative()) {
            "negative start"
        }
        require(duration.isPositive()) {
            "nonpositive duration"
        }
    }

    companion object {
        val PREFIX_COMPOUND: String? = null
        const val PREFIX_BLEND = ".b"
        const val PREFIX_TRANSFORMER = ".t"
    }
}

/** Calculator configurables. */
@Serializable
data class Config(
    /** The duration that is elapsed between two consecutive dose values. */
    val tickDuration: Duration = (1.5).toDuration(DurationUnit.HOURS),
    /** Do not evaluate further decay of compound once active dose is below this number. */
    val cutoffMilligrams: Double = 0.01,
    /** The canonical iteration of the formula online has the lambda correction of *dose* and not just time;
     * if the mathematically correct value is desired, set this to False.
     */
    val doLambdaDoseCorrection: Boolean = false,
) {
    init {
        require(tickDuration.isPositive()) {
            "nonpositive tickDuration"
        }
        require(cutoffMilligrams > 0.0) {
            "nonpositive cutoff"
        }
    }
}

/** Tagged XY pair that gives information on (x,y) plotting type (bar vs point).
 *
 * @param type plotting type; see [PlotType]
 */
@Serializable
data class XYList(val type: PlotType, val x: List<Int>, val y: List<Double>) {
    @Serializable
    enum class PlotType {
        @SerialName("point")
        POINT,
        @SerialName("bar")
        BAR,
    }

    companion object {
        fun pointPlot(xs: List<Int>, ys: List<Double>) = XYList(PlotType.POINT, xs, ys)
        fun barPlot(xs: List<Int>, ys: List<Double>) = XYList(PlotType.BAR, xs, ys)
    }
}

@Serializable
data class DecodedXYList(val type: XYList.PlotType, val x: List<Duration>, val y: List<Double>) {
    @Suppress("unused") // usage analysis only discovers the cases used in unit testing
    companion object {
        fun pointPlot(xs: List<Duration>, ys: List<Double>) = DecodedXYList(XYList.PlotType.POINT, xs, ys)
        fun barPlot(xs: List<Duration>, ys: List<Double>) = DecodedXYList(XYList.PlotType.BAR, xs, ys)
    }
}

/**
 * Converts scalar lists to XY with zeros omitted.
 */
@JvmName("IndexedPoints\$toXYList")
fun List<Double>.toXYList(): XYList {
    val xs: MutableList<Int> = mutableListOf()
    val ys: MutableList<Double> = mutableListOf()
    for (xIdx in indices) {
        val yv = this[xIdx]
        if (yv > 0.0) {
            xs.add(xIdx)
            ys.add(yv)
        }
    }
    return XYList.pointPlot(xs, ys)
}

fun XYList.decodeTimeTickScaling(timeTick: Duration) =
    DecodedXYList(type, x.map { timeTick * it }, y)

/** Describes info and usage for a transformer. */
@Serializable
data class TransformerInfo(
    val info: String,
    val usage: String
)
