package com.moshy.drugcalc.util

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.addEnvironmentSource
import com.sksamuel.hoplite.addResourceSource
import com.zaxxer.hikari.HikariDataSource
import kotlin.time.Duration

internal object AppConfig {
    data class Server(val host: String, val port: Int)

    data class Limits(
        val requestBodyLimit: Long = (1L shl 20), // 1 MiB
        val sendUnexpectedExceptionStackTrace: Boolean = false
    )

    data class Repo(
        val readOnlyMode: Boolean = false,
        val objectCachePolicy: CacheEvictionPolicy,
        val configCachePolicy: CacheEvictionPolicy,
    ) {
        data class CacheEvictionPolicy(val size: Long? = null, val accessTime: Duration? = null)
    }

    data class App(val server: Server, val limits: Limits, val db: HikariDataSource, val repo: Repo)

    val config =
    ConfigLoaderBuilder.default()
        .addEnvironmentSource()
        .addResourceSource("/app.conf")
        .build().loadConfigOrThrow<App>()
}