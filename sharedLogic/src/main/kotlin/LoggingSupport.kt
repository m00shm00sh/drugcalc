package com.moshy.drugcalc.common

import org.slf4j.*

/** Lazy printable for logging.
 *
 * Usage: `logger.debug("thing = {}", lazyPrintable { /* possibly expensive computation */ })`
 */
inline fun lazyPrintable(crossinline block: () -> String) = object {
    override fun toString() = block()
}

fun logger(forName: String): Logger = LoggerFactory.getLogger(forName)
