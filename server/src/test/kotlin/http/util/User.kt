package com.moshy.drugcalc.server.http.util

import com.moshy.drugcalc.server.http.user.*
import com.moshy.drugcalc.server.util.generateRandomString

internal suspend fun testingUserService(): UserService =
    UserService().apply {
        saveUser(
            User(
                name = TESTING_ADMIN,
                pass = generateRandomString(64),
                isAdmin = true
            )
        )
        saveUser(
            User(
                name = TESTING_OTHER,
                pass = generateRandomString(64),
                isAdmin = false
            )
        )
    }

internal const val TESTING_ADMIN = "test-admin"
internal const val TESTING_OTHER = "test-other"