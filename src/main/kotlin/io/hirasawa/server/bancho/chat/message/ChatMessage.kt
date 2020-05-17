package io.hirasawa.server.bancho.chat.message

import io.hirasawa.server.bancho.user.User

abstract class ChatMessage(val source: User, val destinationName: String, val message: String)