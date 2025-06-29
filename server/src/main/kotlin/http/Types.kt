package com.moshy.drugcalc.server.http

import kotlinx.serialization.Serializable

@Serializable
internal data class LoginResponse(
    val token: String
)
