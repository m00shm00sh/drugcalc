package com.moshy.drugcalc.util

import org.slf4j.Logger

/** Kotlin's check() but it calls $receiver.error() with the message before throwing the IllegalStateException. */
internal inline fun Logger.check(condition: Boolean, lazyMessage: () -> Any) {
    if (!condition) {
        val msg = lazyMessage().toString()
        error(msg)
        throw IllegalStateException(msg)
    }
}