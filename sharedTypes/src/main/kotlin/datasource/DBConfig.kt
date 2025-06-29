package com.moshy.drugcalc.types.datasource

/** Generic database config. */
data class DBConfig(
    val driver: String,
    val url: String,
    val user: String? = null,
    val password: String? = null,
    val maxPoolSize: Int? = null,
    val connectTimeout: Long? = null,
)
