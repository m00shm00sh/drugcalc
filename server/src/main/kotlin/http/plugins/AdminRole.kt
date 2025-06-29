package com.moshy.drugcalc.server.http.plugins

import com.moshy.drugcalc.server.http.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.respond
import io.ktor.server.routing.Route


internal val AdminRoleAuthorizationPlugin = createRouteScopedPlugin("AdminRole") {
    on(AuthenticationChecked) { call ->
        val isAdmin = JwtService.getIsAdmin(call)
        when (isAdmin) {
            null -> throw IllegalStateException("unexpected null auth token")
            false -> call.respond(HttpStatusCode.Forbidden)
            true -> {}
        }
    }
}

internal fun Route.authenticatedAsAdmin(
    vararg authenticationConfigurations: String? = arrayOf<String?>(null),
    build: Route.() -> Unit
): Route =
    authenticate(
        configurations = authenticationConfigurations,
        AuthenticationStrategy.FirstSuccessful,
        build = {
            install(AdminRoleAuthorizationPlugin)
            build()
        }
    )
