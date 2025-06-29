package com.moshy.drugcalc.db.jooq

import java.util.function.*
import org.jetbrains.annotations.Blocking
import org.jooq.*

/** SAM conversion to facilitate lazy forEach instead of eager foreach.
 *
 * The issue with idiomatic Kotlin forEach is that it acts on the Iterable, which fetches all values due to
 * closability issues.
 *
 * This is necessary due to https://youtrack.jetbrains.com/issue/KT-39091.
 */
@Blocking
internal fun <T : Record> ResultQuery<T>.forEachLazy(action: (T) -> Unit) =
    forEach(Consumer(action))

/** Use with [AsyncJooq.suspendedBlockingTransaction] to get did-any-change-state. */
internal fun List<Int>.anyChanged(): Boolean = count { it > 0 } > 0