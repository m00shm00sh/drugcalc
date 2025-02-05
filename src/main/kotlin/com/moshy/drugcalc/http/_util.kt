package com.moshy.drugcalc.http

import io.ktor.server.routing.RoutingRequest

/** Get query parameter [name].
 *
 * If missing, return null.
 * If [converter] returns null due to failure, throw [IllegalArgumentException].
 */
// inline because we get NoSuchMethodException on Jvm otherwise
internal inline fun <T> RoutingRequest.getValidQueryParameter(name: String, converter: String.() -> T?): T? {
    val qpValue = queryParameters[name] ?: return null
    return requireNotNull(qpValue.converter()) {
        "invalid value for $name"
    }
}
