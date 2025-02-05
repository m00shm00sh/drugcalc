package com.moshy.drugcalc.io

import com.moshy.drugcalc.misc.XYList
import com.moshy.drugcalc.calc.CycleDescription
import com.moshy.drugcalc.misc.DecodedXYList
import kotlinx.serialization.Serializable

@Serializable
internal data class CycleRequest(
    val data: DiffData? = null,
    val config: ConfigMap? = null,
    val cycle: List<CycleDescription>
)

typealias CycleResult = Map<String, XYList>
typealias DecodedCycleResult = Map<String, DecodedXYList>