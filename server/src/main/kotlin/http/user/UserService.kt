package com.moshy.drugcalc.server.http.user

import java.util.concurrent.ConcurrentHashMap

internal class UserService() {
    private val users = ConcurrentHashMap<String, User>()

    suspend fun findUser(name: String?): User? =
        name?.let { users[name] }

    suspend fun saveUser(user: User) {
        users[user.name] = user
    }
}
