package io.hirasawa.server.plugin.event.bancho

import io.hirasawa.server.bancho.chat.message.ChatMessage
import io.hirasawa.server.bancho.user.BanchoUser
import io.hirasawa.server.plugin.event.Cancelable
import io.hirasawa.server.plugin.event.HirasawaEvent

/**
 * Event when player sends a message in a chat channel
 */
class BanchoUserChatEvent(val chatMessage: ChatMessage): HirasawaEvent, Cancelable()