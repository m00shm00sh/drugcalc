package com.moshy.drugcalc.types.datasource

import com.moshy.drugcalc.types.*
import com.moshy.ProxyMap
import com.moshy.containers.ListAsSortedSet
import com.moshy.drugcalc.types.dataentry.BlendName
import com.moshy.drugcalc.types.dataentry.BlendValue
import com.moshy.drugcalc.types.dataentry.CompoundBase
import com.moshy.drugcalc.types.dataentry.CompoundInfo
import com.moshy.drugcalc.types.dataentry.CompoundName
import com.moshy.drugcalc.types.dataentry.FrequencyName
import com.moshy.drugcalc.types.dataentry.FrequencyValue
import kotlin.time.Duration
import kotlinx.datetime.Instant

/** Delegate for abstracting away database connection implementation from database operations.
 *
 * An implementation may have something like
 * ```
 * class FooDelegate(private val db: FooDatabase): DataSourceDelegate {
 *     [...]
 *     override suspend fun OP(...): ... =
 *         db.OP(...)
 *  }
 *  ```
 *
 *  Implementations may override single-item or all-item operations for performance reasons.
 *
 *  Note: no pagination is done. It is expected that the controller caches the list and extracts and appropriate
 *        slice. This simplifies caching as only the whole list needs to be valid
 */
interface DataSourceDelegate {
    /** Add a single compound.
     *
     * Returns true if value was inserted.
     */
    suspend fun putCompound(name: CompoundName, info: CompoundInfo): Boolean =
        putCompounds(mapOf(name to info))

    /** Update a compound by setting one or more values.
     *
     * Returns true if value was updated.
     */
    suspend fun updateCompound(name: CompoundName, map: ProxyMap<CompoundInfo>): Boolean =
        updateCompounds(mapOf(name to map))

    /** Add multiple compounds.
     *
     * Returns true if any value was inserted.
     */
    suspend fun putCompounds(compounds: Map<CompoundName, CompoundInfo>): Boolean =
        putBulk(compounds = compounds)

    /** Update multiple compounds.
     *
     * Returns true if any value was updated.
     */
    suspend fun updateCompounds(compounds: Map<CompoundName, ProxyMap<CompoundInfo>>): Boolean

    /** Get the list of all compound names. */
    suspend fun getCompoundNames(): ListAsSortedSet<CompoundBase>

    /** Get the list of all matching variants for a compound. */
    suspend fun getVariantsForCompound(forName: CompoundBase): ListAsSortedSet<String>?

    /** Get info record pertaining to one compound name. Returns null if no such compound exists. */
    suspend fun getSingleCompoundOrNull(name: CompoundName): CompoundInfo? =
        getCompounds(listOf(name))[name]

    /** Get info map pertaining to multiple compound names.
     *
     * @return map where each key is a matching compound and value is an info record
     * @see getSingleCompoundOrNull
     */
    suspend fun getCompounds(names: Collection<CompoundName>): Map<CompoundName, CompoundInfo>

    /** Delete a single compound matching name. Returns whether the operation deleted a matching entry. */
    suspend fun deleteCompound(name: CompoundName): Boolean =
        deleteCompounds(listOf(name))

    /** Delete all compounds in [names].
     *
     * Returns whether the operation deleted one or more matching entries.
     */
    suspend fun deleteCompounds(names: Collection<CompoundName>): Boolean =
        deleteBulk(compounds = names)

    /** Delete all compounds. */
    suspend fun deleteAllCompounds(): Boolean =
        deleteCompounds(DELETE_ALL_COMPOUNDS)

    /** Add or update a single blend.
     *
     * Returns true if value was inserted or updated.
     */
    suspend fun putBlend(name: BlendName, components: BlendValue): Boolean =
        putBlends(mapOf(name to components))

    /** Add or update multiple blends.
     *
     * Returns true if any value was inserted or updated.
     */
    suspend fun putBlends(blends: Map<BlendName, BlendValue>): Boolean =
        putBulk(blends = blends)

    /** Get the list of all blend names. */
    suspend fun getBlendNames(): ListAsSortedSet<BlendName>

    /** Get components map pertaining to one blend name. Returns null if no such blend exists. */
    suspend fun getSingleBlendOrNull(name: BlendName): BlendValue? =
        getBlends(listOf(name))[name]

