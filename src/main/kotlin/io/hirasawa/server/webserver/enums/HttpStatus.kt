package io.hirasawa.server.webserver.enums

enum class HttpStatus(val code: Int) {
    OK(200),

    FORBIDDEN(403),
    NOT_FOUND(404),

    INTERNAL_SERVER_ERROR(500)
}