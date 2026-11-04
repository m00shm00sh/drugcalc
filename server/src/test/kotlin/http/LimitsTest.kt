package com.moshy.drugcalc.server.http

import com.moshy.drugcalc.calctest.DataControllerTestSupport.Companion.RO_ALL
import com.moshy.drugcalc.server.http.util.Method
import com.moshy.drugcalc.server.http.util.defaultTestLimits
import com.moshy.drugcalc.server.http.util.testEndpoint
import com.moshy.drugcalc.server.http.util.withServer
import com.moshy.drugcalc.server.util.AppConfig
import com.moshy.drugcalc.types.login.UserRequest
import io.ktor.client.request.*
import io.ktor.http.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.hours

private fun rateLimit(num: Int) = AppConfig.Http.Limits.RateLimit(num, 1.hours)

internal class LimitsTest {

    @Test
    fun testClientRateLimit() = withServer(
        RO_ALL,
        limits = defaultTestLimits().copy(
            // only the first two requests from this client are allowed
            clientRateLimit = rateLimit(2),
        )
    ) {
        get("/api/data/compounds").apply {
            assertEquals(HttpStatusCode.OK, status)
            assertEquals("2", headers["X-RateLimit-Limit"])
        }
        assertEquals(HttpStatusCode.OK, get("/api/data/blends").status)
        get("/api/data/frequencies").apply {
            assertEquals(HttpStatusCode.TooManyRequests, status)
            assertNotNull(headers[HttpHeaders.RetryAfter], "rate-limited response should include Retry-After")
        }
    }

    @Test
    fun testGlobalRateLimit() = withServer(
        RO_ALL,
        limits = defaultTestLimits().copy(
            // the shared bucket allows only two requests in total
            globalRateLimit = rateLimit(2),
        )
    ) {
        // the shared bucket spans different endpoints
        assertEquals(HttpStatusCode.OK, get("/api/data/compounds").status)
        assertEquals(HttpStatusCode.OK, get("/api/data/blends").status)
        get("/api/data/frequencies").apply {
            assertEquals(HttpStatusCode.TooManyRequests, status)
            assertNotNull(headers[HttpHeaders.RetryAfter], "rate-limited response should include Retry-After")
        }
    }

    @Test
    fun testHeartbeatExcludedFromRateLimits() = withServer(
        RO_ALL,
        limits = defaultTestLimits().copy(
            // heartbeat must not consume either single token
            globalRateLimit = rateLimit(1),
            clientRateLimit = rateLimit(1),
        )
    ) {
        // heartbeat is neither blocked by nor consumes either bucket
        repeat(3) {
            assertEquals(HttpStatusCode.OK, get("/api").status)
        }
        assertEquals(HttpStatusCode.OK, get("/api/").status)
        // the first real API request consumes the single token from both buckets
        assertEquals(HttpStatusCode.OK, get("/api/data/compounds").status)
        assertEquals(HttpStatusCode.TooManyRequests, get("/api/data/blends").status)
        // heartbeat still works after both buckets are exhausted
        assertEquals(HttpStatusCode.OK, get("/api").status)
    }

    @Test
    fun testRequestBodyLimit() = withServer(
        RO_ALL,
        limits = defaultTestLimits().copy(
            // small enough that the oversized request below is rejected
            requestBodyLimit = 64,
        )
    ) {
        testEndpoint<UserRequest, Unit>(
            Method.PostJson, "/api/login",
            reqBody = UserRequest("a".repeat(64), "b".repeat(64)),
            responseCode = HttpStatusCode.PayloadTooLarge,
            message = "{} (oversized request body)"
        )
        testEndpoint<UserRequest, Unit>(
            Method.PostJson, "/api/login",
            reqBody = UserRequest("no-such-user", "no-such-pass"),
            responseCode = HttpStatusCode.Unauthorized,
            message = "{} (request body within limit)"
        )
    }
}
