package com.moshy.drugcalc.cmdclient.handlers

import com.moshy.drugcalc.cmdclient.AppState
import com.moshy.drugcalc.types.login.UserRequest
import com.moshy.krepl.Repl
import com.moshy.krepl.asLine

internal fun AppState.configureLogin(): Repl.EntryBuilder.() -> Unit = {
    val app = this@configureLogin
    usage = "$name [user pass]"
    help = "log in"
    handler = { (pos, _, _, out) ->
        with(app.forLogin) l@ {
            when (pos.size) {
                2 -> login = UserRequest(pos[0], pos[1])
                0 -> requireNotNull(login) {
                    "empty saved login"
                }

                else -> throw IllegalArgumentException("unexpected arg count: ${pos.size}")
            }
            getLoginToken(app)
        }
        out.send("login ok".asLine())
    }
}
