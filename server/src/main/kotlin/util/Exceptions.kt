package com.moshy.drugcalc.server.util

internal class AccessException : RuntimeException {
    constructor()
    constructor(message: String?) : super(message)
    constructor (message: String?, cause: Throwable?) : super(message, cause)
    constructor (cause: Throwable?) : super(cause)
}