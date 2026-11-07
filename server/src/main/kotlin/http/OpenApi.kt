package com.moshy.drugcalc.server.http

import io.ktor.http.ContentType
import io.ktor.openapi.KotlinxSerializerJsonSchemaInference
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.server.routing.openapi.OpenApiDocSource
import kotlinx.serialization.json.Json

/** Base document used for route-derived OpenAPI generation. */
internal fun openApiBaseDoc(): OpenApiDoc =
    OpenApiDoc.build {
        info = OpenApiInfo(
            title = "DrugCalc API",
            description = "DrugCalc application API",
            version = "1.0.0",
        )
        servers {
            server("http://0.0.0.0:8080")
        }
    }

/**
 * OpenAPI source that derives paths and schemas from the installed Ktor routing tree.
 *
 * The supplied [json] module is used for schema inference so contextual serializers (such as
 * the duration serializer) resolve the same way they do at runtime.
 */
internal fun openApiRoutingSource(json: Json): OpenApiDocSource.Routing =
    OpenApiDocSource.Routing(
        contentType = ContentType.Application.Yaml,
        schemaInference = KotlinxSerializerJsonSchemaInference(json.serializersModule),
    )
