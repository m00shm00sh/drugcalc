package com.moshy.drugcalc.calc.datacontroller

import com.moshy.drugcalc.types.dataentry.*
import com.moshy.ProxyMap

internal fun String.clean() = trim().lowercase()

internal fun CompoundBase.clean() = CompoundBase(value.trim().lowercase())

internal fun CompoundName.clean() =
    copy(compound.clean(), variant.clean())

internal fun BlendName.clean() =
    BlendName(value.clean())

internal fun BlendValue.clean() =
    BlendValue(mapKeys { (k, _) -> k.clean() })

internal fun FrequencyName.clean() =
    FrequencyName(value.clean())

// used by upserters in DataController
@JvmName("clean\$CompoundMap")
internal fun Map<CompoundName, CompoundInfo>.clean() = mapKeys { (k, _) -> k.clean() }

@JvmName("clean\$BlendMap")
internal fun Map<BlendName, BlendValue>.clean() = entries.associate { (k, v) -> k.clean() to v.clean() }

@JvmName("clean\$FrequencyMap")
internal fun Map<FrequencyName, FrequencyValue>.clean() = mapKeys { (k, _) -> k.clean() }

// used by updaters in DataController
@JvmName("clean\$CompoundUpdateMap")
internal fun Map<CompoundName, ProxyMap<CompoundInfo>>.clean() = mapKeys { (k, _) -> k.clean() }

// used by deleters in DataController
@JvmName("clean\$CompoundNameList")
internal fun Collection<CompoundName>.clean() = map { it.clean() }

@JvmName("clean\$BlendNameList")
internal fun Collection<BlendName>.clean() = map { it.clean() }

@JvmName("clean\$FrequencyNameList")
internal fun Collection<FrequencyName>.clean() = map { it.clean() }
