package com.moshy.drugcalc.server.http.plugins

import com.moshy.drugcalc.server.http.JwtService
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.respond

/** install: [Authentication] using [jwtService] as authentication conrtroller */
internal fun Application.configureSecurity(jwtService: JwtService) {
    authentication {
        jwt {
            realm = jwtService.realm
            verifier(jwtService.verifier)
            validate { credential ->
                jwtService.validator(credential)
            }
            challenge { scheme, realm ->
                call.respond(HttpStatusCode.Unauthorized, "invalid or expired token")
            }
        }
    }
}