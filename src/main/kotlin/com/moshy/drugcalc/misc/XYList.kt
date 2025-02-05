package com.moshy.drugcalc.misc

import com.moshy.drugcalc.calc.TimeTickIndexed
import com.moshy.drugcalc.calc.TimeTickScaled
import com.moshy.drugcalc.internaltypes.RangeList
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/** Tagged XY pair that gives information on (x,y) plotting type (bar vs point).
 *
 * @param type plotting type; see [PlotType]
 */
@Serializable
data class XYList(val type: PlotType, val x: List<@TimeTickScaled Int>, val y: List<Double>) {
    @Serializable
    enum class PlotType {
        @SerialName("point") POINT,
        @SerialName("bar") BAR,
    }

    companion object {
        fun pointPlot(xs: List<@TimeTickScaled Int>, ys: List<Double>) = XYList(PlotType.POINT, xs, ys)
        fun barPlot(xs: List<@TimeTickScaled Int>, ys: List<Double>) = XYList(PlotType.BAR, xs, ys)
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
internal fun @TimeTickIndexed List<Double>.toXYList(): XYList {
    val xs: MutableList<@TimeTickScaled Int> = mutableListOf()
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

@JvmName("RangeList\$toXYList")
internal fun RangeList<Double, @TimeTickScaled Int>.toXYList(): XYList =
    this.map { Pair(it.from, it.value) }.run {
        XYList.barPlot(this.map { it.first }, this.map { it.second })
    }

internal fun XYList.decodeTimeTickScaling(timeTick: Duration) =
    DecodedXYList(type, x.map { timeTick * it }, y)