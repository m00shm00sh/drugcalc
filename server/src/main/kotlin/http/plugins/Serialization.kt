package com.moshy.drugcalc.server.http.plugins

import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import kotlinx.serialization.json.Json

/** install: [ContentNegotiation] */
internal fun Application.configureSerialization(jsonModule: Json) {
    install(ContentNegotiation) {
        json(jsonModule)
    }
}