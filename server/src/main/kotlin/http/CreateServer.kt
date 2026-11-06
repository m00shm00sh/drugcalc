package com.moshy.drugcalc.server.http

import com.moshy.drugcalc.common.logger
import com.moshy.drugcalc.server.util.*
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import kotlinx.coroutines.currentCoroutineContext

internal suspend fun createKtorServer(config: AppConfig.App): Server =
    createKtorServer(config, Netty) { }

internal suspend fun <Engine : ApplicationEngine, Configuration : ApplicationEngine.Configuration>
createKtorServer(
    config: AppConfig.App, // .http.bind[], .flags.devMode
    factory: ApplicationEngineFactory<Engine, Configuration>,
    serverConfigure: Configuration.() -> Unit = {},
): Server {
    val context = currentCoroutineContext()
    val props = serverConfig(applicationEnvironment {
        log = logger("ktor")
    }) {
        // TODO: watch paths for hot reload
        parentCoroutineContext = context
        developmentMode = config.flags.httpDevMode
        module {
            initializeKtorModule(config)
        }
    }

    val connectors = config.http.bind.map { b ->
        EngineConnectorBuilder().apply {
            host = b.host
            port = b.port
        }
    }

    val server = embeddedServer(factory,props){
        this.connectors.addAll(connectors)
        serverConfigure()
    }

    return object : Server {
        override fun start(wait: Boolean): Server {
            server.start(wait)
            return this
        }

        override fun stop(gracePeriodMillis: Long, timeoutMillis: Long) {
            server.stop(gracePeriodMillis, timeoutMillis)
        }
    }
}

private val logger = logger("createServer")
