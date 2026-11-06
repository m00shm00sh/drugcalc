package com.moshy.drugcalc.cmdclient.states

import com.moshy.drugcalc.cmdclient.io.resultTextOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.*

@Serializable
internal class ForConnect(
    @Transient
    private val clientFactory: (ConnectParams) -> HttpClient = ::createClient,
) {
    @Serializable
    data class ConnectParams(
        val addr: String = "http://127.0.0.1:8080",
    ) {
        fun makeConnectableUrl(path: String): String {
            val addSlash = if (path.startsWith("/")) "" else "/"
            return "$addr$addSlash$path"
        }

    }

    var connect: ConnectParams = ConnectParams()
    @Transient
    private var client: HttpClient? = null

    suspend fun getClient(): HttpClient {
        client?.let { return it }
        val client = clientFactory(connect)
        var success = false
        try {
            val req = client.get(connect.makeConnectableUrl("/api/"))
            val hbResult = req.resultTextOrThrow()
            require(hbResult == "OK") {
                "unexpected heartbeat result: $hbResult"
            }
            this.client = client
            success = true
            return client
        } finally {
            if (!success)
                client.close()
        }
    }

    fun clearClient() {
        client?.close()
        client = null
    }
}

private fun createClient(params: ForConnect.ConnectParams): HttpClient =
    HttpClient(Java) {
        install(ContentNegotiation) {
            json()
        }
    }
