package com.moshy.drugcalc.cmdclient.states

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.IOException
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.test.assertFailsWith

internal class ForConnectTest {
    @Test
    fun `default connect params target localhost`() {
        val params = ForConnect.ConnectParams()

        assertEquals("http://127.0.0.1:8080", params.addr)
    }

    @Test
    fun `makeConnectableUrl joins the address and path`() {
        val params = ForConnect.ConnectParams("http://example:1234")

        assertEquals("http://example:1234/api", params.makeConnectableUrl("api"))
    }

    @Test
    fun `makeConnectableUrl handles leading slash`() {
        val params = ForConnect.ConnectParams("http://example:1234")

        assertEquals("http://example:1234/api", params.makeConnectableUrl("/api"))
    }

    @Test
    fun `non-ok heartbeat status closes the client`() = runBlocking {
        val client = mockClient(HttpStatusCode.InternalServerError, "")

        assertFailsWith<RuntimeException> {
            ForConnect { _ -> client }.getClient()
        }

        assertClientClosed(client)
    }

    @Test
    fun `unexpected heartbeat body closes the client`() = runBlocking {
        val client = mockClient(HttpStatusCode.OK, "NO")

        assertFailsWith<IllegalArgumentException> {
            ForConnect { _ -> client }.getClient()
        }

        assertClientClosed(client)
    }

    @Test
    fun `io failure closes the client`() = runBlocking {
        val client = HttpClient(MockEngine { throw IOException("boom") }) {
            install(ContentNegotiation) {
                json()
            }
        }

        assertFailsWith<IOException> {
            ForConnect { _ -> client }.getClient()
        }

        assertClientClosed(client)
    }

    @Test
    fun `clearClient closes a cached client`() = runBlocking {
        val client = mockClient(HttpStatusCode.OK, "OK")
        val forConnect = ForConnect { _ -> client }

        assertSame(client, forConnect.getClient())
        assertClientOpen(client)

        forConnect.clearClient()

        assertClientClosed(client)
    }

    private fun mockClient(status: HttpStatusCode, body: String): HttpClient =
        HttpClient(MockEngine {
            respond(
                content = body,
                status = status,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }) {
            install(ContentNegotiation) {
                json()
            }
        }

    private fun assertClientOpen(client: HttpClient) {
        val job = client.coroutineContext[Job]
        assertNotNull(job, "client has no job")
        assertTrue(job!!.isActive, "client should be open")
    }

    private fun assertClientClosed(client: HttpClient) {
        val job = client.coroutineContext[Job]
        assertNotNull(job, "client has no job")
        assertFalse(job!!.isActive, "client should be closed after a failed heartbeat")
    }
}
