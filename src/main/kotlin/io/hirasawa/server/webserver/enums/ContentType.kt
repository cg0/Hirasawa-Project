package io.hirasawa.server.webserver.enums

enum class ContentType(private val value: String) {
    APPLICATION_OCTET_STREAM("application/octet-stream"),
    TEXT_PLAN("text/plain"),
    TEXT_HTML("text/html"),
    IMAGE_PNG("image/png");

    override fun toString(): String {
        return this.value
    }
}