package io.hirasawa.server.bancho.user

import io.hirasawa.server.Hirasawa
import io.hirasawa.server.bancho.chat.message.PrivateChatMessage
import io.hirasawa.server.bancho.enums.GameMode
import io.hirasawa.server.bancho.objects.BanchoStatus
import io.hirasawa.server.bancho.objects.UserStats
import io.hirasawa.server.bancho.packets.*
import io.hirasawa.server.database.tables.UserStatsTable
import io.hirasawa.server.database.tables.UsersTable
import io.hirasawa.server.permissions.PermissionGroup
import io.hirasawa.server.plugin.event.bancho.BanchoUserSpectateJoinEvent
import io.hirasawa.server.plugin.event.bancho.BanchoUserSpectateLeaveEvent
import io.hirasawa.server.plugin.event.bancho.BanchoUserSpectateSwitchEvent
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.*
import java.util.concurrent.TimeUnit

open class BanchoUser(id: Int, username: String, timezone: Byte, countryCode: Byte, longitude: Float, latitude: Float,
                      var uuid: UUID, isBanned: Boolean): User(id, username,
        timezone, countryCode, longitude, latitude, isBanned) {

    constructor(result: ResultRow): this(result[UsersTable.id].value, result[UsersTable.username], 0, 0, 0F ,0F,
        UUID.randomUUID(), result[UsersTable.banned])

    val packetCache = Stack<BanchoPacket>()
    var status = BanchoStatus()
    var userStats = UserStats(id)
    var lastKeepAlive = 0
    val clientPermissions by lazy { Hirasawa.permissionEngine.calculateClientPermissions(this) }
    val spectators = ArrayList<BanchoUser>()
    var spectating: BanchoUser? = null

    /**
     * Send a packet to the user
     *
     * @param banchoPacket The packet to send
     */
    open fun sendPacket(banchoPacket: BanchoPacket) {
        packetCache.push(banchoPacket)
    }

    /**\
     * Sets the lastKeepAlive value to the current time
     * This value is used to timeout the user after inactivity
      */
    fun updateKeepAlive() {
        lastKeepAlive = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis()).toInt()
    }

    /**
     * Sends a private chat message to this user
     *
     * @param from The user that sent the chat message
     * @param message The message sent to the user
     */
    override fun sendPrivateMessage(from: User, message: String) {
        Hirasawa.chatEngine.handleChat(PrivateChatMessage(from, this, message))
    }

    /**
     * Updates user stats for the specified gamemode
     *
     * This can be used to switch gamemodes or just update the stats on it
     */
    fun updateUserStats(gameMode: GameMode) {
        userStats = UserStats(transaction {
            UserStatsTable.select {
                (UserStatsTable.userId eq id) and (UserStatsTable.gamemode eq gameMode.ordinal)
            }.first()
        })
    }

    /**
     * Spectates another Bancho User
     * @param banchoUser The user to spectate
     */
    fun spectateUser(banchoUser: BanchoUser) {
        if (this.spectating != null) {
            val spectateSwitchEvent = BanchoUserSpectateSwitchEvent(this, this.spectating!!, banchoUser)
            Hirasawa.eventHandler.callEvent(spectateSwitchEvent)
            stopSpectating()
        }
        val spectateJoinEvent = BanchoUserSpectateJoinEvent(this, banchoUser)
        Hirasawa.eventHandler.callEvent(spectateJoinEvent)

        for (spectators in banchoUser.spectators) {
            spectators.sendPacket(FellowSpectatorJoined(this))
        }

        banchoUser.sendPacket(SpectatorJoined(this))

        this.spectating = banchoUser
        banchoUser.spectators.add(this)

        this.sendPacket(ChannelJoinSuccessPacket(Hirasawa.chatEngine.spectatorChannel))
        banchoUser.sendPacket(ChannelJoinSuccessPacket(Hirasawa.chatEngine.spectatorChannel))
    }

    fun stopSpectating() {
        if (this.spectating != null) {
            val spectateLeaveEvent = BanchoUserSpectateLeaveEvent(this, this.spectating!!)
            Hirasawa.eventHandler.callEvent(spectateLeaveEvent)

            for (spectators in this.spectating!!.spectators) {
                spectators.sendPacket(FellowSpectatorLeft(this))
            }

            this.spectating?.sendPacket(SpectatorLeft(this))

            this.spectating?.spectators?.remove(this)
            this.spectating = null
            this.sendPacket(ChannelRevokedPacket(Hirasawa.chatEngine.spectatorChannel))
        }
    }

}