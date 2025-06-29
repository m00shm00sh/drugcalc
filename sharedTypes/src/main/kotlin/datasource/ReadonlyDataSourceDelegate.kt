package com.moshy.drugcalc.types.datasource

import com.moshy.ProxyMap
import com.moshy.drugcalc.types.dataentry.BlendName
import com.moshy.drugcalc.types.dataentry.BlendValue
import com.moshy.drugcalc.types.dataentry.CompoundInfo
import com.moshy.drugcalc.types.dataentry.CompoundName
import com.moshy.drugcalc.types.dataentry.FrequencyName
import com.moshy.drugcalc.types.dataentry.FrequencyValue

/** Read-only delegate for an existing delegate.
 *
 * The following classes of operations throw [UnsupportedOperationException] without performing any operation:
 * - `putXxx`
 * - `updateXxx`
 * - `deleteXxx`
 */
class ReadonlyDataSourceDelegate(
    private val delegate: DataSourceDelegate
) : DataSourceDelegate by delegate {
    override suspend fun updateCompound(name: CompoundName, map: ProxyMap<CompoundInfo>): Boolean = uoe()
    override suspend fun updateCompounds(compounds: Map<CompoundName, ProxyMap<CompoundInfo>>): Boolean = uoe()

    override suspend fun putBulk(
        compounds: Map<CompoundName, CompoundInfo>,
        blends: Map<BlendName, BlendValue>,
        frequencies: Map<FrequencyName, FrequencyValue>
    ): Boolean = uoe()

    override suspend fun deleteBulk(
        compounds: Collection<CompoundName>,
        blends: Collection<BlendName>,
        frequencies: Collection<FrequencyName>
    ): Boolean = uoe()
}