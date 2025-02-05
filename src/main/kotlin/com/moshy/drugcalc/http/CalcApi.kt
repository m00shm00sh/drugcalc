package com.moshy.drugcalc.http

import com.moshy.drugcalc.calc.decodeCycles
import com.moshy.drugcalc.calc.decodeTimeTickScaling
import com.moshy.drugcalc.calc.evaluateDecodedCycle
import com.moshy.drugcalc.calcdata.DataStore
import com.moshy.drugcalc.io.CycleRequest
import com.moshy.drugcalc.misc.DecodedXYList
import com.moshy.drugcalc.misc.caseFolded
import com.moshy.drugcalc.misc.withPreparedCompounds
import com.moshy.drugcalc.repo.Repository
import com.moshy.drugcalc.repo.applyDiff
import com.moshy.drugcalc.repo.toFullDiffData
import com.moshy.plus

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*

internal fun Route.configureCalcApiRoutes(repo: Repository) {
    suspend fun revData(rev: Int?): DataStore =
        rev?.let {
            require(it > 0) {
                "invalid data revision ($it)"
            }
            repo.getDataAtRevision(it).state
        }
            ?: repo.getLatestData().second.state

    route("/calc") {
        post {
            val noDecode = (call.request.queryParameters["nodecode"]?.toIntOrNull() ?: 0) > 0
            val req = call.receive<CycleRequest>()
            val data = revData(req.data?.parentRevision).run {
                req.data?.let {
                    applyDiff(it.caseFolded().withPreparedCompounds().toFullDiffData(this))
                } ?: this
            }
            val config = repo.getLatestConfig().second.state.run {
                req.config?.let {
                    this + it
                } ?: this
            }
            // TODO: each of these three steps could be its own endpoint
            val decodedCycle = decodeCycles(req.cycle, data, config)
            val evaluated = evaluateDecodedCycle(decodedCycle, config)
            if (noDecode)
                call.respond(evaluated)
            else
                call.respond(evaluated.decodeTimeTickScaling(config))
        }
    }

}
