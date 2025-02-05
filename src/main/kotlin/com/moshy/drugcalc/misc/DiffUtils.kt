package com.moshy.drugcalc.misc

import com.moshy.drugcalc.calcdata.CompoundInfo
import com.moshy.drugcalc.io.Diff
import com.moshy.drugcalc.io.DiffData
import com.moshy.drugcalc.io.FullData
import com.moshy.drugcalc.io.FullDiff
import com.moshy.drugcalc.io.FullDiffData

internal fun List<FullDiffData>.computeCompositeDiff(): FullDiffData =
    reduce {
        acc, next ->
        acc.copy(
            compounds = acc.compounds.combineWith(next.compounds),
            blends = acc.blends.combineWith(next.blends),
            frequencies = acc.frequencies.combineWith(next.frequencies)
        )
    }

internal fun DiffData.caseFolded() =
    copy(
        compounds = Diff(
            delete = compounds.delete.caseFolded("compounds.delete"),
            add = compounds.add.caseFolded("compounds.add")
        ),
        blends = Diff(
            delete = blends.delete.caseFolded("blends.delete"),
            add = blends.add.caseFolded("blends.add") { k, v -> v.caseFolded(k) }
        ),
        frequencies = Diff(
            delete = frequencies.delete.caseFolded("frequencies.delete"),
            add = frequencies.add.caseFolded("frequencies.add")
        )
    )

internal fun <V> FullDiff<V>.reversed() =
    copy(
        delete = add,
        add = delete
    )

// this is only used in Repo when operating FullDiff<V> so no need for Diff<V>.summarized()
internal fun <V> FullDiff<V>.summarized() =
    delete.size to add.size

internal fun FullDiffData.reversed() =
    copy(
        compounds = compounds.reversed(),
        blends = blends.reversed(),
        frequencies = frequencies.reversed()
    )

internal fun FullData.applyDiff(data: FullDiffData) =
    FullData(
        compounds = compounds.combineWith(data.compounds),
        blends = blends.combineWith(data.blends),
        frequencies = frequencies.combineWith(data.frequencies)
    )

internal fun DiffData.withPreparedCompounds() =
    copy(
        compounds = compounds.copy(
            add = compounds.add.fillActiveCompounds()
        )
    )

/** Prepare CompoundMap for further use by filling missing activeCompound fields. */
internal fun Map<String, CompoundInfo>.fillActiveCompounds() =
    mapValues {(name, info) ->
        if (info.activeCompound.isNotEmpty())
            info
        else
            info.copy(activeCompound = name.substringBeforeLast(" "))
    }

private fun <V> FullDiff<V>.combineWith(next: FullDiff<V>): FullDiff<V> {
    val crossedOut = next.delete.entries.intersect(add.entries).map { it.key }.toSet() - next.add.keys
    return FullDiff(
        delete = (next.delete + delete - crossedOut),
        add = (add + next.add - crossedOut)
    )
}

private fun <V> Map<String, V>.combineWith(next: FullDiff<V>) =
    (this - next.delete.keys + next.add)

private fun Collection<String>.caseFolded(container: String) =
    buildSet {
        for (e in this@caseFolded)
            require(add(e.lowercase())) {
                "$container: case folding produced a duplicate key for \"$e\""
            }
    }
private fun <V> Map<String, V>.caseFolded(
    container: String,
    valueTransform: (String, V) -> V = { _, it -> it }
) =
    buildMap {
        for ((k, v) in this@caseFolded) {
            require (put(k.lowercase(), valueTransform("$container.$k", v)) == null) {
                "$container: case folding produced a duplicate key for \"$k\""
            }
        }
    }

