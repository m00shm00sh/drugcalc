package com.moshy.drugcalc.cmdclient.io.data

import com.moshy.drugcalc.types.dataentry.*
import com.moshy.ProxyMap
import org.jetbrains.kotlinx.dataframe.DataFrame
import org.jetbrains.kotlinx.dataframe.annotations.DataSchema
import org.jetbrains.kotlinx.dataframe.api.rows
import kotlin.time.Duration

/* NOTE:
 * If a field is nullable, then an empty value can exist in the CSV / Excel cell pertaining to that field
 */

@DataSchema
internal interface CompoundsUpdater {
    val compound: String
    val variant: String?
    val halfLife: Duration?
    val pctActive: Double?
    val note: String?
}

@DataSchema
internal interface Compound {
    val compound: String
    val variant: String?
    val halfLife: Duration
    val pctActive: Double?
    val note: String?
}

@DataSchema
internal interface Blend {
    val name: String
    val componentCompound: String
    val componentVariant: String
    val componentDose: Double
    val note: String?
}

@DataSchema
internal interface Frequency {
    val name: String
    val time: Duration
}

// we need this hack to bridge the generics and overloads
internal inline fun <reified R, K, V> dfToMap(df: DataFrame<R>, startOffset: Int = 0): Map<K, V> =
    @Suppress("UNCHECKED_CAST")
    when (R::class) {
        CompoundsUpdater::class -> dfToMap(df as DataFrame<CompoundsUpdater>, startOffset) as Map<K, V>
        Compound::class -> dfToMap(df as DataFrame<Compound>, startOffset) as Map<K, V>
        Blend::class -> dfToMap(df as DataFrame<Blend>, startOffset) as Map<K, V>
        Frequency::class -> dfToMap(df as DataFrame<Frequency>, startOffset) as Map<K, V>
        else -> error("unexpected dataframe type ${R::class}")
    }

/*
 * For the most part, the DataFrame to Map converters are duplicates of the engine-specific bits in db/ that
 * convert rows to structures, but with one notable difference: in the DB part, the note field is only multiplied
 * during the join and not in storage, so to mimic this rule, we use the first non-null value of note and raise
 * a parsing exception if there's a non-null inequal value.
 *
 * The parameter startOffset is used in exceptions for resolving physical line numbers.
 */

@JvmName($$"dfToMap$CompoundsUpdater")
private fun dfToMap(df: DataFrame<CompoundsUpdater>, startOffset: Int = 0): Map<CompoundName, ProxyMap<CompoundInfo>> =
    buildMap {
        df.rows().forEach { row ->
            with(row) {
                val physicalIndexP1 = startOffset + index() + 1
                try {
                    val compound = CompoundBase(compound)
                    val variant = variant.convertHyphenMarkerToEmptyString()
                    val name =
                        if (variant != null && variant != "-")
                            CompoundName(compound, variant)
                        else
                            CompoundName(compound)
                    val pmInfo = buildMap {
                        halfLife?.let { put("halfLife", it) }
                        pctActive?.let { put("pctActive", it) }
                        note.convertHyphenMarkerToEmptyString()?.let { put("note", it) }
                    }.let { ProxyMap<CompoundInfo>(it) }
                    put(name, pmInfo)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("at line $physicalIndexP1: $e.message")
                }
            }
        }
    }

@JvmName($$"dfToMap$Compound")
private fun dfToMap(df: DataFrame<Compound>, startOffset: Int = 0): Map<CompoundName, CompoundInfo> =
    buildMap {
        df.rows().forEach { row ->
            with(row) {
                val physicalIndexP1 = startOffset + index() + 1
                try {
                    val compound = CompoundBase(compound)
                    val variant = variant.convertHyphenMarkerToEmptyString()
                    val name =
                        if (variant != null && variant != "-")
                            CompoundName(compound, variant)
                        else
                            CompoundName(compound)
                    val info = buildMap {
                        halfLife.let { put("halfLife", it) }
                        pctActive?.let { put("pctActive", it) }
                        note.convertHyphenMarkerToEmptyString()?.let { put("note", it) }
                    }.let {
                        /* we have enough null=>default that deferring to ProxyMap.createObject is simpler since it
                         * takes care of the constructor.callBy details for us
                         */
                        ProxyMap<CompoundInfo>(it)
                            .createObject()
                    }
                    put(name, info)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("at line $physicalIndexP1: $e.message")
                }
            }
        }
    }

@JvmName($$"dfToMap$Blend")
private fun dfToMap(df: DataFrame<Blend>, startOffset: Int = 0): Map<BlendName, BlendValue> =
    buildMap {
        var currentName: BlendName? = null
        var noteForName: String? = null
        var inner: MutableMap<CompoundName, Double>? = null
        var physicalIndexP1 = 0
        try {
            df.rows().forEach { row ->
                with(row) {
                    physicalIndexP1 = startOffset + index() + 1
                        val blendName = name.let(::BlendName)
                        if (currentName == null || currentName != blendName) {
                            // move inner on blendName transition, not first row
                            if (currentName != null)
                                this@buildMap[currentName!!] =
                                    BlendValue(inner!! as Map<CompoundName, Double>, noteForName.orEmpty())
                            inner = mutableMapOf()
                            currentName = blendName
                            note?.let { rowNote ->
                                if (noteForName != null && rowNote != noteForName)
                                    throw IllegalArgumentException("at line $physicalIndexP1: unexpected note value")
                                noteForName = rowNote
                            }
                        }
                        inner!![CompoundName(CompoundBase(componentCompound), componentVariant)] = componentDose
                }
            }
            if (currentName != null && inner != null)
                this@buildMap[currentName] = BlendValue(inner as Map<CompoundName, Double>, noteForName.orEmpty())
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("at line $physicalIndexP1 or block ending on it: $e.message")
        }
    }

@JvmName($$"dfToMap$Frequency")
private fun dfToMap(df: DataFrame<Frequency>, startOffset: Int = 0): Map<FrequencyName, FrequencyValue> =
    buildMap {
        var currentName: FrequencyName? = null
        var inner: MutableList<Duration>? = null
        var physicalIndexP1 = 0
        try {
            df.rows().forEach { row ->
                with(row) {
                    physicalIndexP1 = startOffset + index() + 1
                    val freqName = name
                    if (currentName == null || currentName?.value != freqName) {
                        // move inner on freqName transition, not first row
                        if (currentName != null)
                            this@buildMap[currentName!!] = FrequencyValue(inner!! as List<Duration>)
                        inner = mutableListOf()
                        currentName = FrequencyName(freqName)
                    }
                    inner!!.add(time)
                }
            }
            if (currentName != null && inner != null)
                this@buildMap[currentName] = FrequencyValue(inner)
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("at line $physicalIndexP1 or block ending on it: $e.message")
        }
    }

internal fun String?.convertHyphenMarkerToEmptyString() =
    when {
        this == null -> null
        isEmpty() -> null
        this == "-" -> ""
        else -> this
    }