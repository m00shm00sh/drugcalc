package com.moshy.drugcalc.common

@JvmField
val assertionsEnabled = object {}.javaClass.desiredAssertionStatus()

// Lazily evaluating asserter.
inline fun assert(lazyCondition: () -> Boolean, lazyMessage: () -> Any) {
    if (assertionsEnabled && !lazyCondition())
        throw AssertionError(lazyMessage().toString())
}
