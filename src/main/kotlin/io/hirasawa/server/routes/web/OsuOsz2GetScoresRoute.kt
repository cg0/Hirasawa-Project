package io.hirasawa.server.routes.web

import io.hirasawa.server.Hirasawa
import io.hirasawa.server.bancho.enums.GameMode
import io.hirasawa.server.bancho.user.BanchoUser
import io.hirasawa.server.database.tables.BeatmapsTable
import io.hirasawa.server.database.tables.ScoresTable
import io.hirasawa.server.database.tables.UsersTable
import io.hirasawa.server.enums.BeatmapStatus
import io.hirasawa.server.handlers.GetScoresErrorHeaderHandler
import io.hirasawa.server.handlers.GetScoresHeaderHandler
import io.hirasawa.server.handlers.ScoreInfoHandler
import io.hirasawa.server.objects.Beatmap
import io.hirasawa.server.objects.Score
import io.hirasawa.server.plugin.event.score.ClientLeaderboardFailEvent
import io.hirasawa.server.plugin.event.score.ClientLeaderboardLoadEvent
import io.hirasawa.server.plugin.event.score.ClientLeaderboardPreloadEvent
import io.hirasawa.server.webserver.route.Route
import io.hirasawa.server.webserver.enums.HttpHeader
import io.hirasawa.server.webserver.objects.Request
import io.hirasawa.server.webserver.objects.Response
import io.hirasawa.server.webserver.internalroutes.errors.RouteForbidden
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction

class OsuOsz2GetScoresRoute: Route {
    override fun handle(request: Request, response: Response) {
        if (request.headers[HttpHeader.USER_AGENT] != "osu!") {
            RouteForbidden().handle(request, response)
            return
        }

        val username = request.get["us"]
        val passwordHash = request.get["ha"]
        val beatmapHash = request.get["c"]
        val gamemode = GameMode.values()[request.get["m"]?.toInt() ?: 0]

        if (username == null || passwordHash == null || beatmapHash == null) {
            return
        }

        if (Hirasawa.authenticate(username, passwordHash)) {
            val user = BanchoUser(transaction {
                UsersTable.select {
                    UsersTable.username eq username
                }.first()
            })

            val preloadEvent = ClientLeaderboardPreloadEvent(user, beatmapHash, gamemode)
            Hirasawa.eventHandler.callEvent(preloadEvent)

            val beatmap = Hirasawa.databaseToObject<Beatmap>(Beatmap::class, transaction {
                BeatmapsTable.select { BeatmapsTable.hash eq beatmapHash }.firstOrNull()
            })
            val beatmapSet = beatmap?.beatmapSet

            if (beatmap == null || beatmapSet == null) {
                val failEvent = ClientLeaderboardFailEvent(user, beatmapHash, gamemode)
                Hirasawa.eventHandler.callEvent(failEvent)
                GetScoresErrorHeaderHandler(BeatmapStatus.NOT_SUBMITTED, false).write(response.outputStream)
                return
            }

            val loadEvent = ClientLeaderboardLoadEvent(user, beatmap, beatmapSet, gamemode)
            Hirasawa.eventHandler.callEvent(loadEvent)

            GetScoresHeaderHandler(false, beatmap, beatmapSet).write(response.outputStream)

            val userScore = Hirasawa.databaseToObject<Score>(Score::class, transaction {
                (ScoresTable innerJoin UsersTable).select {
                    (ScoresTable.beatmapId eq beatmap.id) and (ScoresTable.gamemode eq gamemode.ordinal) and
                            (ScoresTable.userId eq user.id)
                }.firstOrNull()
            })

            if (userScore != null) {
                ScoreInfoHandler(userScore, userScore.rank, false).write(response.outputStream)
            } else {
                response.outputStream.writeBytes("\n")
            }

            transaction {
                (ScoresTable leftJoin UsersTable).select {
                    (ScoresTable.beatmapId eq beatmap.id) and (ScoresTable.gamemode eq gamemode.ordinal)
                }.limit(50).sortedByDescending { ScoresTable.score }
            }.forEachIndexed { index, element ->
                val score = Score(element)
                ScoreInfoHandler(score, index+1, true).write(response.outputStream)
            }
        } else {
            RouteForbidden().handle(request, response)
            return
        }
    }
}