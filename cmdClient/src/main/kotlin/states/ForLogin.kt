package com.moshy.drugcalc.cmdclient.states

import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.cmdclient.io.AuthenticationFailure
import com.moshy.drugcalc.cmdclient.io.NetRequestMethod
import com.moshy.drugcalc.cmdclient.io.doRequest
import com.moshy.drugcalc.common.logger
import com.moshy.drugcalc.types.login.LoginResponse
import com.moshy.drugcalc.types.login.UserRequest
import java.lang.IllegalArgumentException

internal class ForLogin {
    val logger = logger("${AppState.Companion.NAME}:login")

    var login: UserRequest? = null
    private var token: String? = null

    suspend fun getLoginToken(app: AppState): String {
        token?.let { return it }
        login ?: throw IllegalArgumentException("unconfigured login")
        try {
            app.doRequest<LoginResponse, UserRequest>(NetRequestMethod.Post, "/api/login", body = login)
                .let {
                    token = it.token
                    return it.token
                }
        } catch (e: AuthenticationFailure) {
            login = null
            throw e
        }
    }

    fun clearLoginToken() {
        token = null
    }
}