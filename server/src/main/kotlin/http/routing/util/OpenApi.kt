package com.moshy.drugcalc.server.http.routing.util

import io.ktor.http.ContentType
import io.ktor.openapi.AdditionalProperties
import io.ktor.openapi.JsonSchema
import io.ktor.openapi.KotlinxSerializerJsonSchemaInference
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.openapi.Operation
import io.ktor.openapi.PathItem
import io.ktor.openapi.ReferenceOr
import io.ktor.openapi.RequestBody
import io.ktor.server.routing.openapi.OpenApiDocSource
import kotlinx.serialization.json.Json
import java.io.File

private val genericSchemaName = Regex("""^(.+)<(.+)>$""")

/**
 * Matches the OpenAPI 3.1 representation of a nullable type, e.g.
 *
 * ```
 * type:
 * - object
 * - "null"
 * ```
 *
 * The static HTML docs are rendered by swagger-codegen, which only understands OpenAPI 3.0.
 */
private val nullableTypeList = Regex("""(?m)^(\s*)type:\n\1- ([^\n]+)\n\1- "null"$""")

/**
 * Matches the OpenAPI 3.1 numeric form of `exclusiveMinimum`. OpenAPI 3.0 expresses the same
 * constraint as `minimum: X` plus `exclusiveMinimum: true`.
 */
private val numericExclusiveMinimum = Regex("""(?m)^(\s*)exclusiveMinimum:\s*([^\n]+)$""")

private const val SCHEMA_REF_PREFIX = "#/components/schemas/"

private val serializeYaml: (OpenApiDoc) -> String =
    OpenApiDocSource.Routing(contentType = ContentType.Application.Yaml).serializeModel

/**
 * Base document used for route-derived OpenAPI generation.
 *
 * The version is 3.0.3 rather than Ktor's default 3.1.1 because the built-in HTML documentation
 * renderer (swagger-codegen) only supports OpenAPI 3.0. With a 3.1 document it drops every
 * schema `type`, so parameters render as "any"/undefined even though the YAML is valid.
 */
internal fun openApiBaseDoc(): OpenApiDoc =
    OpenApiDoc.build {
        openapiVersion = "3.0.3"
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
            val described = doc.copy(webhooks = null).describeSchemas().describePaths()
            toOpenApi30(renameSchemaComponents(described, serializeYaml(described)))
        },
    )

/**
 * Rewrites the few OpenAPI 3.1-only constructs Ktor emits into their OpenAPI 3.0 equivalents.
 *
 * Ktor represents nullable schemas as a `type` list containing `"null"` and exclusive bounds as
 * numeric values. OpenAPI 3.0 instead uses `nullable: true` and a boolean `exclusiveMinimum`.
 */
private fun toOpenApi30(yaml: String): String =
    nullableTypeList.replace(yaml) { match ->
        val indent = match.groupValues[1]
        val type = match.groupValues[2]
        "${indent}type: $type\n${indent}nullable: true"
    }.let { text ->
        numericExclusiveMinimum.replace(text) { match ->
            val indent = match.groupValues[1]
            val bound = match.groupValues[2]
            "${indent}minimum: $bound\n${indent}exclusiveMinimum: true"
        }
    }

/**
 * Adds human-readable semantics and constraints that cannot be inferred from Kotlin types alone.
 */
