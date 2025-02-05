package com.moshy.drugcalc.repo

import com.moshy.drugcalc.calc.Config
import com.moshy.drugcalc.calcdata.CompoundInfo
import com.moshy.drugcalc.calcdata.DataStore
import com.moshy.drugcalc.io.*
import com.moshy.drugcalc.io.ConfigMap
import com.moshy.drugcalc.io.Diff
import com.moshy.drugcalc.io.FullData
import com.moshy.drugcalc.io.FullDiffData

import com.moshy.plus
import kotlin.time.DurationUnit
import kotlin.time.toDuration

// FIXME: refactor to use FullDiffData
/**
 * Perform a bulk operation on a [DataStore], returning a new one on success or [IllegalArgumentException] on failure.
 *
 * All strings are case folded.
 */
internal fun DataStore.applyDiff(delta: FullDiffData): DataStore {
    delta.compounds.requireNoCyclicalReplaces(compounds, "compounds",
        equalityChecker = { a, b -> a.halfLife == b.halfLife && a.pctActive == b.pctActive }
    )
    val modifiedCompounds = delta.compounds.delete.keys.intersect(delta.compounds.add.keys)
    delta.blends.requireNoCyclicalReplaces(getBlends(), "blends",
        willInsertEntailRecompute = {
        /* for blends, a replace operation is only cyclical if there is no recompute involved;
         * to check if a recompute is involved, get the set intersection of blends and modified compounds
         */
        it.keys.intersect(modifiedCompounds).isNotEmpty()
    })
    delta.frequencies.requireNoCyclicalReplaces(frequencies, "frequencies")

    return buildAndFreezeCopy {
        removeFrequencies(delta.frequencies.delete.keys)
        removeBlends(delta.blends.delete.keys)
        removeCompounds(delta.compounds.delete.keys)

        addCompounds(delta.compounds.add)
        addBlends(delta.blends.add)
        addFrequencies(delta.frequencies.add)
    }
}

/** Apply bulk inserts onto a blank slate. All strings are case folded. */
internal fun DataStore.applyData(data: FullData): DataStore {

    require(isEmpty()) {
        "attempted applyData on non-empty DataStore"
    }
    return buildAndFreezeCopy {
        addCompounds(data.compounds)
        addBlends(data.blends)
        addFrequencies(data.frequencies)
    }
}

internal fun Config.applyLens(new: ConfigMap) =
    this + new


/** Verify that there are no cyclical replacement operations.
 *
 * A cyclical operation is defined by an operation, given map m, key k, and new value v, that
 * k is in both the delete set and new key set, and that `m[k] == v`, and that [willInsertEntailRecompute]`(v)`
 * returns false.
 */
private fun <V> FullDiff<V>.requireNoCyclicalReplaces(
    m: Map<String, V>,
    name: String,
    equalityChecker: (V, V) -> Boolean = { a, b -> a == b },
    willInsertEntailRecompute: (V) -> Boolean = { false }
) {
    for ((k, newV) in add) {
        if (k !in delete)
            continue
        val existing = m[k] ?: continue
        require (!equalityChecker(existing, newV) || willInsertEntailRecompute(newV)) {
            "$name: detected cyclical replace for key \"$k\""
        }

    }
}

internal fun DiffData.toFullDiffData(dataStore: DataStore) =
    FullDiffData(
        parentRevision = parentRevision,
        compounds = compounds.toFullDiff(dataStore.compounds, "compound"),
        blends = blends.toFullDiff(dataStore.getBlends(), "blend"),
        frequencies = frequencies.toFullDiff(dataStore.frequencies, "frequency")
    )