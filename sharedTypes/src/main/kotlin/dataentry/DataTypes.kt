package com.moshy.drugcalc.types.dataentry

import com.moshy.drugcalc.common.checkValues
import com.moshy.ProxyMap
import kotlin.time.Duration
import kotlinx.serialization.*
import kotlinx.serialization.descriptors.*
import kotlinx.serialization.encoding.*

@Serializable
data class Data(
    val compounds: CompoundsMap = emptyMap(),
    val blends: BlendsMap = emptyMap(),
    val frequencies: FrequenciesMap = emptyMap()
) {
    fun isNotEmpty() = listOf(compounds, blends, frequencies).any { it.isNotEmpty() }
}

typealias CompoundsMap = Map<CompoundName, CompoundInfo>
typealias CompoundsUpdateMap = Map<CompoundName, CompoundDetailUpdateMap>
typealias CompoundDetailUpdateMap = ProxyMap<CompoundInfo>
typealias BlendsMap = Map<BlendName, BlendValue>
typealias FrequenciesMap = Map<FrequencyName, FrequencyValue>

/** The full name of a compound. */
@Serializable(with = CNSerializer::class)
data class CompoundName(
    val compound: CompoundBase,
    val variant: String = "",
    /** To select all variants for compound, set this to true. Excluded from serialization. */
    @Transient val selectAllVariants: Boolean = false
) : Comparable<CompoundName> {
    constructor(compound: String): this(CompoundBase(compound))

    init {
        if (selectAllVariants) {
            // we're not using nullable types so unset is coerced to empty
            require(variant.isEmpty()) {
                "variant cannot be set to non-empty if selectAllVariants is true"
            }
        }
    }

    override fun compareTo(other: CompoundName): Int {
        (compound.compareTo(other.compound)).takeIf { it != 0 }?.let { return it }
        (variant.compareTo(other.variant)).takeIf { it != 0 }?.let { return it }
        return selectAllVariants.compareTo(other.selectAllVariants)
    }

    override fun toString(): String = buildString {
        val hasV = variant.isNotEmpty()
        append("CompoundName(")
        if (hasV)
            append("compound=")
        append(compound.value)
        if (hasV) {
            append(", variant=")
            append(variant)
        } else if (selectAllVariants) {
            append(" (select-all-variants)")
        }
        append(")")
    }

    companion object {
        fun selectAllVariants(compound: String) =
            selectAllVariants(CompoundBase(compound))
        fun selectAllVariants(compound: CompoundBase) =
            CompoundName(compound, selectAllVariants = true)
    }
}

internal object CNSerializer: KSerializer<CompoundName> {
    override fun deserialize(decoder: Decoder): CompoundName {
        val s = decoder.decodeString()
        val items = s.split('=', limit = 2)
        val base = CompoundBase(items[0])
        val variant = items.getOrElse(1) { "" }
        return CompoundName(base, variant)
    }

    override fun serialize(encoder: Encoder, value: CompoundName) {
        val s = "${value.compound.value}=${value.variant}"
        encoder.encodeString(s)
    }

    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("dc.io.CNSerializer", PrimitiveKind.STRING)

}

/** The base name of a compound. */
@JvmInline
@Serializable
value class CompoundBase(val value: String) : Comparable<CompoundBase> {
    init {
        require(value.isNotEmpty()) {
            "compound is empty"
        }
        // collision with prefix
        require(!value.startsWith(".")) {
            "compound starts with '.'"
        }
        // reserved for CNSerializer
        require(!value.contains("=")) {
            "compound contains '='"
        }
    }

    override fun compareTo(other: CompoundBase): Int = value.compareTo(other.value)

    override fun toString() = value
}

/** Compound info.
 * @property halfLife half-life; positive
 * @property pctActive percent active dose; value in (0.0, 1.0]
 * @property note miscellaneous note
 */
@Serializable
data class CompoundInfo(
    val halfLife: @Contextual Duration,
    val pctActive: Double = 1.0,
    val note: String = "",
) {
    init {
        require(halfLife.isPositive()) {
            "nonpositive halfLife ($halfLife)"
        }
        require(pctActive > 0 && pctActive <= 1.0) {
            "pctActive not in (0.0, 1.0] (got $pctActive)"
        }
    }
}

/** A valid blend name. */
@JvmInline
@Serializable
value class BlendName(val value: String) : Comparable<BlendName> {
    init {
        require(value.isNotEmpty()) {
            "blend string is empty"
        }
        require(!value.startsWith(".")) {
            "blend string starts with '.'"
        }
        require(!value.contains("=")) {
            "blend string contains '='"
        }
    }

    override fun compareTo(other: BlendName): Int = value.compareTo(other.value)

    override fun toString() = value
}

/** A valid blend value. */
@Serializable
data class BlendValue(private val components: Map<CompoundName, Double>): Map<CompoundName, Double> by components {
    init {
        require(components.size > 1) {
            "2+ components required"
        }
        for ((k, v) in components) {
            require(v > 0) {
                "non-positive component value $v for key $k"
            }
        }
    }
}

/** A valid frequency name. */
@JvmInline
@Serializable
value class FrequencyName(val value: String) : Comparable<FrequencyName> {
    init {
        require(value.isNotEmpty()) {
            "frequency string is empty"
        }
    }

    override fun compareTo(other: FrequencyName): Int = value.compareTo(other.value)

    override fun toString() = value
}

/** A valid frequency value. */
@Suppress("JavaDefaultMethodsNotOverriddenByDelegation")
@Serializable
data class FrequencyValue(private val values: List<@Contextual Duration>): List<Duration> by values {
    init {
        require(values.isNotEmpty()) {
            "empty list"
        }
        values.checkValues(Duration::isPositive) { i, v -> "$i: nonpositive duration $v" }
    }
}