private fun OpenApiDoc.describeSchemas(): OpenApiDoc {
    val components = components ?: return this
    val schemas = components.schemas ?: return this
    val cycleRequest = schemas.values.firstOrNull { it.title == "CycleRequest" }
    val cycleDataName = cycleRequest?.properties?.get("data").refSchemaName()
    val configName = cycleRequest?.properties?.get("config").refSchemaName()

    val described = schemas.mapValues { (name, schema) ->
        when {
            name == cycleDataName -> schema
                .withDescription("Optional auxiliary data used to resolve names in a cycle.")
                .updatePropertyDescriptions(
                    "compounds" to
                        "Map from an auxiliary compound name (key) to its [CompoundInfo](#/components/schemas/CompoundInfo) (value).",
                    "blends" to
                        "Map from an auxiliary blend name (key) to its [BlendValue](#/components/schemas/BlendValue) (value).",
                    "frequencies" to
                        "Map from an auxiliary frequency name (key) to its [FrequencyValue](#/components/schemas/FrequencyValue) (value).",
                )

            name == configName -> schema
                .withDescription("Calculator configurables.")
                .updatePropertyDescriptions(
                    "tickDuration" to
                        "The duration that is elapsed between two consecutive dose values. Must " +
                        "represent a positive duration; ISO-8601 or other supported duration " +
                        "formats (for example \"1h 30m\") are accepted.",
                    "cutoffMilligrams" to
                        "Do not evaluate further decay of compound once active dose is below this " +
                        "number. Must be a positive number.",
                    "doLambdaDoseCorrection" to
                        "The canonical iteration of the formula online has the lambda correction " +
                        "of dose and not just time; if the mathematically correct value is desired, " +
                        "set this to false.",
                )
                .updateProperty("cutoffMilligrams") { property ->
                    property.copy(exclusiveMinimum = 0.0)
                }

            schema.title == "CycleRequest" -> schema.updatePropertyDescriptions(
                "cycle" to "Cycle steps to evaluate; must be non-empty.",
                "data" to "Optional auxiliary data used to resolve names in the cycle.",
                "config" to "Optional calculator configuration overrides.",
                "decode" to
                    "How to decode the result. Accepted values are \"none\", \"duration\", or an " +
                    "ISO-8601 duration string (for example \"PT24H\") that defines a custom day length.",
            )

            schema.title == "CycleDescription" -> schema
                .withDescription("Describes a cycle of a compound.")
                .updatePropertyDescriptions(
                    "prefix" to
                        "Optional entry kind: omit or null for a compound, \".b\" for a blend, or " +
                        "\".t\" for a transformer.",
                    "compoundOrBlend" to
                        "Compound base name (CompoundBase) or blend name (BlendName), depending on " +
                        "prefix. Names must be non-empty and must not start with '.'. CompoundBase " +
                        "also must not contain '=' or ':'; BlendName must not contain '='.",
                    "variantOrTransformer" to
                        "Optional compound variant when prefix is omitted or null; must be empty " +
                        "for blends; transformer name when prefix is \".t\" (required and non-empty).",
                    "dose" to
                        "Required and positive when prefix is not \".t\" (compound or blend); " +
                        "ignored for transformers.",
                    "start" to
                        "Duration string; must represent a nonnegative duration. ISO-8601 or other " +
                        "supported duration formats (for example \"1h 30m\") are accepted.",
                    "duration" to
                        "Duration string; must represent a positive duration. ISO-8601 or other " +
                        "supported duration formats (for example \"1h 30m\") are accepted.",
                    "freqName" to
                        "A valid FrequencyName string (non-empty); must exist in the frequencies data.",
                    "group" to "Group number; must be nonnegative; defaults to 0.",
                    "doseRamp" to
                        "Optional dose ramping specification; only valid for compounds and blends. " +
                        "When present, addDose must be finite and non-zero (positive to ramp up, " +
                        "negative to ramp down).",
                )

            schema.title == "Data" -> schema.updatePropertyDescriptions(
                "compounds" to "Map from compound name (key) to its [CompoundInfo](#/components/schemas/CompoundInfo) (value).",
                "blends" to "Map from blend name (key) to its [BlendValue](#/components/schemas/BlendValue) (value).",
                "frequencies" to "Map from frequency name (key) to its [FrequencyValue](#/components/schemas/FrequencyValue) (value).",
            )

            schema.title == "CompoundInfo" -> schema
                .withDescription(
                    "CompoundInfo describes a compound: a positive half-life, a percent active " +
                        "dose in (0.0, 1.0], and an optional note."
                )
                .updatePropertyDescriptions(
                    "halfLife" to "Half-life duration string; must be positive.",
                    "pctActive" to "Percent active dose; value in (0.0, 1.0].",
                    "note" to "Miscellaneous note.",
                )

            // The component is still titled with its pre-rename generic name at this point.
            schema.title in setOf("ProxyMap<CompoundInfo>", "ProxyMapOfCompoundInfo") -> schema
                .withDescription("Partial CompoundInfo update; omitted fields are unchanged.")
                .updatePropertyDescriptions(
                    "halfLife" to "Half-life duration string; must be positive.",
                    "pctActive" to "Percent active dose; value in (0.0, 1.0].",
                    "note" to "Miscellaneous note.",
                )

            schema.title == "BlendValue" -> schema
                .withDescription(
                    "BlendValue describes a blend: at least two compound components with positive " +
                        "amounts, plus an optional note."
                )
                .updatePropertyDescriptions(
                    "note" to "Miscellaneous note.",
                )
                .updateProperty("components") { components ->
                    val additionalProperties =
                        (components.additionalProperties as? AdditionalProperties.PSchema)?.let { schema ->
                            AdditionalProperties.PSchema(
                                schema.value.mapValue {
                                    it.copy(
                                        description = "Positive numeric component value.",
                                        exclusiveMinimum = 0.0,
                                    )
                                }
                            )
                        } ?: components.additionalProperties
                    components.copy(
                        description =
                            "Map from compound name (key, serialized as `base` or `base=variant`) " +
                                "to its positive numeric component value. At least two components " +
                                "are required.",
                        minProperties = 2,
                        additionalProperties = additionalProperties,
                    )
                }

            schema.title == "FrequencyValue" -> schema
                .withDescription(
                    "FrequencyValue describes a frequency: a non-empty list of positive durations " +
                        "between doses."
                )
                .updatePropertyDescriptions(
                    "values" to "Durations between doses; each must be positive.",
                )

            schema.title == "RampedDose" -> schema
                .withDescription(
                    "A stepwise dose ramp. The dose administered at time t is base + k * addDose, " +
                        "where k is the number of completed addFreqName intervals at t. The first " +
                        "dose is the base dose."
                )
                .updatePropertyDescriptions(
                    "addDose" to
                        "Amount to add per ramp interval, in milligrams; must be finite and " +
                        "non-zero. May be positive (ramp up) or negative (ramp down).",
                    "addFreqName" to
                        "Ramp interval frequency name; must be a valid FrequencyName string " +
                        "(non-empty) that exists in the frequencies data.",
                )

            schema.title == "TransformerInfo" -> schema
                .withDescription("Describes info and usage for a transformer.")
                .updatePropertyDescriptions(
                    "info" to "Human-readable description of what the transformer does.",
                    "usage" to "Usage template for the transformer, for example `<compound>`.",
                )

            schema.title?.startsWith("XYList") == true -> schema
                .withDescription(
                    "XYList describes a tagged XY pair that gives information on (x,y) plotting " +
                        "type (bar vs point). The concrete variant is selected by `xType`."
                )
                .updatePropertyDescriptions(
                    "plotType" to "Plotting type; see PlotType.",
                )

            else -> schema
        }
    }

    return copy(components = components.copy(schemas = described))
}

