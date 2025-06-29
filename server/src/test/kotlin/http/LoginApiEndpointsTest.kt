package com.moshy.drugcalc.server.http

import com.moshy.drugcalc.server.http.util.*
import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO_ALL
import io.ktor.client.call.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.*
// unstarred import so it takes precedence for assert(DoesNotThrow|Throws)
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.Test

internal class LoginApiEndpointsTest {

    /*
     * /api/login/ POST
     */

    @Test
    fun testPostLogin() = withServer(RO_ALL, users) {
        get("/api/-test-a-/role") {
            bearerAuth(loginAdmin(users))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = assertDoesNotThrow { body<Map<String, String>>() }
            assertEquals("true", body["admin"])
        }
        get("/api/-test-a-/role") {
            bearerAuth(loginOther(users))
        }.apply {
            assertEquals(HttpStatusCode.OK, status)
            val body = assertDoesNotThrow { body<Map<String, String>>() }
            assertEquals("false", body["admin"])
        }
    }

    companion object {
        private val users = runBlocking { testingUserService() }
    }
}