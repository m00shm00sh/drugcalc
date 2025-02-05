package com.moshy.drugcalc.http

import com.moshy.drugcalc.io.DiffData
import com.moshy.drugcalc.io.FullData
import com.moshy.drugcalc.io.FullDiffData
import com.moshy.drugcalc.io.RevisionSummary
import com.moshy.drugcalc.io.toDiffData
import com.moshy.drugcalc.misc.caseFolded
import com.moshy.drugcalc.misc.withPreparedCompounds
import com.moshy.drugcalc.repo.Repository

import io.ktor.http.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlin.reflect.KProperty1

internal fun Route.configureDataApiRoutes(repo: Repository) {
    suspend fun revData(rev: String?): FullData =
        rev?.let {
            if (it == "latest")
                repo.getLatestData().second.data
            else {
                val revNum = it.toIntOrNull()
                require(revNum != null && revNum > 0) {
                    "invalid revision ($it)"
                }
                repo.getDataAtRevision(revNum).data
            }
        } ?: error("unexpected null: rev")

    route("/data") {
        // this route handler handles both /latest and /{rev} because the logic is 95% similar
        get("/{rev}") {
            val rev = call.parameters["rev"]!!
            val data = revData(rev)
            call.respond(data)
        }
        get("/{rev}/field/{field}") {
            val rev = call.parameters["rev"]!!
            val field = call.parameters["field"]!!
            val data = revData(rev)
            FieldValues[field]?.let {
                val fieldData = it.get(data)
                call.respond(fieldData)
            } ?: run {
                throw IllegalArgumentException("invalid field: $field")
            }
        }
        get("/{rev}/diff") {
            val revParam = call.parameters["rev"]!!
            val rev = revParam.toIntOrNull() ?: throw IllegalArgumentException("invalid revision $revParam")
            val dd = repo.getDataDiffForRevision(rev)
            call.respond(dd.toDiffData())
        }
        get("/{rev}/diff/full") {
            val revParam = call.parameters["rev"]!!
            val rev = revParam.toIntOrNull() ?: throw IllegalArgumentException("invalid revision $revParam")
            val dd = repo.getDataDiffForRevision(rev)
            call.respond(dd)
        }
        post("/{rev}/diff") {
            val rev = call.parameters["rev"]!!
            val diff = call.receive<DiffData>().copy(parentRevision = rev.toIntOrNull())
            require(diff.isNotEmpty()) {
                "empty diff"
            }
            val response = repo.checkDataDiff(diff.caseFolded().withPreparedCompounds())
            call.respondText(response.toString())
        }

        get("/diffstat") {
            val until = call.request.getValidQueryParameter("until") { toIntOrNull() }
            val limit = call.request.getValidQueryParameter("limit") { toIntOrNull() }
            call.respond(repo.getDataRevisions(until, limit))
        }
        configureDataAdminApiRoutes(repo)
    }
}

private fun Route.configureDataAdminApiRoutes(repo: Repository) {
    post("/admin/update") {
        val diff = call.receive<DiffData>()
        require(diff.isNotEmpty()) {
            "empty diff"
        }
        val newLatest = repo.updateLatestDataObject(diff.caseFolded().withPreparedCompounds())
        call.respond(newLatest)
    }
    // this endpoint is POST instead of DELETE because it acts on whatever latest is and not a specific revision
    post("/admin/undo") {
        repo.undoLatestData()
        call.respond(HttpStatusCode.NoContent)
    }
}

private val FieldValues: Map<String, KProperty1<FullData, Map<String, Any>>> =
        mapOf(
            "compounds" to FullData::compounds,
            "blends" to FullData::blends,
            "frequencies" to FullData::frequencies
)
