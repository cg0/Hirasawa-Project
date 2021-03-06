package io.hirasawa.server.config

import io.hirasawa.server.bancho.chat.ChatChannel
import io.hirasawa.server.database.DatabaseCredentials
import io.hirasawa.server.enums.Mod

data class HirasawaConfig (val httpPort: Int, val httpsPort: Int, val database: DatabaseCredentials,
                           val channels: List<ChatChannel>, val banchoUserTimeout: Int, val banchoBotId: Int,
                           val domain: String, val blockedMods: List<Mod>, val osuApiKey: String) {
    constructor(): this(
        8080,
        4430,
        DatabaseCredentials(),
        arrayListOf(
            ChatChannel("#osu", "Main channel", true),
            ChatChannel("#lounge", "Administration channel", false)
        ),
        60,
        3,
        "localhost",
        listOf(Mod.AUTOPLAY, Mod.RELAX, Mod.RELAX2, Mod.CINEMA, Mod.TARGET, Mod.SCORE_V2),
        ""
    )
}