    /** Get components map pertaining to multiple blend [names].
     *
     * @return map where each key is a matching blend and value is a map of components
     * @see getSingleBlendOrNull
     */
    suspend fun getBlends(names: Collection<BlendName>): Map<BlendName, BlendValue>

    /** Delete a single blend matching [name]. Returns whether the operation deleted an entry. */
    suspend fun deleteBlend(name: BlendName): Boolean =
        deleteBlends(listOf(name))

    /** Delete all blends in [names]. Returns whether the operation deleted one or more entries. */
    suspend fun deleteBlends(names: Collection<BlendName>): Boolean =
        deleteBulk(blends = names)

    /** Delete all blends. */
    suspend fun deleteAllBlends(): Boolean =
        deleteBlends(DELETE_ALL_BLENDS)

    /** Add or update a single frequency.
     *
     * Returns true if value was inserted or updated.
     */
    suspend fun putFrequency(name: FrequencyName, items: FrequencyValue): Boolean =
        putFrequencies(mapOf(name to items))

    /** Add or update multiple frequencies.
     *
     * Returns true if any value was inserted or updated.
     */
    suspend fun putFrequencies(frequencies: Map<FrequencyName, FrequencyValue>): Boolean =
        putBulk(frequencies = frequencies)

    /** Get the list of all frequency names. */
    suspend fun getFrequencyNames(): ListAsSortedSet<FrequencyName>

    /** Get list of [Duration]s pertaining to one frequency name. Returns null if no such frequency exists. */
    suspend fun getSingleFrequencyOrNull(name: FrequencyName): FrequencyValue? =
        getFrequencies(listOf(name))[name]

    /** Get items map pertaining to multiple frequency names.
     *
     * @return map where each key is a matching frequency and value is a list of items
     * @see getSingleFrequencyOrNull
     */
    suspend fun getFrequencies(names: Collection<FrequencyName>): Map<FrequencyName, FrequencyValue>

    /** Delete a single frequency matching name. Returns whether the operation deleted a matching entry. */
    suspend fun deleteFrequency(name: FrequencyName): Boolean =
        deleteFrequencies(listOf(name))

    /** Delete all frequencies matching name. Returns whether the operation deleted one or more matching entries. */
    suspend fun deleteFrequencies(names: Collection<FrequencyName>): Boolean =
        deleteBulk(frequencies = names)

    /** Delete all frequencies. */
    suspend fun deleteAllFrequencies(): Boolean =
        deleteFrequencies(DELETE_ALL_FREQUENCIES)

    /** Insert all specified data in one transaction. */
    suspend fun putBulk(
        compounds: Map<CompoundName, CompoundInfo> = emptyMap(),
        blends: Map<BlendName, BlendValue> = emptyMap(),
        frequencies: Map<FrequencyName, FrequencyValue> = emptyMap()
    ): Boolean

    /** Delete all specified data in one transaction. */
    suspend fun deleteBulk(
        compounds: Collection<CompoundName> = emptyList(),
        blends: Collection<BlendName> = emptyList(),
        frequencies: Collection<FrequencyName> = emptyList()
    ): Boolean

    /** Returns whether DB is empty. */
    suspend fun isDbEmpty(): Boolean

    /** Get compound names that were created or updated after some [timestamp]. */
    suspend fun getCompoundNamesCreatedOrUpdatedAfterTimestamp(timestamp: Instant): ListAsSortedSet<CompoundName>

    /** Get blend names that were created or updated after some [timestamp]. */
    suspend fun getBlendNamesCreatedOrUpdatedAfterTimestamp(timestamp: Instant): ListAsSortedSet<BlendName>

    /** Get frequency names that were created or updated after some [timestamp]. */
    suspend fun getFrequencyNamesCreatedOrUpdatedAfterTimestamp(timestamp: Instant): ListAsSortedSet<FrequencyName>

    fun DataSourceDelegate.uoe(): Nothing = throw UnsupportedOperationException()

    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation", "ClassName")
    object DELETE_ALL_COMPOUNDS: Set<CompoundName> by emptySet()
    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation", "ClassName")
    object DELETE_ALL_BLENDS: Set<BlendName> by emptySet()
    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation", "ClassName")
    object DELETE_ALL_FREQUENCIES: Set<FrequencyName> by emptySet()
}