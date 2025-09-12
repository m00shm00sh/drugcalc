package com.moshy.drugcalc.cmdclient.io

import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.cmdclient.states.ForConnect
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.reflect.TypeInfo
import kotlinx.io.IOException
import java.util.EnumSet

@JvmName($$"doRequest$noResponse")
internal suspend inline fun AppState.doRequest(
    method: NetRequestMethod,
    urlString: String,
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
    flags: NetRequestFlags = EnumSet.of(NetRequestFlag.ENCODE_JSON),
) {
    require(!method.withResponseBody) {
        "no-response-body form restricted to methods not expecting response body"
    }
    return doRequest<Unit, Unit>(method, urlString, expectedStatus, flags)
}

@JvmName($$"doRequest$noRequestBody")
internal suspend inline fun <reified O> AppState.doRequest(
    method: NetRequestMethod,
    urlString: String,
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
    flags: NetRequestFlags = EnumSet.of(NetRequestFlag.ENCODE_JSON),
): O {
    require(!method.withRequestBody) {
        "no-request-body form restricted to methods not expecting request body"
    }
    return doRequest<O, Unit>(method, urlString, expectedStatus, flags)
}

internal suspend inline fun <reified O, reified I : Any> AppState.doRequest(
    method: NetRequestMethod,
    urlString: String,
    expectedStatus: HttpStatusCode = HttpStatusCode.OK,
    flags: NetRequestFlags = EnumSet.of(NetRequestFlag.ENCODE_JSON),
    body: I? = null,
): O {
    try {
        logger.debug("net: {} {}", method.name, urlString)
        val connectParams = forConnect.connect
        val client = forConnect.getClient()
        val jwtToken =
            if (flags.contains(NetRequestFlag.AUTH_JWT))
                forLogin.getLoginToken(this).also {
                    require(it.isNotEmpty()) {
                        "no login token"
                    }
                }
            else null
        client.(method.method)(connectParams.makeConnectableUrl(urlString)) {
            if (flags.contains(NetRequestFlag.AUTH_JWT)) {
                bearerAuth(jwtToken!!)
            }
            if (flags.contains(NetRequestFlag.ENCODE_JSON)) contentType(ContentType.Application.Json)
            if (method.withRequestBody) {
                require(body != null) {
                    "null body for request requiring body"
                }
                setBody(body)
            }
        }.apply {
            val lengthResponseHeader = call.response.headers["content-length"]
            logger.debug("net: rc: {} len: {}", this.status, lengthResponseHeader)
            return resultOrThrow<O>(expectedStatus)
        }
    } catch (e: AuthenticationFailure) {
        forLogin.clearLoginToken()
        throw e
    } catch (e: IOException) {
        forConnect.clearClient()
        throw e
    }
}


internal enum class NetRequestFlag {
    AUTH_JWT,
    ENCODE_JSON
}
internal typealias NetRequestFlags = EnumSet<NetRequestFlag>
internal val NRF_AUTH_AND_JSON = NetRequestFlags.of(NetRequestFlag.ENCODE_JSON, NetRequestFlag.AUTH_JWT)

internal enum class NetRequestMethod(
    val method: suspend HttpClient.(String, HttpRequestBuilder.() -> Unit) -> HttpResponse,
    val withRequestBody: Boolean = false,
    val withResponseBody: Boolean = true,
) {
    Get(HttpClient::get),
    Post(HttpClient::post, true),
    Put(HttpClient::put, true),
    Patch(HttpClient::patch, true),
    Delete(HttpClient::delete, withResponseBody = false),
}

internal suspend fun HttpResponse.resultTextOrThrow(
    vararg accept: HttpStatusCode = arrayOf(HttpStatusCode.OK)
): String {
    successOrThrow(accept)
    return bodyAsText()
}

internal suspend inline fun <reified T> HttpResponse.resultOrThrow(
    vararg accept: HttpStatusCode = arrayOf(HttpStatusCode.OK)
): T {
    successOrThrow(accept)
    return if (T::class != Unit)
        body()
    else
        Unit as T
}

private suspend fun HttpResponse.successOrThrow(accept: Array<out HttpStatusCode>) {
    if (status in accept)
        return
    val msg = bodyAsText()
    val thrower: (String) -> Exception = when (status) {
        HttpStatusCode.BadRequest -> { s -> IllegalArgumentException(s) }
        HttpStatusCode.Unauthorized -> { s -> AuthenticationFailure(s) }
        HttpStatusCode.Forbidden -> { s -> UnsupportedOperationException(s) }
        HttpStatusCode.NotFound -> { s -> NoSuchElementException(s) }
        HttpStatusCode.InternalServerError -> { s -> IllegalStateException(s) }
        else -> error("unhandled status code $status")
    }
    throw thrower("request: $msg")
}

internal class AuthenticationFailure(s: String = ""): RuntimeException(s)
