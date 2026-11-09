package com.moshy.drugcalc.server.http.routing.util

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.openapi.jsonSchema
import io.ktor.server.application.isHandled
import io.ktor.server.resources.*
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
// do not import io.ktor.server.routing.*; it will pull in post<T>
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.openapi.describe
import io.ktor.utils.io.ExperimentalKtorApi

internal suspend inline fun <reified O : Any>
RoutingContext.handleResponseBody(status: HttpStatusCode, result: O) {
    if (call.isHandled)
        return
    when (O::class) {
        HttpStatusCode::class -> call.respondText(result.toString(), status = result as HttpStatusCode)
        Unit::class -> call.respondText("", status = status)
        else -> {
            call.response.status(status)
            call.respond(result)
        }
    }
}

/** Attach response metadata for OpenAPI generation. */
@OptIn(ExperimentalKtorApi::class)
internal inline fun <reified O : Any> Route.describeResponse(status: HttpStatusCode): Route =
    describe {
        responses {
            status {
                description = status.description
                when (O::class) {
                    Unit::class -> {}
                    String::class -> ContentType.Text.Plain {
                        schema = jsonSchema<O>()
                    }
                    else -> content {
                        schema = jsonSchema<O>()
                    }
                }
            }
        }
    }

/** Attach request and response metadata for OpenAPI generation. */
@OptIn(ExperimentalKtorApi::class)
internal inline fun <reified R : Any, reified O : Any> Route.describeRequestResponse(status: HttpStatusCode): Route =
    describe {
        if (R::class != Unit::class) {
            requestBody {
                required = true
                if (R::class == String::class) {
                    ContentType.Text.Plain {
                        schema = jsonSchema<R>()
                    }
                } else {
                    content {
                        schema = jsonSchema<R>()
                    }
                }
            }
        }
        responses {
            status {
                description = status.description
                when (O::class) {
                    Unit::class -> {}
                    String::class -> ContentType.Text.Plain {
                        schema = jsonSchema<O>()
                    }
                    else -> content {
                        schema = jsonSchema<O>()
                    }
                }
            }
        }
    }

@JvmName("getReturningObject")
internal inline fun <reified T : Any, reified O : Any>
Route.get(status: HttpStatusCode = HttpStatusCode.OK, crossinline body: suspend RoutingContext.(T) -> O): Route =
    get<T> { handleResponseBody(status,body(it)) }.describeResponse<O>(status)

// (+) request body; (+) response body
@JvmName("postReturningObject")
internal inline fun <reified T : Any, reified R : Any, reified O : Any>
Route.post(status: HttpStatusCode = HttpStatusCode.OK, crossinline body: suspend RoutingContext.(T, R) -> O): Route =
    post<T, R> { resource, response -> handleResponseBody(status,body(resource, response)) }
        .describeRequestResponse<R, O>(status)

// (-) request body; (+) response body
@JvmName("postWithoutRequestBodyReturningObject")
internal inline fun <reified T : Any, reified O: Any>
Route.post(status: HttpStatusCode = HttpStatusCode.OK, crossinline body: suspend RoutingContext.(T) -> O): Route =
    post<T> { handleResponseBody(status,body(it)) }.describeResponse<O>(status)

// (+) request body; (+) response body
@JvmName("putReturningObject")
internal inline fun <reified T : Any, reified R : Any, reified O : Any>
Route.put(status: HttpStatusCode = HttpStatusCode.OK, crossinline body: suspend RoutingContext.(T, R) -> O): Route =
    put<T, R> { resource, response -> handleResponseBody(status, body(resource, response)) }
        .describeRequestResponse<R, O>(status)

// (-) request body; (+) response body
@JvmName("putWithoutRequestBodyReturningObject")
internal inline fun <reified T : Any, reified O: Any>
Route.put(status: HttpStatusCode = HttpStatusCode.OK, crossinline body: suspend RoutingContext.(T) -> O): Route =
    put<T> { handleResponseBody(status,body(it)) }.describeResponse<O>(status)

@JvmName("deleteReturningObject")
internal inline fun <reified T : Any, reified O : Any>
Route.delete(status: HttpStatusCode = HttpStatusCode.OK, crossinline body: suspend RoutingContext.(T) -> O): Route =
    delete<T> { handleResponseBody(status,body(it)) }.describeResponse<O>(status)

// (+) request body; (+) response body
@JvmName("patchReturningObject")
internal inline fun <reified T : Any, reified R : Any, reified O : Any>
Route.patch(status: HttpStatusCode = HttpStatusCode.OK, crossinline body: suspend RoutingContext.(T, R) -> O): Route =
    patch<T, R> { resource, response -> handleResponseBody(status, body(resource, response)) }
        .describeRequestResponse<R, O>(status)

// (-) request body; (+) response body
@JvmName("patchWithoutRequestBodyReturningObject")
internal inline fun <reified T : Any, reified O : Any>
Route.patch(status: HttpStatusCode = HttpStatusCode.OK, crossinline body: suspend RoutingContext.(T) -> O): Route =
    patch<T> { handleResponseBody(status, body(it)) }.describeResponse<O>(status)


