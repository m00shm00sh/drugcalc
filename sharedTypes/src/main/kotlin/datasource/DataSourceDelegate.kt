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
import kotlin.time.Instant

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
    /** Update multiple compounds.
     *
     * Returns true if any value was updated.
     */
    suspend fun updateCompounds(compounds: Map<CompoundName, ProxyMap<CompoundInfo>>): Boolean

    /** Get the list of all compound names. */
    suspend fun getCompoundNames(): ListAsSortedSet<CompoundBase>

    /** Get the list of all matching variants for a compound. */
    suspend fun getVariantsForCompound(forName: CompoundBase): ListAsSortedSet<String>?

    /** Get info map pertaining to multiple compound names.
     *
     * @return map where each key is a matching compound and value is an info record
     */
    suspend fun getCompounds(names: Collection<CompoundName>): Map<CompoundName, CompoundInfo>

    /** Delete all compounds in [names].
     *
     * Returns whether the operation deleted one or more matching entries.
     */
    suspend fun deleteCompounds(names: Collection<CompoundName>): Boolean =
        deleteBulk(compounds = names)

    /** Delete all compounds. */
    suspend fun deleteAllCompounds(): Boolean =
        deleteCompounds(DELETE_ALL_COMPOUNDS)

    /** Get the list of all blend names. */
    suspend fun getBlendNames(): ListAsSortedSet<BlendName>

    /** Get components map pertaining to multiple blend [names].
     *
     * @return map where each key is a matching blend and value is a map of components
     */
    suspend fun getBlends(names: Collection<BlendName>): Map<BlendName, BlendValue>

    /** Delete all blends in [names]. Returns whether the operation deleted one or more entries. */
    suspend fun deleteBlends(names: Collection<BlendName>): Boolean =
        deleteBulk(blends = names)

    /** Delete all blends. */
    suspend fun deleteAllBlends(): Boolean =
        deleteBlends(DELETE_ALL_BLENDS)

    /** Get the list of all frequency names. */
    suspend fun getFrequencyNames(): ListAsSortedSet<FrequencyName>

    /** Get items map pertaining to multiple frequency names.
     *
     * @return map where each key is a matching frequency and value is a list of items
     */
    suspend fun getFrequencies(names: Collection<FrequencyName>): Map<FrequencyName, FrequencyValue>

    /** Delete all frequencies matching name. Returns whether the operation deleted one or more matching entries. */
    suspend fun deleteFrequencies(names: Collection<FrequencyName>): Boolean =
        deleteBulk(frequencies = names)

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

    /** Release any resources held by this delegate (for example, a connection pool). */
    fun close() {}

    fun DataSourceDelegate.uoe(): Nothing = throw UnsupportedOperationException()

    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation", "ClassName")
    object DELETE_ALL_COMPOUNDS: Set<CompoundName> by emptySet()
    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation", "ClassName")
    object DELETE_ALL_BLENDS: Set<BlendName> by emptySet()
    @Suppress("JavaDefaultMethodsNotOverriddenByDelegation", "ClassName")
    object DELETE_ALL_FREQUENCIES: Set<FrequencyName> by emptySet()
}