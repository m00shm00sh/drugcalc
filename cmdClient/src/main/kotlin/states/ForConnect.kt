package com.moshy.drugcalc.cmdclient.states

import com.moshy.drugcalc.cmdclient.io.resultOrThrow
import com.moshy.drugcalc.cmdclient.io.resultTextOrThrow
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.java.Java
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.io.IOException
import kotlinx.serialization.*
import java.security.cert.X509Certificate
import javax.net.ssl.*

@Serializable
internal class ForConnect(
    @Transient
    private val clientFactory: (ConnectParams) -> HttpClient = ::createClient,
) {
    @Serializable
    data class ConnectParams(
        val addr: String = "http://127.0.0.1:8080",
        val insecure: Boolean = false,
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
        } catch (e: IOException) {
            throw RuntimeException("GET heartbeat: ${e.message}", e)
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
        if (params.insecure)
            engine {
                config {
                    sslContext(insecureTlsContext())
                }
            }
        install(ContentNegotiation) {
            json()
        }
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