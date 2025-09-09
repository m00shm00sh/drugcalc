package com.moshy.drugcalc.server.http.plugins

import com.moshy.drugcalc.common.logger
import com.moshy.drugcalc.server.util.AccessException
import io.ktor.http.*
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.CannotTransformContentToTypeException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

/** install: [StatusPages] */
internal fun Application.configureExceptions(sendUnexpectedExceptionStackTrace: Boolean = false) {
    install(StatusPages) {
        val logger = logger("server: ExceptionRenderer")
        exception<Throwable> { call, cause ->
            when (cause) {
                is BadRequestException,
                is JsonConvertException,
                is UnsupportedOperationException,
                is IllegalArgumentException -> {
                    logger.debug("400: {}", cause.message)
                    call.respondText(text = "400: ${cause.message}", status = HttpStatusCode.BadRequest)
                }

                is NoSuchElementException -> {
                    logger.debug("404: {}", cause.message)
                    call.respondText(text = "404: ${cause.message}", status = HttpStatusCode.NotFound)
                }

                is AccessException -> {
                    logger.debug("403: {}", cause.message)
                    call.respondText(text = "403: ${cause.message}", status = HttpStatusCode.Forbidden)
                }

                is AuthenticationFailure -> {
                    logger.debug("401: {}", cause.message)
                    call.respondText(text = "401: ${cause.message}", status = HttpStatusCode.Unauthorized)
                }

                else -> {
                    logger.error("Caught unexpected exception", cause)
                    val detail = when (sendUnexpectedExceptionStackTrace) {
                        true -> {
                            "Stack trace:" + cause.stackTraceToString()
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

internal class AuthenticationFailure(s: String = ""): RuntimeException(s)