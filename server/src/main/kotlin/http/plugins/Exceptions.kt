package com.moshy.drugcalc.server.http.plugins

import com.moshy.drugcalc.common.logger
import com.moshy.drugcalc.server.util.AccessException
import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.http.*
import io.ktor.serialization.JsonConvertException
import io.ktor.server.application.*
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

/** install: [StatusPages] */
internal fun Application.configureExceptions(flags: AppConfig.DevFlags) {
    install(StatusPages) {
        val logger = logger("server: ExceptionRenderer")
        exception<Throwable> { call, cause ->
            val causes =
                if (flags.dumpCausesToLog || flags.sendStackTrace)
                    getCauses(cause)
                else
                    emptyList()

            fun exMsg(rc: Int): String = buildString {
                append(rc)
                append(": ")
                appendLine(cause.message)
                if (flags.sendStackTrace)
                    causes.forEach(::appendLine)
            }

            when (cause) {
                is BadRequestException,
                is JsonConvertException,
                is UnsupportedOperationException,
                is IllegalArgumentException -> {
                    logger.debug("400: {}", cause.message)
                    if (flags.dumpCausesToLog)
                        causes.forEach { logger.debug("{}", it) }

                    call.respondText(text = exMsg(400), status = HttpStatusCode.BadRequest)
                }

                is NoSuchElementException -> {
                    logger.debug("404: {}", cause.message)
                    if (flags.dumpCausesToLog)
                        causes.forEach { logger.debug("{}", it) }
                    call.respondText(text = exMsg(404), status = HttpStatusCode.NotFound)
                }

                is AccessException -> {
                    logger.debug("403: {}", cause.message)
                    if (flags.dumpCausesToLog)
                        causes.forEach { logger.debug("{}", it) }
                    call.respondText(text = exMsg(403), status = HttpStatusCode.Forbidden)
                }

                is AuthenticationFailure -> {
                    logger.debug("401: {}", cause.message)
                    if (flags.dumpCausesToLog)
                        causes.forEach { logger.debug("{}", it) }
                    call.respondText(text = exMsg(401), status = HttpStatusCode.Unauthorized)
                }

                else -> {
                    logger.error("Caught unexpected exception", cause)
                    call.respondText(
                        text = exMsg(500),
                        status = HttpStatusCode.InternalServerError
                    )
                }
            }
        }
    }
}

internal class AuthenticationFailure(s: String = ""): RuntimeException(s)


// Throwable.stackTraceToString() gives a full stack trace, which is uselessly noisy in a coroutine environment
private fun getCauses(exc: Throwable): List<String> = buildList {
    var ex = exc.cause
    while (ex != null && ex != ex.cause) {
        add("-> ${ex::class}")
        add("   message ${ex.message}")
        ex = ex.cause
    }
}