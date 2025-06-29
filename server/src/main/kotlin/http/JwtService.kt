package com.moshy.drugcalc.server.http

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.JWTVerifier
import com.moshy.drugcalc.server.http.user.*
import com.moshy.drugcalc.server.util.AppConfig
import io.ktor.server.application.ApplicationCall
import io.ktor.server.auth.jwt.*
import io.ktor.server.auth.principal
import kotlin.time.*


internal class JwtService(
    private val jwtConfig: AppConfig.Jwt,
    private val userService: UserService,
) {
    val realm = jwtConfig.realm

    val verifier: JWTVerifier = JWT
        .require(Algorithm.HMAC256(jwtConfig.secret.value))
        .withAudience(jwtConfig.audience)
        .withIssuer(jwtConfig.issuer)
        .build()

    fun createToken(user: User): String {
        @OptIn(ExperimentalTime::class)
        return JWT.create()
            .withAudience(jwtConfig.audience)
            .withIssuer(jwtConfig.issuer)
            .withClaim(CLAIM_USER, user.name)
            .withClaim(CLAIM_ROLE, user.isAdmin)
            .withExpiresAt((Clock.System.now() + jwtConfig.timeout).toJavaInstant())
            .sign(Algorithm.HMAC256(jwtConfig.secret.value))
    }

    suspend fun validator(credential: JWTCredential): JWTPrincipal? {
        val user = credential.payload.getClaim(CLAIM_USER).asString() ?: return null
        // if we have a JWT referencing a non-existent user, then that's clearly a problem
        userService.findUser(user) ?: return null
        if (credential.payload.audience.contains(jwtConfig.audience))
            return JWTPrincipal(credential.payload)
        return null
    }

    companion object {
        private const val CLAIM_USER = "user"
        private const val CLAIM_ROLE = "role"

        fun getUser(call: ApplicationCall): String? =
            call.principal<JWTPrincipal>()?.payload?.getClaim(CLAIM_USER)?.asString()

        fun getIsAdmin(call: ApplicationCall): Boolean? =
            call.principal<JWTPrincipal>()?.payload?.getClaim(CLAIM_ROLE)?.asBoolean()

    }

}
