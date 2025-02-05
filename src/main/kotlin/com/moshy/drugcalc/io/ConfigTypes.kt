package com.moshy.drugcalc.io

import com.moshy.drugcalc.calc.Config
import com.moshy.ProxyMap

internal typealias ConfigMap = ProxyMap<Config>
internal fun ConfigMap() = ProxyMap<Config>()
