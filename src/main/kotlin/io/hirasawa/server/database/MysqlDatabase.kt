package io.hirasawa.server.database

import io.hirasawa.server.Hirasawa
import io.hirasawa.server.bancho.enums.GameMode
import io.hirasawa.server.bancho.objects.UserStats
import io.hirasawa.server.bancho.user.BanchoUser
import io.hirasawa.server.bancho.user.User
import io.hirasawa.server.enums.BeatmapStatus
import io.hirasawa.server.objects.Beatmap
import io.hirasawa.server.objects.BeatmapSet
import io.hirasawa.server.objects.Score
import io.hirasawa.server.permissions.PermissionGroup
import org.mindrot.jbcrypt.BCrypt
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import java.util.*
import kotlin.collections.ArrayList

class MysqlDatabase(credentials: DatabaseCredentials) : Database(credentials) {
    private var connection: Connection
    init {
        Class.forName("com.mysql.jdbc.Driver")
        connection = DriverManager.getConnection(
            "jdbc:mysql://${credentials.host}/${credentials.database}" +
                    "?user=${credentials.username}&password=${credentials.password}")

    }

    override fun authenticate(username: String, password: String): Boolean {
        val query = "SELECT username, password FROM users WHERE username = ?"
        val statement = connection.prepareStatement(query)
        statement.setString(1, username)

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            return BCrypt.checkpw(password, resultSet.getString("password"))
        }

