package com.moshy.drugcalc.types.login

import kotlinx.serialization.Serializable

@Serializable
data class LoginResponse(
    val token: String
)

@Serializable
data class UserRequest(
    val name: String,
    val pass: String,
) {
    override fun toString() =
        "UserRequest(${name}, *****)"
}
