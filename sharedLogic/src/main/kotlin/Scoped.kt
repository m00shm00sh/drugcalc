package com.moshy.drugcalc.common


/** Run if [condition] is true.
 *
 * Use case: pattern of `val x = f().run { if (p) g() else this }` => `val x = f().runIf(p) { g() }`
 */
inline fun <T> T.runIf(condition: Boolean, block: T.() -> T) =
    if (condition)
        block()
    else this