        return false
    }

    private fun getPermissionGroupsFromUser(userId: Int): ArrayList<PermissionGroup> {
        val query = "SELECT name FROM permission_group_users INNER JOIN permission_groups ON " +
                "permission_groups.id = group_id  WHERE user_id = ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, userId)

        val groups = ArrayList<PermissionGroup>()

        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            groups.add(Hirasawa.permissionEngine.getGroup(resultSet.getString("name")))
        }

        return groups

    }

    private fun resultSetToUser(resultSet: ResultSet): User {
        return BanchoUser(resultSet.getInt("users.id"), resultSet.getString("users.username"), 0, 0,
            getPermissionGroupsFromUser(resultSet.getInt("users.id")),0F,0F, UUID.randomUUID(),
            resultSet.getBoolean("users.banned"))
    }

    override fun getUser(id: Int): User? {
        val query = "SELECT * FROM users WHERE id = ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, id)

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            return resultSetToUser(resultSet)
        }

        return null
    }

    override fun getUser(username: String): User? {
        val query = "SELECT * FROM users WHERE username = ?"
        val statement = connection.prepareStatement(query)
        statement.setString(1, username)

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            return resultSetToUser(resultSet)
        }

        return null
    }

    override fun createPasswordHash(password: String): String {
        return BCrypt.hashpw(password, BCrypt.gensalt())
    }

    override fun getUserFriends(id: Int): ArrayList<User> {
        val query = "SELECT * FROM friends INNER JOIN users ON friend_id = users.id WHERE user_id = ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, id)

        val friends = ArrayList<User>()

        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            friends.add(resultSetToUser(resultSet))
        }

        return friends
    }

    override fun getPermissionGroups(): HashMap<String, PermissionGroup> {
        val query = "SELECT name, node from permission_groups LEFT JOIN permission_nodes ON group_id = " +
                "permission_groups.id;"
        val statement = connection.prepareStatement(query)

        val groups = HashMap<String, PermissionGroup>()

        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            val name = resultSet.getString("name")
            val node = resultSet.getString("node")

            if (name !in groups) {
                groups[name] = PermissionGroup(name)
            }
            groups[name]?.permissions?.add(node)
        }

        return groups
    }

    private fun resultSetToScore(resultSet: ResultSet): Score {
        return resultSetToScore(resultSet, resultSetToUser(resultSet))
    }

    private fun resultSetToScore(resultSet: ResultSet, user: User): Score {
        return Score(resultSet.getInt("scores.id"), user, resultSet.getInt("scores.score"),
            resultSet.getInt("scores.combo"), resultSet.getInt("scores.count50"), resultSet.getInt("scores.count100"),
            resultSet.getInt("scores.count300"), resultSet.getInt("scores.count_miss"),
            resultSet.getInt("scores.count_katu"), resultSet.getInt("scores.count_geki"),
            resultSet.getBoolean("scores.full_combo"), resultSet.getInt("scores.mods"),
            resultSet.getInt("scores.timestamp"), GameMode.values()[resultSet.getInt("scores.gamemode")],
            resultSet.getInt("scores.rank"), resultSet.getInt("beatmap_id"), resultSet.getFloat("accuracy"))
    }

    override fun getScore(id: Int): Score? {
        val query = "SELECT * FROM scores LEFT JOIN users ON user_id = users.id WHERE scores.id = ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, id)

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            return resultSetToScore(resultSet)
        }

        return null
    }

    private fun resultSetToBeatmap(resultSet: ResultSet): Beatmap {
        return Beatmap(resultSet.getInt("beatmaps.id"), resultSet.getInt("beatmaps.mapset_id"),
            resultSet.getString("beatmaps.difficulty"), resultSet.getString("beatmaps.hash"),
            resultSet.getInt("beatmaps.ranks"), resultSet.getFloat("beatmaps.offset"))
    }

    private fun resultSetToBeatmapSet(resultSet: ResultSet): BeatmapSet {
        return BeatmapSet(resultSet.getInt("beatmapsets.id"), resultSet.getString("beatmapsets.artist"),
            resultSet.getString("beatmapsets.title"), BeatmapStatus.fromId(resultSet.getInt("beatmapsets.status")))
    }

    override fun getBeatmap(id: Int): Beatmap? {
        val query = "SELECT * FROM beatmaps WHERE id = ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, id)

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            return resultSetToBeatmap(resultSet)
        }

        return null
    }

    override fun getBeatmap(hash: String): Beatmap? {
        val query = "SELECT * FROM beatmaps WHERE hash = ?"
        val statement = connection.prepareStatement(query)
        statement.setString(1, hash)

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            return resultSetToBeatmap(resultSet)
        }

        return null
    }

    override fun getBeatmapSet(id: Int): BeatmapSet? {
        val query = "SELECT * FROM beatmapsets WHERE id = ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, id)

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            return BeatmapSet(resultSet.getInt("id"), resultSet.getString("artist"), resultSet.getString("title"),
                BeatmapStatus.fromId(resultSet.getInt("status")))
        }

        return null
    }

    override fun getBeatmapScores(beatmap: Beatmap, mode: GameMode, limit: Int): ArrayList<Score> {
        val query = "SELECT * FROM scores LEFT JOIN users ON user_id = users.id WHERE beatmap_id = ? AND " +
                "gamemode = ? ORDER BY score DESC LIMIT ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, beatmap.id)
        statement.setInt(2, mode.ordinal)
        statement.setInt(3, limit)

        val scores = ArrayList<Score>()

        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            val score = resultSetToScore(resultSet)
            scores.add(score)
        }

        return scores
    }

    override fun getUserScore(beatmap: Beatmap, mode: GameMode, user: User): Score? {
        val query = "SELECT * FROM scores WHERE user_id = ? AND beatmap_id = ? AND gamemode = ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, user.id)
        statement.setInt(2, beatmap.id)
        statement.setInt(3, mode.ordinal)

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            return resultSetToScore(resultSet, user)
        }

        return null
    }

    override fun getUserScores(mode: GameMode, user: User): ArrayList<Score> {
        val query = "SELECT * FROM scores WHERE user_id = ? AND gamemode = ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, user.id)
        statement.setInt(2, mode.ordinal)

        val scores = ArrayList<Score>()

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            scores.add(resultSetToScore(resultSet, user))
        }

        return scores
    }

    override fun getUserStats(user: User, gameMode: GameMode): UserStats? {
        val query = "SELECT * FROM user_stats WHERE user_id = ? AND gamemode = ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, user.id)
        statement.setInt(2, gameMode.ordinal)

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            return UserStats(user.id, resultSet.getLong("ranked_score"), resultSet.getFloat("accuracy"),
                resultSet.getInt("playcount"), resultSet.getLong("total_score"), resultSet.getInt("rank"),
                resultSet.getShort("pp"), gameMode)
        }

        return null
    }

    override fun getUserStats(gameMode: GameMode, sort: String): ArrayList<UserStats> {
        val query = "SELECT * FROM user_stats WHERE gamemode = ? ORDER BY ? DESC"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, gameMode.ordinal)
        statement.setString(2, sort)

        val userStats = ArrayList<UserStats>()

        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            userStats.add(UserStats(resultSet.getInt("user_id"), resultSet.getLong("ranked_score"),
                resultSet.getFloat("accuracy"), resultSet.getInt("playcount"), resultSet.getLong("total_score"),
                resultSet.getInt("rank"), resultSet.getShort("pp"), gameMode))
        }

        return userStats
    }

    override fun submitScore(score: Score) {
        val query = "INSERT INTO scores (user_id, score, combo, count50, count100, count300, count_miss, count_katu," +
                "count_geki, full_combo, mods, timestamp, beatmap_id, gamemode, rank, accuracy) VALUES (?, ?, ?, ?, ?, " +
                "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
        val statement = connection.prepareStatement(query)

        statement.setInt(1, score.user.id)
        statement.setInt(2, score.score)
        statement.setInt(3, score.combo)
        statement.setInt(4, score.count50)
        statement.setInt(5, score.count100)
        statement.setInt(6, score.count300)
        statement.setInt(7, score.countMiss)
        statement.setInt(8, score.countKatu)
        statement.setInt(9, score.countGeki)
        statement.setBoolean(10, score.fullCombo)
        statement.setInt(11, score.mods)
        statement.setInt(12, score.timestamp)
        statement.setInt(13, score.beatmapId)
        statement.setInt(14, score.gameMode.ordinal)
        statement.setInt(15, 0)
        statement.setFloat(16, score.accuracy)

        score.id = statement.executeUpdate()
    }

    override fun updateScore(newScore: Score) {
        val query = "UPDATE scores SET user_id = ?, score = ?, combo = ?, count50 = ?, count100 = ?, count300 = ? , " +
                "count_miss = ?, count_katu = ?, count_geki = ?, full_combo = ?, mods = ?, timestamp = ?, " +
                "beatmap_id = ?, gamemode = ?, rank = ?, accuracy = ? WHERE id = ?"
        val statement = connection.prepareStatement(query)

        statement.setInt(1, newScore.user.id)
        statement.setInt(2, newScore.score)
        statement.setInt(3, newScore.combo)
        statement.setInt(4, newScore.count50)
        statement.setInt(5, newScore.count100)
        statement.setInt(6, newScore.count300)
        statement.setInt(7, newScore.countMiss)
        statement.setInt(8, newScore.countKatu)
        statement.setInt(9, newScore.countGeki)
        statement.setBoolean(10, newScore.fullCombo)
        statement.setInt(11, newScore.mods)
        statement.setInt(12, newScore.timestamp)
        statement.setInt(13, newScore.beatmapId)
        statement.setInt(14, newScore.gameMode.ordinal)
        statement.setInt(15, newScore.rank)
        statement.setFloat(16, newScore.accuracy)
        statement.setInt(17, newScore.id)

        statement.executeUpdate()
    }

    override fun removeScore(score: Score) {
        val query = "DELETE FROM scores WHERE id = ?"
        val statement = connection.prepareStatement(query)

        statement.setInt(1, score.id)

        statement.executeUpdate()
    }

    override fun processLeaderboard(beatmap: Beatmap, gameMode: GameMode) {
        var rank = 1
        val scores = getBeatmapScores(beatmap, gameMode, Int.MAX_VALUE)
        for (score in scores) {
            if (!score.user.isBanned) {
                score.rank = rank++
                updateScore(score)
            }
        }

        beatmap.ranks = scores.size

        updateBeatmap(beatmap)
    }

    override fun updateBeatmap(newBeatmap: Beatmap) {
        val query = "UPDATE beatmaps SET mapset_id = ?, difficulty = ?, hash = ?, ranks = ?, offset = ? WHERE id = ?"
        val statement = connection.prepareStatement(query)

        statement.setInt(1, newBeatmap.mapsetId)
        statement.setString(2, newBeatmap.difficulty)
        statement.setString(3, newBeatmap.hash)
        statement.setInt(4, newBeatmap.ranks)
        statement.setFloat(5, newBeatmap.offset)
        statement.setInt(6, newBeatmap.id)

        statement.executeUpdate()
    }

    override fun updateUserStats(userStats: UserStats) {
        val query = "UPDATE user_stats SET ranked_score = ?, accuracy = ?, playcount = ?, rank = ?, pp = ? " +
                "WHERE user_id = ? AND gamemode = ?"
        val statement = connection.prepareStatement(query)

        statement.setLong(1, userStats.rankedScore)
        statement.setFloat(2, userStats.accuracy)
        statement.setInt(3, userStats.playcount)
        statement.setInt(4, userStats.rank)
        statement.setShort(5, userStats.pp)
        statement.setInt(6, userStats.userId)
        statement.setInt(7, userStats.gameMode.ordinal)

        statement.executeUpdate()
    }

    override fun processGlobalLeaderboard(gameMode: GameMode) {
        var rank = 1
        for (userStats in getUserStats(gameMode, sort = "ranked_score")) {
            if (getUser(userStats.userId)?.isBanned == false) {
                userStats.rank = rank++
                updateUserStats(userStats)
            }
        }
    }

    override fun getBeatmapSets(page: Int, limit: Int): ArrayList<BeatmapSet> {
        val query = "SELECT * FROM beatmapsets LIMIT ?,?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, page * limit)
        statement.setInt(2, (page + 1) * limit)

        val beatmapSets = ArrayList<BeatmapSet>()

        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            beatmapSets.add(resultSetToBeatmapSet(resultSet))
        }

        return beatmapSets
    }

    override fun getBeatmapSetAmount(): Int {
        val query = "SELECT COUNT(*) FROM beatmapsets"
        val statement = connection.prepareStatement(query)

        val resultSet = statement.executeQuery()
        if (resultSet.next()) {
            return resultSet.getInt("count(*)")
        } else {
            return 0
        }
    }

    override fun getBeatmatSetDifficulties(beatmapSet: BeatmapSet): ArrayList<Beatmap> {
        val query = "SELECT * FROM beatmaps WHERE mapset_id = ?"
        val statement = connection.prepareStatement(query)
        statement.setInt(1, beatmapSet.id)

        val beatmaps = ArrayList<Beatmap>()

        val resultSet = statement.executeQuery()
        while (resultSet.next()) {
            beatmaps.add(resultSetToBeatmap(resultSet))
        }

        return beatmaps
    }

}