private fun JsonSchema.withDescription(description: String): JsonSchema =
    if (this.description == null) copy(description = description) else this

private fun JsonSchema.updatePropertyDescriptions(
    vararg descriptions: Pair<String, String>,
): JsonSchema =
    descriptions.fold(this) { schema, (name, description) ->
        schema.updateProperty(name) { it.copy(description = description) }
    }

private fun JsonSchema.updateProperty(
    name: String,
    transform: (JsonSchema) -> JsonSchema,
): JsonSchema {
    val currentProperties = properties ?: return this
    if (name !in currentProperties) return this
    return copy(
        properties = currentProperties.mapValues { (key, value) ->
            if (key == name) value.mapValue(transform) else value
        }
    )
}

private val pathParameterDescriptions = mapOf(
    "_compound" to "URL-encoded compound base name (CompoundBase).",
    "_variant" to "URL-encoded compound variant name.",
    "_blend" to "URL-encoded blend name (BlendName).",
    "_frequency" to "URL-encoded frequency name (FrequencyName).",
    "_transformer" to "URL-encoded transformer name.",
    "_transformerFreq" to "URL-encoded transformer frequency name (FrequencyName).",
)

/**
 * Adds semantics for path parameters and for inline map schemas that only exist on operations.
 */
private fun OpenApiDoc.describePaths(): OpenApiDoc {
    if (paths.isEmpty()) return this
    return copy(
        paths = paths.mapValues { (path, pathRef) ->
            pathRef.mapValue { item ->
                item.updateOperations { method, operation -> operation.describeOperation(path, method) }
            }
        }
    )
}

private fun PathItem.updateOperations(transform: (String, Operation) -> Operation): PathItem =
    copy(
        get = get?.let { transform("get", it) },
        put = put?.let { transform("put", it) },
        post = post?.let { transform("post", it) },
        delete = delete?.let { transform("delete", it) },
        options = options?.let { transform("options", it) },
        head = head?.let { transform("head", it) },
        patch = patch?.let { transform("patch", it) },
        trace = trace?.let { transform("trace", it) },
    )

