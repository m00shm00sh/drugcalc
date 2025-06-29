package com.moshy.drugcalc.server.http.user

import kotlinx.serialization.Serializable

@Serializable
internal data class UserRequest(
    val name: String,
    val pass: String,
) {
    override fun toString() =
       "UserRequest(${name}, *****)"
}

internal data class User(
    val name: String,
    val pass: String,
    val isAdmin: Boolean = false
) {
    override fun toString() =
        "User(${name}, *****, admin=${isAdmin})"
}
