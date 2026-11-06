package com.moshy.drugcalc.server.http.routing.util

import com.moshy.drugcalc.calc.datacontroller.PaginationSpecifier

internal fun <T : Any> paginationSpecifier(from: T?, limit: Int?): PaginationSpecifier<T>? {
    if (from == null)
        return null
    @Suppress("LocalVariableName") val ASC = PaginationSpecifier.Direction.AFTER
    if (limit == null)
        return PaginationSpecifier(seekAfter = from, direction = ASC)
    return PaginationSpecifier(limit = limit, seekAfter = from, direction = ASC)
}