package io.hirasawa.server.bancho.chat

import io.hirasawa.server.Hirasawa
import io.hirasawa.server.bancho.chat.command.ChatCommand
import io.hirasawa.server.bancho.chat.command.CommandContext
import io.hirasawa.server.bancho.chat.command.CommandSender
import io.hirasawa.server.bancho.chat.message.ChatMessage
import io.hirasawa.server.bancho.chat.message.GlobalChatMessage
import io.hirasawa.server.bancho.chat.message.PrivateChatMessage
import io.hirasawa.server.bancho.packets.ChannelRevokedPacket
import io.hirasawa.server.bancho.packets.SendMessagePacket
import io.hirasawa.server.bancho.user.BanchoUser
import io.hirasawa.server.bancho.user.User
import io.hirasawa.server.plugin.HirasawaPlugin
import io.hirasawa.server.plugin.event.bancho.BanchoUserChatEvent
import kotlin.collections.HashMap

class ChatEngine {
    val chatChannels = HashMap<String, ChatChannel>()
    val chatCommands = HashMap<String, Pair<ChatCommand, HirasawaPlugin>>()
    val spectatorChannel = ChatChannel("#spectator", "", false)

    operator fun set(key: String, value: ChatChannel) {
        chatChannels[key] = value
    }

    operator fun get(key: String): ChatChannel? {
        return chatChannels[key]
    }

    fun handleChat(user: User, destination: String, message: String) {
        when {
            destination == "!CONSOLE" -> {
                println(message)
            }
            destination == spectatorChannel.name -> {
                if (user is BanchoUser) {
                    handleSpectatorChat(user, message)
                }
            }
            destination.startsWith("#") -> {
                handleChat(GlobalChatMessage(user, chatChannels[destination]!!, message))
            }
            else -> {
                if (destination == "BanchoBot" && Hirasawa.hirasawaBot.username != destination) {
                    // Redirect /bb commands to the "real" BanchoBot
                    val banchoUser = Hirasawa.banchoUsers[user.id]
                    banchoUser?.sendPacket(ChannelRevokedPacket(destination))
                    handleChat(user, Hirasawa.hirasawaBot.username, message)
                } else {
                    handleChat(PrivateChatMessage(user, Hirasawa.banchoUsers[destination]!!, message))
                }
            }
        }
    }

    private fun handleSpectatorChat(user: BanchoUser, message: String) {
        val spectators: ArrayList<BanchoUser>
        if (user.spectating != null) {
            user.spectating?.sendPacket(SendMessagePacket(GlobalChatMessage(user, spectatorChannel, message)))
            spectators = user.spectating?.spectators ?: return
        } else {
            spectators = user.spectators
        }

        for (spectator in spectators) {
            if (spectator != user) {
                spectator.sendPacket(SendMessagePacket(GlobalChatMessage(user, spectatorChannel, message)))
            }
        }
    }

    fun handleChat(chatMessage: ChatMessage) {
        val event = BanchoUserChatEvent(chatMessage)

        Hirasawa.eventHandler.callEvent(event)

        if (event.isCancelled) {
            return
        }


        if (chatMessage is GlobalChatMessage) {
            handleGlobalChat(chatMessage)
        } else if (chatMessage is PrivateChatMessage) {
            handlePrivateChat(chatMessage)
        } else if (chatMessage.destinationName == "CONSOLE") {
            println(chatMessage.message)
        }
    }

    private fun handlePrivateChat(chatMessage: PrivateChatMessage) {
        if (chatMessage.destination in Hirasawa.banchoUsers) {
            Hirasawa.banchoUsers[chatMessage.destination]?.sendPacket(SendMessagePacket(chatMessage))
        }
    }

    private fun handleGlobalChat(chatMessage: GlobalChatMessage) {
        chatMessage.channel.sendMessage(chatMessage)

        if (chatMessage.message.startsWith("!")) {
            // is command
            val chatSegments = chatMessage.message.split(" ")
            handleCommand(chatSegments, chatMessage.source, chatMessage.channel)
        }
    }

    fun handleCommand(chatSegments: List<String>, sender: CommandSender, channel: ChatChannel) {
        val commandName = chatSegments[0].replace("!", "")
        if (commandName in chatCommands) {
            val chatCommand = chatCommands[commandName]?.first
            val context = CommandContext(sender, channel)
            chatCommand?.onCommand(context, chatSegments[0], chatSegments.slice(IntRange(1, chatSegments.size - 1)))
        }
    }


    fun registerCommand(command: ChatCommand, plugin: HirasawaPlugin) {
        chatCommands[command.name] = Pair(command, plugin)
    }

    fun removeCommands(plugin: HirasawaPlugin) {
        val iterator = chatCommands.iterator()
        while (iterator.hasNext()) {
            val pair = iterator.next().value
            if (pair.second == plugin) {
                iterator.remove()
            }
        }
    }
}