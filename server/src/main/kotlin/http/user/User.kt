package com.moshy.drugcalc.server.http.user

internal data class User(
    val name: String,
    val pass: String,
    val isAdmin: Boolean = false
) {
    override fun toString() =
        "User(${name}, *****, admin=${isAdmin})"
}