private fun Operation.describeOperation(path: String, method: String): Operation {
    val withParameters = copy(
        parameters = parameters?.map { parameterRef ->
            parameterRef.mapValue { parameter ->
                pathParameterDescriptions[parameter.name]
                    ?.let { parameter.copy(description = it) }
                    ?: parameter
            }
        }
    )

    return when (path to method) {
        "/api/data" to "post" -> withParameters.updateRequestBody { body ->
            body.copy(
                description = "Data entries to insert. Each map is keyed by the corresponding " +
                    "compound, blend, or frequency name."
            )
        }

        "/api/data/compounds" to "post" -> withParameters.updateRequestBodySchema {
            it.copy(description = "Map from compound name (key) to its [CompoundInfo](#/components/schemas/CompoundInfo) (value).")
        }

        "/api/data/compounds" to "patch" -> withParameters.updateRequestBodySchema {
            it.copy(
                description =
                    "Map from compound name (key) to a partial " +
                        "[CompoundInfo](#/components/schemas/CompoundInfo) update."
            )
        }

        "/api/data/compounds" to "get" -> withParameters.updateResponseSchema(200) {
            it.describeArrayItems("A compound base name (CompoundBase).")
        }

        "/api/data/compounds/{_compound}" to "post" -> withParameters.updateRequestBodySchema {
            it.copy(
                description = "Map from compound variant name (key) to its [CompoundInfo](#/components/schemas/CompoundInfo) (value)."
            )
        }

        "/api/data/compounds/{_compound}" to "get" -> withParameters.updateResponseSchema(200) {
            it.describeArrayItems("A compound variant name.")
        }

        "/api/data/blends" to "post" -> withParameters.updateRequestBodySchema {
            it.copy(description = "Map from blend name (key) to its [BlendValue](#/components/schemas/BlendValue) (value).")
        }

        "/api/data/blends" to "get" -> withParameters.updateResponseSchema(200) {
            it.describeArrayItems("A blend name (BlendName).")
        }

        "/api/data/frequencies" to "post" -> withParameters.updateRequestBodySchema {
            it.copy(description = "Map from frequency name (key) to its [FrequencyValue](#/components/schemas/FrequencyValue) (value).")
        }

        "/api/data/frequencies" to "get" -> withParameters.updateResponseSchema(200) {
            it.describeArrayItems("A frequency name (FrequencyName).")
        }

        "/api/data/frequencies.w" to "get" -> withParameters.updateResponseSchema(200) {
            it.copy(description = "Map from frequency name (key) to its total duration (value).")
        }

        "/api/data/transformers/names" to "get" -> withParameters.updateResponseSchema(200) {
            it.describeArrayItems("A transformer name.")
        }

        "/api/data/transformers/frequencies" to "get" -> withParameters.updateResponseSchema(200) {
            it.describeArrayItems("A transformer frequency name (FrequencyName).")
        }

        "/api/calc" to "post" -> withParameters.updateResponseSchema(200) {
            it.copy(
                description = "Map from result series name (key) to its [XYList](#/components/schemas/XYList) (value). The " +
                    "`xType` discriminator selects the concrete XYList variant."
            )
        }

        else -> withParameters
    }
}

private fun JsonSchema.describeArrayItems(description: String): JsonSchema =
    copy(items = items?.mapValue { it.copy(description = description) })

private fun Operation.updateRequestBody(transform: (RequestBody) -> RequestBody): Operation =
    copy(requestBody = requestBody?.mapValue(transform))

private fun Operation.updateRequestBodySchema(transform: (JsonSchema) -> JsonSchema): Operation =
    updateRequestBody { body ->
        body.copy(
            content = body.content?.mapValues { (_, media) ->
                media.copy(schema = media.schema?.mapValue(transform))
            }
        )
    }

private fun Operation.updateResponseSchema(
    status: Int,
    transform: (JsonSchema) -> JsonSchema,
): Operation {
    val currentResponses = responses ?: return this
    val responseMap = currentResponses.responses ?: return this
    val responseRef = responseMap[status] ?: return this
    val updated = responseRef.mapValue { response ->
        response.copy(
            content = response.content?.mapValues { (_, media) ->
                media.copy(schema = media.schema?.mapValue(transform))
            }
        )
    }
    return copy(responses = currentResponses.copy(responses = responseMap + (status to updated)))
}

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

private const val SCHEMA_ANCHOR_MARKER = "<!-- drugcalc-schema-anchors:start -->"

private object HtmlDocs

/**
 * Adds a Models section to the generated static HTML documentation.
 *
 * swagger-codegen's template only renders API operations; component schemas live in a JavaScript
 * map used for inline expansion. As a result, the `#/components/schemas/...` links written in
 * descriptions have no navigation target. This appends a section (rendered from that same map)
 * whose anchors use the link fragments as ids, so the links resolve.
 *
 * Must run after the HTML has been generated into [outputPath].
 */
internal fun addSchemaAnchorsToHtml(outputPath: String = "docs") {
    val htmlFile = File(outputPath, "index.html")
    if (!htmlFile.isFile) return
    val html = htmlFile.readText()
    if (SCHEMA_ANCHOR_MARKER in html) return
    val snippet = HtmlDocs::class.java
        .getResourceAsStream("/openapi/html-models.html")
        ?.bufferedReader()
        ?.use { it.readText() }
        ?: return
    val updated = if ("<div id=\"footer\">" in html) {
        html.replaceFirst("<div id=\"footer\">", "$snippet\n          <div id=\"footer\">")
    } else {
        html.replace("</body>", "$snippet\n</body>")
    }
    htmlFile.writeText(updated)
}
