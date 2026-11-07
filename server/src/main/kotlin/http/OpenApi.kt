package com.moshy.drugcalc.server.http

import io.ktor.http.ContentType
import io.ktor.openapi.KotlinxSerializerJsonSchemaInference
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.ReferenceOr
import io.ktor.server.routing.openapi.OpenApiDocSource
import kotlinx.serialization.json.Json

private val genericSchemaName = Regex("""^(.+)<(.+)>$""")

private const val SCHEMA_REF_PREFIX = "#/components/schemas/"

private val serializeYaml: (OpenApiDoc) -> String =
    OpenApiDocSource.Routing(contentType = ContentType.Application.Yaml).serializeModel

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
 *
 * Schema component names are normalized so that Kotlin generic types are not exposed: `C<T>`
 * becomes `COfT`, and the optional `CycleRequest.data` schema is named `CycleData`. The empty
 * `webhooks` section that [OpenApiDoc.Builder] always creates is omitted.
 */
internal fun openApiRoutingSource(json: Json): OpenApiDocSource.Routing =
    OpenApiDocSource.Routing(
        contentType = ContentType.Application.Yaml,
        schemaInference = KotlinxSerializerJsonSchemaInference(json.serializersModule),
        serializeModel = { doc ->
            val withoutWebhooks = doc.copy(webhooks = null)
            renameSchemaComponents(withoutWebhooks, serializeYaml(withoutWebhooks))
        },
    )

/**
 * Applies component renames to the serialized document.
 *
 * Renaming the serialized text keeps every `$ref` (including references nested in discriminator
 * mappings) consistent without having to walk the entire OpenAPI model.
 */
private fun renameSchemaComponents(doc: OpenApiDoc, yaml: String): String {
    val schemas = doc.components?.schemas.orEmpty()
    val renames = buildMap {
        for (name in schemas.keys) {
            genericSchemaName.matchEntire(name)?.let { match ->
                val base = match.groupValues[1]
                val argument = match.groupValues[2].substringAfterLast('.')
                put(name, "${base}Of$argument")
            }
        }
        // `Data` is documented both as a required request body and as CycleRequest's optional
        // `data` property; give the latter a distinct component name instead of the collision
        // suffix that Ktor would otherwise generate.
        schemas.values
            .firstOrNull { it.title == "CycleRequest" }
            ?.properties
            ?.get("data")
            .refSchemaName()
            ?.let { put(it, "CycleData") }
    }
    if (renames.isEmpty()) return yaml
    return renames.entries.fold(yaml) { text, (from, to) -> text.replace(from, to) }
}

private fun ReferenceOr<*>?.refSchemaName(): String? =
    (this as? ReferenceOr.Reference)
        ?.ref
        ?.takeIf { it.startsWith(SCHEMA_REF_PREFIX) }
        ?.removePrefix(SCHEMA_REF_PREFIX)
