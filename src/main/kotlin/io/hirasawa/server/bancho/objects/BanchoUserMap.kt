package io.hirasawa.server.bancho.objects

import io.hirasawa.server.bancho.user.BanchoUser
import io.hirasawa.server.bancho.user.User
import java.util.*
import kotlin.collections.HashMap

class BanchoUserMap {
    private val uuidCache = HashMap<UUID, BanchoUser>()
    private val usernameCache = HashMap<String, BanchoUser>()
    private val idCache = HashMap<Int, BanchoUser>()

    val idKeys get() = idCache.keys

    operator fun get(key: UUID): BanchoUser? {
        return uuidCache[key]
    }

    operator fun get(key: String): BanchoUser? {
        return usernameCache[key]
    }

    operator fun get(key: Int): BanchoUser? {
        return idCache[key]
    }

    operator fun get(user: User): BanchoUser? {
        return idCache[user.id]
    }

    operator fun iterator(): MutableIterator<BanchoUser> {
        return uuidCache.values.iterator()
    }

    fun add(user: BanchoUser) {
        uuidCache[user.uuid] = user
        usernameCache[user.username] = user
        idCache[user.id] = user
    }

    fun remove(user: BanchoUser) {
        uuidCache.remove(user.uuid)
        usernameCache.remove(user.username)
        idCache.remove(user.id)
    }

    operator fun contains(key: UUID): Boolean {
        return key in uuidCache.keys
    }

    operator fun contains(key: String): Boolean {
        return key in usernameCache.keys
    }

    operator fun contains(key: Int): Boolean {
        return key in idCache.keys
    }

    operator fun contains(banchoUser: BanchoUser): Boolean {
        return banchoUser in uuidCache.values
    }

    operator fun contains(user: User): Boolean {
        return user.id in idCache.keys
    }
}