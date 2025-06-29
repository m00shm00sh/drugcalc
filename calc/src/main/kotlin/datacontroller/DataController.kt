package com.moshy.drugcalc.calc.datacontroller

import com.moshy.drugcalc.types.datasource.DataSourceDelegate
import com.moshy.drugcalc.types.calccommand.CycleDescription
import com.moshy.drugcalc.common.*
import com.moshy.drugcalc.types.dataentry.*
import com.moshy.ProxyMap
import com.moshy.containers.*
import kotlinx.coroutines.*

class DataController(
    private val controllerConfig: Config,
    private val source: DataSourceDelegate
) {
    // we store the read-only mode value so that writes abort, even if they succeed to cache and fail to source
    var readOnlyMode = controllerConfig.readOnlyMode

    // entry caches; these are not loading caches because we use bulk loaders
    private val compoundsCache = newCache<CompoundName, CompoundInfo>(controllerConfig.compoundEvictionPolicy)
    private val blendsCache = newCache<BlendName, BlendValue>(controllerConfig.blendEvictionPolicy)
    private val frequenciesCache = newCache<FrequencyName, FrequencyValue>(controllerConfig.frequencyEvictionPolicy)

    // search caches are loading caches because we use one-to-many loaders instead of many-to-many loaders
    private val compoundVariantsSearchCache: LoadingCache<CompoundBase, String> =
        newLoadingCache(controllerConfig.compoundEvictionPolicy) {
            source.getVariantsForCompound(it) ?: throw NoSuchElementException("for compound $it")
        }
    private val compoundNamesSearchCache: UnitLoadingCache<CompoundBase> =
        newUnitLoadingCache(controllerConfig.compoundEvictionPolicy) {
            source.getCompoundNames()
        }
    private val blendNamesSearchCache: UnitLoadingCache<BlendName> =
        newUnitLoadingCache(controllerConfig.blendEvictionPolicy) {
            source.getBlendNames()
        }
    private val frequencyNamesSearchCache: UnitLoadingCache<FrequencyName> =
        newUnitLoadingCache(controllerConfig.frequencyEvictionPolicy) {
            source.getFrequencyNames()
        }

    /** Manually purge all caches, e.g. for testing. */
    fun purgeAllCaches() {
        compoundsCache.invalidateAll()
        blendsCache.invalidateAll()
        frequenciesCache.invalidateAll()
        compoundNamesSearchCache.invalidateAll()
        compoundVariantsSearchCache.invalidateAll()
        blendNamesSearchCache.invalidateAll()
        frequencyNamesSearchCache.invalidateAll()
    }

    private suspend fun updateCompoundsSearchCacheOnModify(compounds: Collection<CompoundName>, isDel: Boolean) {
        val cVariantsToModify = buildMap {
            for (c in compounds) {
                getOrPut(c.compound) { mutableSetOf() }.apply {
                    if (c.variant.isNotEmpty())
                        add(c.variant)
                }
            }
        }
        if (!isDel) {
            compoundNamesSearchCache.apply {
                get(Unit)
                    .buildCopy { addAll(cVariantsToModify.keys) }
                    .let { put(Unit, it) }
            }
            compoundVariantsSearchCache.apply {
                getAll(cVariantsToModify.keys)
                    .mapValues { (k, v) ->
                        v.buildCopy { addAll(cVariantsToModify[k]!!) }
                    }
                    .let {
                        for ((k, v) in it)
                            put(k, v)
                    }
            }
        } else {
            val toInvalidate = buildList {
                // only update existing or in-flight cache values
                cVariantsToModify.keys.mapNotNull {
                    compoundVariantsSearchCache.getIfPresent(it)?.let { v -> it to v }
                }.mapNotNull { (k, cacheV) ->
                    val modV = cVariantsToModify[k]!!
                    val newV = cacheV.buildCopy { removeAll(modV) }
                    // delete last variant
                    if (newV.isEmpty() && cacheV.isNotEmpty()) {
                        add(k)
                        return@mapNotNull null
                    }
                    // delete no-variant
                    if (cacheV.isEmpty()) {
                        add(k)
                        return@mapNotNull null
                    }
                    k to newV
                }.forEach { (k, v) ->
                    compoundVariantsSearchCache.put(k, v)
                }
                // kv of cache entries
            }
            for (k in toInvalidate)
                compoundVariantsSearchCache.invalidate(k)
        }
    }

    private suspend fun updateBlendsSearchCacheOnModify(blends: Collection<BlendName>, isDel: Boolean) {
        if (!isDel)
            blendNamesSearchCache.apply {
                get(Unit)
                    .buildCopy { addAll(blends) }
                    .let { put(Unit, it) }
            }
        else
            blendNamesSearchCache.apply {
                getIfPresent(Unit)
                    ?.let {
                        val newV = it.buildCopy { removeAll(blends) }
                        put(Unit, newV)
                    }
            }
    }

    private suspend fun invalidateFrequenciesSearchCacheOnDelete(freqs: Collection<FrequencyName>, isDel: Boolean) {
        if (!isDel)
            frequencyNamesSearchCache.apply {
                get(Unit)
                    .buildCopy { addAll(freqs) }
                    .let { put(Unit, it) }
            }
        else
            frequencyNamesSearchCache.apply {
                getIfPresent(Unit)
                    ?.let {
                        val newV = it.buildCopy { removeAll(freqs) }
                        put(Unit, newV)
                    }
            }
    }

    private suspend inline fun <K, V : Comparable<V>> paginatingGetter(
        cache: LoadingCache<K, V>,
        name: K,
        paginationSpecifier: PaginationSpecifier<V>? = null
    ): ListAsSortedSet<V> =
        cache.get(name).run { paginationSpecifier?.let(::paginateList) ?: this }

    private suspend inline fun <V : Comparable<V>> paginatingGetter(
        cache: UnitLoadingCache<V>,
        paginationSpecifier: PaginationSpecifier<V>? = null
    ): ListAsSortedSet<V> =
        paginatingGetter(cache, Unit, paginationSpecifier)

    suspend fun getVariantsForCompound(
        name: CompoundBase,
        paginationSpecifier: PaginationSpecifier<String>? = null
    ): ListAsSortedSet<String> =
        paginatingGetter(compoundVariantsSearchCache, name.clean(), paginationSpecifier)

    suspend fun getCompoundNames(paginationSpecifier: PaginationSpecifier<CompoundBase>? = null) =
        paginatingGetter(compoundNamesSearchCache, paginationSpecifier)

    suspend fun getBlendNames(paginationSpecifier: PaginationSpecifier<BlendName>? = null) =
        paginatingGetter(blendNamesSearchCache, paginationSpecifier)

    suspend fun getFrequencyNames(paginationSpecifier: PaginationSpecifier<FrequencyName>? = null) =
        paginatingGetter(frequencyNamesSearchCache, paginationSpecifier)

    suspend fun resolveNamesForCycle(cycle: List<CycleDescription>, auxData: Data? = null): Data {
        val cNames = mutableSetOf<CompoundName>()
        val bNames = mutableSetOf<BlendName>()
        val fNames = mutableSetOf<FrequencyName>()
        val auxData = auxData?.run {
            Data(
                compounds = compounds.clean(),
                blends = blends.clean(),
                frequencies = frequencies.clean()
            )
        }

        for (c in cycle) {
            when (c.prefix) {
                CycleDescription.PREFIX_COMPOUND ->
                    CompoundName(CompoundBase(c.compoundOrBlend), c.variantOrTransformer)
                        .clean()
                        .let(cNames::add)

                CycleDescription.PREFIX_BLEND ->
                    BlendName(c.compoundOrBlend)
                        .clean()
                        .let(bNames::add)

                CycleDescription.PREFIX_TRANSFORMER -> {}
            }
            c.freqName.clean().let(fNames::add)
        }
        return doResolveNames(cNames, bNames, fNames, auxData)
    }

    suspend fun resolveNames(
        cNames: Set<CompoundName> = emptySet(),
        bNames: Set<BlendName> = emptySet(),
        fNames: Set<FrequencyName> = emptySet(),
        auxData: Data? = null
    ): Data =
        doResolveNames(
            // trim and casefold may break set property so need to manually sort afterwards
            cNames.takeUnless { it === DONT_EXPAND_BLEND_COMPOUNDS }?.clean()?.copyToSortedSet() ?: cNames,
            bNames.clean().copyToSortedSet(),
            fNames.clean().copyToSortedSet(),
            auxData?.run {
                Data(
                    compounds = compounds.clean(),
                    blends = blends.clean(),
                    frequencies = frequencies.clean()
                )
            }
        )

    private suspend fun doResolveNames(
        cNames: Set<CompoundName> = emptySet(),
        bNames: Set<BlendName> = emptySet(),
        fNames: Set<FrequencyName> = emptySet(),
        auxData: Data? = null
    ): Data {
        val log = logger("$LOG_NAME: resolveNames")

        auxData?.compounds?.let {
            validateAuxCompounds(it)
        }

        val blends = run {
            if (bNames.isEmpty()) {
                log.debug("b: skipping blend fetch")
                return@run emptyMap()
            }
            log.debug("b: checking for blend overrides")
            val overridenBlends = auxData?.blends[bNames] ?: emptyMap()
            log.debug("b: {} blend overrides", overridenBlends.size)
            val bNames = bNames - overridenBlends.keys
            log.debug("b: fetching blends")
            val blends = overridenBlends +
                    blendsCache.getAll(bNames) {
                        log.debug("b: uncached blends {}", it)
                        source.getBlends(it)
                    }
            val unresolvedBlends = bNames - blends.keys
            require(unresolvedBlends.isEmpty()) {
                log.debug("b: unresolved blends {}", unresolvedBlends)
                unresolved("unresolved blends: $unresolvedBlends")
            }
            blends
        }
        val compounds = run {
            if (cNames === DONT_EXPAND_BLEND_COMPOUNDS) {
                log.debug("c: skipping compounds fetch (blend expansion disabled)")
                return@run emptyMap()
            }
            if (cNames.isEmpty() && blends.isEmpty()) {
                log.debug("c: skipping compounds fetch (nothing to fetch)")
                return@run emptyMap()
            }

            val blendCompounds = buildSet {
                blends.values.forEach {
                    addAll(it.keys)
                }
            }
            log.debug(
                "c: {} added blend compounds",
                lazyPrintable { (blendCompounds - cNames).toString() }
            )

            val allCompounds = cNames + blendCompounds
            log.debug("c: checking for compound overrides")
            val overridenCompounds = auxData?.compounds[allCompounds] ?: emptyMap()
            log.debug("c: {} compound overrides", overridenCompounds.size)
            val remainingCompounds = allCompounds - overridenCompounds.keys
            log.debug("c: fetching compounds")
            val compounds = overridenCompounds +
                    compoundsCache.getAll(remainingCompounds) {
                        log.debug("c: uncached compounds {}", it)
                        source.getCompounds(it)
                    }
            val unresolvedCompounds = allCompounds - compounds.keys
            require(unresolvedCompounds.isEmpty()) {
                log.debug("c: unresolved compounds {}", unresolvedCompounds)
                unresolved("compounds: $unresolvedCompounds")
            }
            compounds
        }
        log.debug("bc: fetched {} compounds, {} blends", compounds.size, blends.size)
        val frequencies = run {
            if (fNames.isEmpty()) {
                log.debug("f: skipping frequencies fetch")
                return@run emptyMap()
            }
            log.debug("f: checking for frequency overrides")
            val overridenFrequencies = auxData?.frequencies[fNames] ?: emptyMap()
            log.debug("f: {} frequency overrides", overridenFrequencies.size)
            val fNames = fNames - overridenFrequencies.keys
            log.debug("f: fetching frequencies")
            val fItems = overridenFrequencies +
                    frequenciesCache.getAll(fNames) {
                        log.debug("f: uncached frequencies {}", it)
                        source.getFrequencies(it)
                    }

            val unresolvedFreqs = fNames - fItems.keys
            require(unresolvedFreqs.isEmpty()) {
                log.debug("f: unresolved frequencies {}", unresolvedFreqs)
                unresolved("frequencies: $unresolvedFreqs")
            }
            fItems
        }
        return Data(compounds, blends, frequencies)
    }

    suspend fun putEntries(resolved: Data): Boolean = mustWrite {
        /* DataSourceDelegate doesn't have a transaction wrapper to transactionalize ((compounds+blends)+frequencies)
         * write. Use supervisorScope to propagate the independence of the two
         *
         * @ref removeEntries
         * @ref clearEntries
         */
        val log = logger("$LOG_NAME: putEntries")
        log.debug("preparing data")
        val compounds = resolved.compounds.clean()
        validateAuxCompounds(compounds)
        val blends = resolved.blends.clean()

        val frequencies = resolved.frequencies.clean()
        // push to db before cache so that we don't have possibly invalid cache entries
        log.debug("pushing {} compounds, {} blends, {} frequencies to source",
            compounds.size, blends.size, frequencies.size
        )
        /* FIXME: the DB operation will fail (via FK constraint) if blends has invalid entry;
         *        figure out if the output can give us a usably detailed error message
         */
        val dbResult = source.putBulk(compounds, blends, frequencies)

        if (compounds.isNotEmpty()) {
            log.debug("updating compounds cache")
            for ((name, info) in compounds)
                compoundsCache[name] = info
            updateCompoundsSearchCacheOnModify(compounds.keys, false)
        }
        if (blends.isNotEmpty()) {
            log.debug("updating blends cache")
            for ((name, info) in blends)
                blendsCache[name] = info
            updateBlendsSearchCacheOnModify(blends.keys, false)
        }
        if (frequencies.isNotEmpty()) {
            log.debug("updating frequencies cache")
            for ((name, vals) in frequencies)
                frequenciesCache[name] = vals
            invalidateFrequenciesSearchCacheOnDelete(frequencies.keys, false)
        }

        return dbResult
    }

    /*  When auxData contains compounds, we need to make sure we don't have any of:
      * DB has zero variants && auxData.compounds has 1+ variants, or
      * DB has 1+ variants and auxData.compounds has 0 variants
      */
    private suspend fun validateAuxCompounds(auxCK: Map<CompoundName, CompoundInfo>) {
        val log = logger("$LOG_NAME: validateAuxCompounds")
        log.debug("validating auxdata compounds")
        if (auxCK.isEmpty())
            return
        val acNames = buildMap<CompoundBase, MutableList<String>>(auxCK.size) {
            for ((name, _) in auxCK) {
                val compound = name.compound
                val variant = name.variant
                /* check for existing variants for compound to self validate against
                 * [no variant] + variant
                 * [variant] + no variant
                 * (same kind of check is done with db vs auxCk later on)
                 */
                val variantList = this[compound]?.let {
                    if (it.isEmpty() && !variant.isEmpty()) {
                        throw IllegalArgumentException("compound $compound requires no variant (got $variant)")
                    }
                    if (it.isNotEmpty() && variant.isEmpty()) {
                        throw IllegalArgumentException("compound $compound requires a variant")
                    }
                    it
                } ?: getOrPut(compound) { mutableListOf() }
                if (variant.isNotEmpty())
                    variantList += variant
            }
        }
        val dbVariants = acNames.mapValues { (k, _) ->
            try {
                getVariantsForCompound(k)
            } catch (_: NoSuchElementException) {
                null
            }
        }

        for ((k, v1) in acNames) {
            val v2 = dbVariants[k]
            if (v2 == null)
                continue
            if (v1.isEmpty() && !v2.isEmpty()) {
                throw IllegalArgumentException("compound $k requires a variant")
            }
            if (v1.isNotEmpty() && v2.isEmpty()) {
                throw IllegalArgumentException("compound $k requires no variant (got ${v1.first()})")
            }
        }
    }

    suspend fun updateCompounds(map: Map<CompoundName, ProxyMap<CompoundInfo>>): Boolean = mustWrite {
        val map = map.clean()
        // fetch existing compounds to make sure they are valid after update
        // TODO: there is no locking of compound slots in the cache between the atomic get-or-put and the final put;
        //       will there be any meaningful race conditions that aren't solved by cache expiration and refresh?
        val data = compoundsCache.getAll(map.keys) {
            source.getCompounds(it)
        }.mapValues { (k, v) ->
            val newV = map[k]!!
            newV.applyToObject(v)
        }
        val dbResult = source.updateCompounds(map)
        for ((k, v) in data)
            compoundsCache.put(k, v)
        return dbResult
    }

    @Throws(IllegalArgumentException::class, UnsupportedOperationException::class)
    suspend fun removeEntries(
        cNames: Collection<CompoundName> = emptySet(),
        bNames: Collection<BlendName> = emptySet(),
        fNames: Collection<FrequencyName> = emptySet()
    ): Boolean = mustWrite {
        val log = logger("$LOG_NAME: removeEntries")

        val cNames = cNames.takeIf { cNames !== DELETE_ALL_COMPOUNDS }?.clean() ?: cNames
        val bNames = bNames.takeIf { bNames !== DELETE_ALL_BLENDS }?.clean() ?: bNames
        val fNames = fNames.takeIf { fNames !== DELETE_ALL_FREQUENCIES }?.clean() ?: fNames

        log.debug("deleting {} compounds, {} blends, {} frequencies from source",
            if (cNames === DELETE_ALL_COMPOUNDS) "<all>" else cNames.size.toString(),
            if (bNames === DELETE_ALL_BLENDS) "<all>" else bNames.size.toString(),
            if (fNames === DELETE_ALL_FREQUENCIES) "<all>" else fNames.size.toString()
        )
        val dbResult = source.deleteBulk(cNames, bNames, fNames)

        if (cNames === DELETE_ALL_COMPOUNDS) {
            log.debug("clearing compounds cache")
            compoundsCache.invalidateAll()
            log.debug("clearing compound names search caches")
            compoundNamesSearchCache.invalidateAll()
            compoundVariantsSearchCache.invalidateAll()
        } else if (cNames.isNotEmpty()) {
            log.debug("deleting from compounds cache")
            cNames.forEach {
                compoundsCache.invalidate(it)
            }
            log.debug("deleting from compound names search cache")
            updateCompoundsSearchCacheOnModify(cNames, true)
        }
        if (bNames === DELETE_ALL_BLENDS) {
            log.debug("clearing blends cache")
            blendsCache.invalidateAll()
            log.debug("clearing blend names search cache")
            blendNamesSearchCache.invalidateAll()
        } else if (bNames.isNotEmpty()) {
            log.debug("deleting from blends cache")
            bNames.forEach {
                blendsCache.invalidate(it)
            }
            log.debug("deleting from blend names search cache")
            updateBlendsSearchCacheOnModify(bNames, true)
        }
        if (fNames === DELETE_ALL_FREQUENCIES) {
            log.debug("clearing frequencies cache")
            frequenciesCache.invalidateAll()
            log.debug("clearing frequency names search cache")
            frequencyNamesSearchCache.invalidateAll()
        } else if (fNames.isNotEmpty()) {
            log.debug("deleting from frequencies cache")
            fNames.forEach {
                frequenciesCache.invalidate(it)
            }
            log.debug("deleting from frequency names search cache")
            invalidateFrequenciesSearchCacheOnDelete(fNames, true)
        }
        dbResult
    }

    suspend fun clearEntries(): Boolean = mustWrite {
        supervisorScope {
            val logger = logger("$LOG_NAME: clearEntries")
            if (!controllerConfig.allowDbClear) {
                logger.warn("attempted to clear all entries without flag allowDbClear set")
                throw UnsupportedOperationException("this instance is not permitted to clear all entries")
            }
            logger.debug("clear all entries")
            logger.debug("purging DB")
            val rv = buildList {
                async(CoroutineName("BlendsCompoundsDeleter")) {
                    source.deleteAllBlends()
                    source.deleteAllCompounds()
                }.let(::add)
                async(CoroutineName("FrequenciesDeleter")) {
                    source.deleteAllFrequencies()
                }.let(::add)
            }.awaitAll().fold(false) { a, b -> a || b }
            logger.debug("purging caches")
            compoundsCache.invalidateAll()
            blendsCache.invalidateAll()
            frequenciesCache.invalidateAll()
            logger.debug("done")
            rv
        }
    }

    suspend fun isEmpty() = source.isDbEmpty()

    private inline fun mustWrite(block: () -> Boolean): Boolean =
        if (readOnlyMode) throw UnsupportedOperationException("read-only mode")
        else block()

    data class Config(
        val readOnlyMode: Boolean = false,
        val allowDbClear: Boolean = false,
        val compoundEvictionPolicy: CacheEvictionPolicy = CacheEvictionPolicy(),
        val blendEvictionPolicy: CacheEvictionPolicy = CacheEvictionPolicy(),
        val frequencyEvictionPolicy: CacheEvictionPolicy = CacheEvictionPolicy(),
    )

    companion object {
        private const val LOG_NAME = "calc: DataController"

        @Suppress("ClassName", "JavaDefaultMethodsNotOverriddenByDelegation")
        object DONT_EXPAND_BLEND_COMPOUNDS : Set<CompoundName> by emptySet()

        val DELETE_ALL_COMPOUNDS = DataSourceDelegate.DELETE_ALL_COMPOUNDS
        val DELETE_ALL_BLENDS = DataSourceDelegate.DELETE_ALL_BLENDS
        val DELETE_ALL_FREQUENCIES = DataSourceDelegate.DELETE_ALL_FREQUENCIES

        const val UNRESOLVED = "unresolved "

        private fun unresolved(what: String) = "$UNRESOLVED$what"
    }
}
