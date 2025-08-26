package com.moshy.drugcalc.cmdclient.helpers

import kotlin.reflect.KProperty

internal fun id(s: String) = s to s
internal fun name(prop: KProperty<*>) = prop.name
internal fun aliasName(alias: String, prop: KProperty<*>) = alias to prop.name
