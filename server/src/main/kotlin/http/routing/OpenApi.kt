package com.moshy.drugcalc.server.http.routing

import com.moshy.drugcalc.server.http.routing.util.addSchemaAnchorsToHtml
import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.events.Events
import io.ktor.server.application.ApplicationModulesLoaded
import io.ktor.server.plugins.openapi.openAPI
import io.ktor.server.routing.Route

internal fun Route.configureOpenApiDocRoute(
    flags: AppConfig.DevFlags,
    monitor: Events
) {
    openAPI("doc") {
        // StaticHtml2Codegen injects these HelloReVerb values by default; the
        // template only renders them when they are non-empty.
        codegen.additionalProperties()["infoUrl"] = "https://github.com/m00shm00sh/drugcalc"
        codegen.additionalProperties()["infoEmail"] = ""
    }
// The HTML template renders API operations but not component schemas, so
// links to `#/components/schemas/...` need anchors added after generation.
    monitor.subscribe(ApplicationModulesLoaded) {
        addSchemaAnchorsToHtml()
    }
}