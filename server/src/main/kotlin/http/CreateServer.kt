package com.moshy.drugcalc.server.http

import com.moshy.drugcalc.common.logger
import com.moshy.drugcalc.server.util.*
import io.ktor.server.application.serverConfig
import io.ktor.server.engine.*
import io.ktor.server.netty.Netty
import kotlin.coroutines.coroutineContext
import kotlin.system.exitProcess

internal suspend fun createKtorServer(config: AppConfig.App): Server =
    createKtorServer(config, getSslKeys(config), Netty) { }

private fun getSslKeys(config: AppConfig.App): List<KeystoreWithAlias> =
    buildList {
        try {
            config.http.sslKey?.let {
                if (it.pemPath.size == 2)
                    add(loadPemKeystore(it))
            }
        } catch (e: IllegalArgumentException) {
            logger.error("invalid certificate: {}", e.toString())
            exitProcess(1)
        }
        if (config.http.sslKey?.selfSignedLocalhost == true)
            add(generateSelfSignedKeystoreForLocalhost())
    }

private fun List<KeystoreWithAlias>.forDomain(domain: String): KeystoreWithAlias? =
    firstOrNull {
        it.forDomains.any { d -> run {
            if (d == domain)
                return@run true
            if (!d.startsWith("*."))
                return@run false
            return@run (d.substring(2) == domain.substringAfter('.'))
        } }
    }

private fun KeystoreWithAlias.ktorBuilder() =
    EngineSSLConnectorBuilder(
        keyStore, alias, { keyPassword.toCharArray() },
        { keyPassword.toCharArray() }
    )

internal suspend fun <Engine : ApplicationEngine, Configuration : ApplicationEngine.Configuration>
createKtorServer(
    config: AppConfig.App, // .http.bind[], .flags.devMode
    sslKeys: List<KeystoreWithAlias>,
    factory: ApplicationEngineFactory<Engine, Configuration>,
    serverConfigure: Configuration.() -> Unit = {},
): Server {
    val context = coroutineContext
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

    val connectors = buildList {
        // implement xxxConnector ourselves to use ssl builder factory
        for (b in config.http.bind) {
            when {
                b.port > 0 && b.sslPort == 0 -> {
                    add(EngineConnectorBuilder().apply {
                        host = b.host
                        port = b.port
                    })
                }

                b.port == 0 && b.sslPort > 0 -> {
                    val ssl = sslKeys.forDomain(b.host)
                    if (ssl == null) {
                        logger.warn(
                            "ignoring listen on {}:{} because an SSL keystore could not be matched for host",
                            b.host, b.sslPort
                        )
                        continue
                    }
                    add(ssl.ktorBuilder().apply {
                        host = b.host
                        port = b.sslPort
                    })
                }
            }
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