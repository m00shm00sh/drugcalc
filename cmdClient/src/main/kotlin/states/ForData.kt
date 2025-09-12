package com.moshy.drugcalc.cmdclient.states

import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.cmdclient.io.*
import com.moshy.drugcalc.cmdclient.io.UrlStringSerializer.Companion.encode
import com.moshy.drugcalc.cmdclient.io.doRequest
import com.moshy.drugcalc.common.*
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.ProxyMap
import com.moshy.containers.ListAsSortedSet
import com.moshy.containers.assertIsSortedSet
import com.moshy.containers.buildCopy
import java.lang.IllegalArgumentException

internal class ForData(private val app: AppState) {
    val logger = logger("${AppState.NAME}:data")

    var currentSection: CurrentSection? = null
    var newEntries: Data = Data()

    sealed interface CurrentSection {
        // save name to avoid a type check
        val name: String

        val editPage: MutableMap<out Comparable<*>, out Any>
        fun produceName(tokens: List<String>): Any

        class Compounds(
            override val name: String = "compounds",
            override val editPage: MutableMap<CompoundName, CompoundInfo> = mutableMapOf(),
        ) : CurrentSection {
            override fun produceName(tokens: List<String>) =
                produceCompoundName(tokens)
        }

        class Blends(
            override val name: String = "blends",
            override val editPage: MutableMap<BlendName, BlendValue> = mutableMapOf(),
        ) : CurrentSection {
            override fun produceName(tokens: List<String>) =
                produceBlendName(tokens)
        }

        class Frequencies(
            override val name: String = "frequencies",
            override val editPage: MutableMap<FrequencyName, FrequencyValue> = mutableMapOf(),
        ) : CurrentSection {
            override fun produceName(tokens: List<String>) =
                produceFrequencyName(tokens)
        }

        class CompoundsUpdater(
            override val name: String = "compounds[updater]",
            override val editPage: MutableMap<CompoundName, ProxyMap<CompoundInfo>> = mutableMapOf()
        ) : CurrentSection {
            override fun produceName(tokens: List<String>) = produceCompoundName(tokens)

        }

    }

    suspend fun fetchCompoundBaseNames() =
        app.fetchSortedList<CompoundBase>("/api/data/compounds").run {
            buildCopy {
                val local = newEntries.compounds.keys
                val toAdd = local.map { it.compound }
                addAll(toAdd)
                (currentSection as? CurrentSection.Compounds)?.let { edit ->
                    addAll(edit.editPage.keys.map { it.compound })
                }
            }
        }

    suspend fun fetchCompoundVariantNames(cb: CompoundBase) =
        app.fetchSortedList<String>("/api/data/compounds/${cb.value.encode()}").run {
                buildCopy {
                    val local = newEntries.compounds.keys
                    val toAdd = local.asSequence()
                        .filter { it.compound == cb }
                        .map { it.variant }
                    addAll(toAdd)
                    (currentSection as? CurrentSection.Compounds)?.let { edit ->
                        edit.editPage.keys.asSequence()
                            .filter { it.compound == cb }
                            .map { it.variant }
                            .let(::addAll)
                    }
                }
            }

    suspend fun fetchBlendNames() =
        app.fetchSortedList<BlendName>("/api/data/blends").run {
            buildCopy {
                val local = newEntries.blends.keys
                addAll(local)
                (currentSection as? CurrentSection.Blends)?.let { edit ->
                    addAll(edit.editPage.keys)
                }
            }
        }

    suspend fun fetchFrequencyNames() =
        app.fetchSortedList<FrequencyName>("/api/data/frequencies").run {
            buildCopy {
                addAll(newEntries.frequencies.keys)
                (currentSection as? CurrentSection.Frequencies)?.let { edit ->
                    addAll(edit.editPage.keys)
                }
            }
        }

    suspend fun fetchCompoundValue(cn: CompoundName): CompoundInfo {
        newEntries.compounds[cn]?.let { return it }
        (currentSection as? CurrentSection.Compounds)?.let { edit ->
            edit.editPage[cn]?.let { return it }
        }
        return app.doRequest<CompoundInfo>(NetRequestMethod.Get,
            "/api/data/compounds" +
                "/${cn.compound.value.encode()}" +
                "/${cn.variant.takeIf { it.isNotEmpty() }?.encode() ?: "-"}"
        )
    }

    suspend fun fetchBlendValue(bn: BlendName): BlendValue {
        newEntries.blends[bn]?.let { return it }
        (currentSection as? CurrentSection.Blends)?.let { edit ->
            edit.editPage[bn]?.let { return it }
        }
        return app.doRequest<BlendValue>(NetRequestMethod.Get,
            "/api/data/blends" +
                "/${bn.value.encode()}"
        )
    }

    suspend fun fetchFrequencyValue(fn: FrequencyName): FrequencyValue {
        newEntries.frequencies[fn]?.let { return it }
        (currentSection as? CurrentSection.Frequencies)?.let { edit ->
            edit.editPage[fn]?.let { return it }
        }
        return app.doRequest<FrequencyValue>(NetRequestMethod.Get,
            "/api/data/frequencies" +
                "/${fn.value.encode()}"
        )
    }

}

internal fun <R> Data.mapAll(block: (Map<out Comparable<*>, Any>) -> R): List<R> =
    buildList {
        add(block(compounds))
        add(block(blends))
        add(block(frequencies))
    }

private suspend inline fun <reified T: Comparable<T>> AppState.fetchSortedList(endpoint: String): ListAsSortedSet<T> =
    doRequest<List<T>>(NetRequestMethod.Get, endpoint).assertIsSortedSet()

private fun produceCompoundName(tokens: List<String>): CompoundName {
    val base = tokens.getOrNull(0)?.let(::CompoundBase)
        ?: throw IllegalArgumentException("expected compound name")
    val variant = tokens.getOrElse(1) { "" }
    return CompoundName(base, variant)
}

private fun produceBlendName(tokens: List<String>): BlendName {
    val bn = tokens.getOrNull(0)?.let(::BlendName)
        ?: throw IllegalArgumentException("expected blend name")
    return bn
}

private fun produceFrequencyName(tokens: List<String>): FrequencyName {
    val fn = tokens.getOrNull(0)?.let(::FrequencyName)
        ?: throw IllegalArgumentException("expected frequency name")
    return fn
}
