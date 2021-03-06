package io.hirasawa.server.database.tables

import org.jetbrains.exposed.dao.IntIdTable

object UsersTable: IntIdTable("users") {
    val username = varchar("username", 30)
    val password = varchar("password", 60)
    val banned = bool("banned")
    val mutedUntil = integer("muted_until")
}