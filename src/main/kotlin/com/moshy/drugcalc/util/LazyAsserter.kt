package com.moshy.drugcalc.util

@JvmField
internal val assertionsEnabled = object{}.javaClass.desiredAssertionStatus()

// Lazily evaluating asserter.
internal inline fun assert(lazyCondition: () -> Boolean, lazyMessage: () -> Any) {
    if (assertionsEnabled && !lazyCondition())
        throw AssertionError(lazyMessage())
}
