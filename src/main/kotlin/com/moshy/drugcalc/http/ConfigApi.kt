package com.moshy.drugcalc.http

import com.moshy.drugcalc.io.ConfigMap
import com.moshy.drugcalc.repo.Repository

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

internal fun Route.configureConfigApiRoutes(repo: Repository) {
    suspend fun revConfig(rev: String?): ConfigMap =
        rev?.let {
            if (it == "latest")
                repo.getLatestConfig().second.data
            else {
                val revNum = it.toIntOrNull()
                require(revNum != null && revNum > 0) {
                    "invalid revision ($it)"
                }
                repo.getConfigAtRevision(revNum).data
            }
        } ?: error("unexpected null: rev")

    route("/config") {
        // this route handler handles both /latest and /{rev} because the logic is 95% similar
        get("/{rev}") {
            val rev = call.parameters["rev"]!!
            val data = revConfig(rev)
            call.respond(data)
        }
        get("/{rev}/diff") {
            val revParam = call.parameters["rev"]!!
            val rev = revParam.toIntOrNull() ?: throw IllegalArgumentException("invalid revision $revParam")
            call.respond(repo.getConfigDiffForRevision(rev))
        }
        post("/{rev}/diff") {
            val diff = call.receive<ConfigMap>()
            require(diff.isNotEmpty()) {
                "empty diff"
            }
            val response = repo.checkConfigDiff(diff)
            call.respondText(response.toString())
        }
        route("/diffstat") {
            get {
                val until = call.request.getValidQueryParameter("until") { toIntOrNull() }
                val limit = call.request.getValidQueryParameter("limit") { toIntOrNull() }
                call.respond(repo.getConfigRevisions(until, limit))
            }
        }
        configureConfigAdminApiRoutes(repo)
    }
}

private fun Route.configureConfigAdminApiRoutes(repo: Repository) {
    post("/admin/update") {
        val lens = call.receive<ConfigMap>()
        require(lens.isNotEmpty()) {
            "empty diff"
        }
        val newLatest = repo.updateLatestConfigObject(lens)
        call.respondText(newLatest.toString())
    }
    /* route("/admin/undo") { post { ... } } unimplemented */
}
