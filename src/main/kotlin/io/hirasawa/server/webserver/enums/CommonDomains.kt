package io.hirasawa.server.webserver.enums

enum class CommonDomains(val domain: String) {
    OSU_WEB("osu.ppy.sh"),
    OSU_AVATAR("a.ppy.sh"),
    OSU_BEATMAPS("b.ppy.sh"),
    OSU_BANCHO("c.ppy.sh");

    override fun toString(): String {
        return this.domain
    }
}