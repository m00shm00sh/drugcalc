package com.moshy.drugcalc.cmdclient.states

import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.common.logger
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import java.lang.IllegalArgumentException
import java.security.cert.X509Certificate
import javax.net.ssl.*

internal class ForConnect {
    data class ConnectParams(
        val addr: String,
        val insecure: Boolean = false,
    ) {
        fun makeConnectableUrl(path: String): String {
            return "$addr/$path"
        }

    }
    private val logger = logger("${AppState.NAME}:connect")

    var connect: ConnectParams? = null
    private var client: HttpClient? = null

    suspend fun getClient(): HttpClient {
        client?.let { return it }
        val connectParams = connect ?: throw IllegalArgumentException("unconfigured connect")

        val client = HttpClient(Java) {
            if (connectParams.insecure)
                engine {
                    config {
                        sslContext(insecureTlsContext())
                    }
                }
            install(ContentNegotiation) {
                json()
            }
        }
        val req = client.get(connectParams.makeConnectableUrl("/api/"))
        require(req.status == HttpStatusCode.OK) {
            throw RuntimeException("heartbeat: http ${req.status}")
        }
        val hbResult = req.body<String>()
        require(hbResult == "OK") {
            "unexpected heartbeat result: $hbResult"
        }
        this.client = client
        return client
    }

    fun clearClient() {
        client = null
    }
}

private fun defaultPortForProto(s: String) =
    when (s) {
        "http" -> 80
        "https" -> 443
        else -> throw IllegalArgumentException("no default port for proto $s")
    }

private fun insecureTlsContext() =
    SSLContext.getInstance("TLS").apply {
        val tm = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate?>?, typ: String?) { }
            override fun checkServerTrusted(chain: Array<out X509Certificate?>?, typ: String?) { }
            override fun getAcceptedIssuers(): Array<out X509Certificate?>? = null
        }
        init(null, arrayOf<TrustManager>(tm), null)
    }