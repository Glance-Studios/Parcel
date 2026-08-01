package com.glance.parcel.platform.paper.command

import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.CommandSender

internal object Text {

    private val mm = MiniMessage.miniMessage()

    private const val PREFIX = "<dark_gray>[<aqua>Parcel<dark_gray>]<reset> "

    fun send(sender: CommandSender, message: String) {
        sender.sendMessage(mm.deserialize(PREFIX + message))
    }

    fun error(sender: CommandSender, message: String) {
        send(sender, "<red>$message")
    }

    fun raw(sender: CommandSender, message: String) {
        sender.sendMessage(mm.deserialize(message))
    }
}
