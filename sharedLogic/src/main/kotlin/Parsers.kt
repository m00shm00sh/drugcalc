package com.moshy.drugcalc.common

/** Produces true if lowercase input is any of "true", "y", "yes", or "1". */
fun String?.toTruthy(): Boolean =
    this?.run { this.lowercase() in listOf("true", "y", "yes", "1") } ?: false
