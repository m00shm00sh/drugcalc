package com.moshy.drugcalc.io

import com.moshy.drugcalc.calcdata.CompoundInfo
import com.moshy.drugcalc.calcdata.CompoundsMap
import com.moshy.drugcalc.calcdata.BlendValue
import com.moshy.drugcalc.calcdata.BlendsMap
import com.moshy.drugcalc.calcdata.FrequenciesMap
import com.moshy.drugcalc.calcdata.DataStore
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlin.time.Duration

/**
 * A diff of data to change. This container does not store deleted values.
 *
 * Use [toFullDiff] to use data from [parentRevision] to convert [Diff]s to [FullDiff]s.
 */
@Serializable
internal data class DiffData(
    override val parentRevision: Int? = null,
    val compounds: Diff<CompoundInfo> = Diff(),
    val blends: Diff<BlendValue> = Diff(),
    val frequencies: Diff<List<@Contextual Duration>> = Diff(),
): HasParentRevision {
    fun isNotEmpty() = listOf(compounds, blends, frequencies).any { it.isNotEmpty() }
}

/**
 * A diff of data to change. Deleted values are preserved in [FullDiff.delete] so this is useful for rollback,
 * detection of `add(del(K))` cycles, and aggregation.
 */
@Serializable
internal data class FullDiffData(
    override val parentRevision: Int? = null,
    val compounds: FullDiff<CompoundInfo> = FullDiff(),
    val blends: FullDiff<BlendValue> = FullDiff(),
    val frequencies: FullDiff<List<@Contextual Duration>> = FullDiff(),
): HasParentRevision

@Serializable
internal data class Diff<V>(
    /* this is weakened to List so that it serializes like a collection more cleanly;
     * uniqueness is verified inside constructor
     */
    val delete: Collection<String> = emptyList(),
    val add: Map<String, V> = emptyMap()
) {
    init {
        val deleteAsSet = when (delete) {
            is Set<String> -> delete
            else -> delete.toSet()
        }
        require(deleteAsSet.size == delete.size) {
            "delete has duplicates"
        }
    }
    fun isNotEmpty() = delete.isNotEmpty() || add.isNotEmpty()
}

@Serializable
internal data class FullDiff<V>(
    val delete: Map<String, V> = emptyMap(),
    val add: Map<String, V> = emptyMap()
) {
    fun isNotEmpty() =
        delete.isNotEmpty() || add.isNotEmpty()
}

/** Given a map of values, expand a mini diff to a ful diff. */
internal fun <V> Diff<V>.toFullDiff(m: Map<String, V>, mapNameSingular: String) =
    FullDiff(delete = delete.associateWith { requireNotNull(m[it]) { "missing $mapNameSingular \"$it\""} }, add = add)

internal fun <V> FullDiff<V>.toDiff() =
    Diff(delete = delete.keys, add = add)
internal fun FullDiffData.toDiffData() =
    DiffData(
        parentRevision = parentRevision,
        compounds = compounds.toDiff(),
        blends = blends.toDiff(),
        frequencies = frequencies.toDiff()
    )

@Serializable
internal data class FullData(
    val compounds: CompoundsMap = emptyMap(),
    val blends: BlendsMap = emptyMap(),
    val frequencies: FrequenciesMap = emptyMap()
)

internal fun DataStore.toFullData() =
    FullData(
        compounds = compounds,
        blends = getBlends(),
        frequencies = frequencies,
    )