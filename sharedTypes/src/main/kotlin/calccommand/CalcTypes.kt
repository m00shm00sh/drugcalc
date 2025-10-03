package com.moshy.drugcalc.types.calccommand

import com.moshy.ProxyMap
import com.moshy.drugcalc.types.dataentry.Data
import com.moshy.drugcalc.types.dataentry.FrequencyName
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*
import kotlinx.serialization.json.JsonClassDiscriminator
import java.util.Objects
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.toDuration

@Serializable
data class CycleRequest(
    val cycle: List<CycleDescription>,
    val data: Data? = null,
    val config: ProxyMap<Config>? = null,
    val decode: DecodeSpec = DecodeSpec.ToDuration(), // default to preserve ?noDecode=false behavior
) {
    init {
        require(cycle.isNotEmpty()) {
            "empty cycle"
        }
    }
}

@JvmInline
@Serializable
value class CycleResult<XYL : XYList>(
    val map: Map<String, XYL>
): Map<String, XYL> by map {
    inline fun <reified XYL2 : XYList> refineOrNull(): CycleResult<XYL2>? {
        if (map.values.any { it !is XYL2 })
            return null
        @Suppress("UNCHECKED_CAST")
        return CycleResult(map as Map<String, XYL2>)
    }
}

@Serializable(with = DecodeSpecSerializer::class)
sealed interface DecodeSpec {
    fun decodeGivenConfig(map: CycleResult<XYList.OfRaw>, c: Config): CycleResult<out XYList>

    class None : DecodeSpec {
        override fun decodeGivenConfig(map: CycleResult<XYList.OfRaw>, c: Config): CycleResult<XYList.OfRaw> {
            return map
        }
    }

    class Scaled(val day: Duration) : DecodeSpec {
        override fun decodeGivenConfig(map: CycleResult<XYList.OfRaw>, c: Config): CycleResult<XYList.OfDay> {
            val dayInTicks = c.tickDuration / day
            return CycleResult(map.mapValues { (_, v) -> v.toTimeRescaledXYList(dayInTicks) })
        }
    }

    class ToDuration : DecodeSpec {
        override fun decodeGivenConfig(map: CycleResult<XYList.OfRaw>, c: Config): CycleResult<XYList.OfDuration> {
            return CycleResult(map.mapValues { (_, v) -> v.toDurationXYList(c.tickDuration) })
        }
    }
}

object DecodeSpecSerializer : KSerializer<DecodeSpec> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("drugcalc.calccommand.DecodeSpec", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): DecodeSpec {
        val incoming = decoder.decodeString()
        if (incoming == "none")
            return DecodeSpec.None()
        if (incoming == "duration")
            return DecodeSpec.ToDuration()
        val asDur = Duration.parseOrNull(incoming)
        if (asDur != null)
            return DecodeSpec.Scaled(asDur)
        throw IllegalArgumentException("unexpected DecodeSpec: $incoming")
    }

    // lenient in accepting, strict in generating
    override fun serialize(encoder: Encoder, value: DecodeSpec) {
        val s = when (value) {
            is DecodeSpec.None -> "none"
            is DecodeSpec.ToDuration -> "duration"
            is DecodeSpec.Scaled -> value.day.toIsoString()
        }
        encoder.encodeString(s)
    }
}

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
                    "missing or nonpositive dose"
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
 * @param plotType plotting type; see [PlotType]
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("xType")
sealed class XYList {
    abstract val plotType: PlotType
    abstract val x: List<Comparable<*>>
    abstract val y: List<Double>

    @Serializable
    enum class PlotType {
        @SerialName("point")
        POINT,
        @SerialName("bar")
        BAR,
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is XYList) return false

        if (plotType != other.plotType) return false
        if (x != other.x) return false
        if (y != other.y) return false

        return true
    }

    override fun hashCode(): Int = Objects.hash(plotType, x, y)

    @Serializable
    @SerialName("raw")
    class OfRaw(
        override val plotType: PlotType,
        override val x: List<Int>,
        override val y: List<Double>
    ) : XYList() {
        companion object {
            fun pointPlot(xs: List<Int>, ys: List<Double>) = OfRaw(PlotType.POINT, xs, ys)
            fun barPlot(xs: List<Int>, ys: List<Double>) = OfRaw(PlotType.BAR, xs, ys)
        }
    }

    @Serializable
    @SerialName("day")
    class OfDay(
        override val plotType: PlotType,
        override val x: List<Double>,
        override val y: List<Double>
    ) : XYList()

    @Serializable
    @SerialName("duration")
    class OfDuration(
        override val plotType: PlotType,
        override val x: List<Duration>,
        override val y: List<Double>
    ) : XYList()
}

/**
 * Converts scalar lists to XY with zeros omitted.
 */
@JvmName("IndexedPoints\$toXYList")
fun List<Double>.toXYList(): XYList.OfRaw {
    val xs: MutableList<Int> = mutableListOf()
    val ys: MutableList<Double> = mutableListOf()
    for (xIdx in indices) {
        val yv = this[xIdx]
        if (yv > 0.0) {
            xs.add(xIdx)
            ys.add(yv)
        }
    }
    return XYList.OfRaw.pointPlot(xs, ys)
}

fun XYList.OfRaw.toTimeRescaledXYList(ticksPerDay: Double): XYList.OfDay =
    XYList.OfDay(plotType, x.map { it * ticksPerDay }, y)

fun XYList.OfRaw.toDurationXYList(timeTick: Duration): XYList.OfDuration =
    XYList.OfDuration(plotType, x.map { timeTick * it }, y)

/** Describes info and usage for a transformer. */
@Serializable
data class TransformerInfo(
    val info: String,
    val usage: String
)
