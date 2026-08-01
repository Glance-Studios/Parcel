package com.glance.parcel.platform.paper.command

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.command.CommandSender
import org.bukkit.plugin.Plugin
import org.incendo.cloud.SenderMapper
import org.incendo.cloud.annotations.AnnotationParser
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.meta.SimpleCommandMeta
import org.incendo.cloud.paper.LegacyPaperCommandManager
import java.util.logging.Level

/**
 * Cloud bootstrap, mirroring Codex's setup.
 *
 * Uses `simpleCoordinator()` rather than the async one on purpose: command bodies here touch
 * selections and regions, which are main-thread state, so running on the dispatching thread avoids
 * a sync hop in every single handler.
 */
internal class ParcelCommandManager(
    private val plugin: Plugin,
) : LegacyPaperCommandManager<CommandSender>(
    plugin,
    ExecutionCoordinator.simpleCoordinator(),
    SenderMapper.identity(),
) {

    private val annotationParser = AnnotationParser(
        this,
        CommandSender::class.java,
    ) { SimpleCommandMeta.empty() }

    init {
        registerDefaultExceptionHandlers(
            { triplet ->
                val sender = senderMapper().reverse(triplet.first().sender())
                val message = triplet.first().formatCaption(triplet.second(), triplet.third())
                sender.sendMessage(Component.text(message, NamedTextColor.RED))
            },
            { pair ->
                plugin.logger.log(Level.SEVERE, pair.first(), pair.second())
            },
        )
    }

    fun register(vararg handlers: Any) {
        handlers.forEach { annotationParser.parse(it) }
    }
}
