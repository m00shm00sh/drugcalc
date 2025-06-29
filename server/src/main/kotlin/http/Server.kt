package com.moshy.drugcalc.server.http

import org.jetbrains.annotations.Blocking

/** Generic HTTP server interface. */
internal interface Server {
    @Blocking
    fun start(wait: Boolean = false): Server

    @Blocking
    fun stop(
        gracePeriodMillis: Long = 1000,
        timeoutMillis: Long = 1000
    )
}
