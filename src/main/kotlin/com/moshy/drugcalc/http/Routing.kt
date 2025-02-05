package com.moshy.drugcalc.http

import com.moshy.drugcalc.io.JsonWithLenientIsoDuration
import com.moshy.drugcalc.repo.DataGoneException
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*
import org.slf4j.LoggerFactory

internal fun Application.configureDataRouting(sendUnexpectedExceptionStackTrace: Boolean = false) {
    install(ContentNegotiation) {
        json(JsonWithLenientIsoDuration)
    }
    install(StatusPages) {
        val logger = LoggerFactory.getLogger("ExceptionRenderer")
        exception<Throwable> { call, cause ->
            when (cause) {
                is IllegalArgumentException ->
                    call.respondText(text = "400: ${cause.message}", status = HttpStatusCode.BadRequest)
                is NoSuchElementException ->
                    call.respondText(text = "404: ${cause.message}", status = HttpStatusCode.NotFound)
                is DataGoneException ->
                    call.respondText(text = "410: ${cause.message}", status = HttpStatusCode.Gone)
                is UnsupportedOperationException ->
                    call.respondText(text = "403: ${cause.message}", status = HttpStatusCode.Forbidden)
                else -> {
                    logger.error("Caught unexpected exception", cause)
                    val detail = when (sendUnexpectedExceptionStackTrace) {
                        true -> {
                            val sw = java.io.StringWriter()
                            java.io.PrintWriter(sw).use {
                                it.println("Stack trace:")
                                cause.printStackTrace(it)
                            }
                            sw.toString()
                        }
                        else -> "see log for details"
                    }
                    call.respondText(
                        text = "500: ${cause.message}\n$detail",
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }
    }
}