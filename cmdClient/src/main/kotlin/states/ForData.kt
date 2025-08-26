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

internal class ForData(app: AppState, cacheEvictionPolicy: CacheEvictionPolicy) {
    val logger = logger("${AppState.NAME}:data")

    var currentSection: CurrentSection? = null
    var newEntries: Data = Data()
    var updateEntries: DataUpdater = DataUpdater()

    sealed interface CurrentSection {
        class Compounds(val compound: CompoundBase? = null) : CurrentSection
        object Blends : CurrentSection
        object Frequencies : CurrentSection
    }

    data class DataUpdater(
        val compounds: Map<CompoundName, ProxyMap<CompoundInfo>> = emptyMap(),
    ) {
        fun isNotEmpty() = compounds.isNotEmpty()
    }

    val compoundBasesCache = newOneToManyUnitLoadingCache<CompoundBase>(cacheEvictionPolicy) {
        app.fetchSortedList("/api/data/compounds")
    }
    val compoundVariantsCache = newLoadingCache<CompoundBase, ListAsSortedSet<String>>(cacheEvictionPolicy) {
        val base = it.value.encode()
        // NOTE: throws NoSuchElementException on non-match key
        app.fetchSortedList("/api/data/compounds/$base")
    }
    val blendNamesCache = newOneToManyUnitLoadingCache<BlendName>(cacheEvictionPolicy) {
        app.fetchSortedList("/api/data/blends")
    }
    val frequencyNamesCache = newOneToManyUnitLoadingCache<FrequencyName>(cacheEvictionPolicy) {
        app.fetchSortedList("/api/data/frequencies")
    }
    val compoundValuesCache = newLoadingCache<CompoundName, CompoundInfo>(cacheEvictionPolicy) {
        val cbv = it.compound.value.encode()
        val cvv = it.variant.encode()
        app.doRequest<CompoundInfo>(NetRequestMethod.Get, "/api/data/compounds/$cbv/$cvv")
    }
    val blendValuesCache = newLoadingCache<BlendName, BlendValue>(cacheEvictionPolicy) {
        val bv = it.value.encode()
        app.doRequest<BlendValue>(NetRequestMethod.Get, "/api/data/blendds/$bv")
    }
    val frequencyValuesCache = newLoadingCache<FrequencyName, FrequencyValue>(cacheEvictionPolicy) {
        val fv = it.value.encode()
        app.doRequest<FrequencyValue>(NetRequestMethod.Get, "/api/data/frequencies/$fv")
    }
    val allNameCaches = listOf(compoundBasesCache, compoundVariantsCache, blendNamesCache, frequencyNamesCache)
    val allValueCaches = listOf(compoundValuesCache, blendValuesCache, frequencyValuesCache)

    sealed interface LastEntry {
        class Compound(override val name: CompoundName) : LastEntry
        class Blend(override val name: BlendName) : LastEntry
        class Frequency(override val name: FrequencyName) : LastEntry

        val name: Comparable<*>
    }

    enum class LastEdit {
        NEW, UPDATE,
    }

    var lastEntry: LastEntry? = null
    var lastEdit: LastEdit? = null
    fun clearEditState() {
        lastEdit = null
        lastEntry = null
    }

    /**
     *  If [lastEdit] is [last] and [LastEntry] is [Entry],
     *  then test entry name of type [Name] with [matcher] and [clearEditState].
     */
    inline fun <reified Entry : LastEntry, reified Name : Comparable<Name>> clearEditStateIfMatch(
        last: LastEdit,
        matcher: (Name) -> Boolean
    ) {
        if (lastEdit == last
            && ((lastEntry as? Entry)?.name?.let { matcher(it as Name) } ?: false)
        )
            clearEditState()
    }

    /** If [LastEntry] is [Entry], then test entry name of type [Name] with [matcher] and [clearEditState]. */
    inline fun <reified Entry : LastEntry, reified Name : Comparable<Name>> clearEditStateIfMatch(
        matcher: (Name) -> Boolean
    ) {
        if ((lastEntry as? Entry)?.name?.let { matcher(it as Name) } ?: false)
            clearEditState()
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