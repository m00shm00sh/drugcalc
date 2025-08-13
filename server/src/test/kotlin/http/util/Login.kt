package com.moshy.drugcalc.server.http.util

import com.moshy.drugcalc.server.http.LoginResponse
import com.moshy.drugcalc.server.http.user.*
import com.moshy.drugcalc.types.login.UserRequest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.http.HttpStatusCode
import org.junit.jupiter.api.Assertions.assertFalse
import kotlin.test.assertEquals

internal suspend fun HttpClient.getAccessToken(userService: UserService, user: String): String =
    requireNotNull(userService.findUser(user)).pass
        .let { pass ->
            postJson("/api/login") {
                setBody(UserRequest(user, pass))
            }.run {
                assertEquals(HttpStatusCode.OK, status)
                val token = body<LoginResponse>().token
                assertFalse(token.isEmpty())
                token
            }
        }

internal suspend fun HttpClient.loginAdmin(userService: UserService): String =
    getAccessToken(userService, TESTING_ADMIN)

internal suspend fun HttpClient.loginOther(userService: UserService): String =
    getAccessToken(userService, TESTING_OTHER)
