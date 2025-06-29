package com.moshy.drugcalc.server.http.routing

import com.moshy.drugcalc.server.http.*
import com.moshy.drugcalc.server.http.plugins.AuthenticationFailure
import com.moshy.drugcalc.server.http.routing.util.*
import com.moshy.drugcalc.server.http.user.*
import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.resources.Resource
import io.ktor.server.auth.authenticate
import io.ktor.server.routing.*


@Resource("login")
internal class LoginRoute

@Resource("-test-a-")
internal class AuthTestRoute {
    @Resource("role")
    internal class Role(val parent: AuthTestRoute)
}

internal fun Route.configureLoginRoutes(
    flags: AppConfig.DevFlags,
    jwt: JwtService,
    userService: UserService,
) {
    post<LoginRoute, UserRequest, LoginResponse> { _, req ->
        val user = userService.findUser(req.name)
        if (user == null || user.pass != req.pass) {
            throw AuthenticationFailure("bad login")
        }
        val token = jwt.createToken(user)
        LoginResponse(token)
    }
    if (flags.enableTestonlyEndpoints) {
        authenticate {
            get<AuthTestRoute.Role, Map<String, String>> { _ ->
                val user = userService.findUser(JwtService.getUser(call))!!
                mapOf(
                    "user" to user.name,
                    "admin" to user.isAdmin.toString()
                )
            }
        }
    }
}
