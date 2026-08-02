package com.glance.parcel.platform.paper

import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets

/**
 * Brings an existing config.yml up to date with the one shipped in the jar.
 *
 * `saveDefaultConfig()` writes the file once and never touches it again, so every option added
 * after a server first installed the plugin is invisible to it - the plugin runs on code defaults
 * while the admin reads a file that does not mention them. That is silent, and it already caught us
 * once on our own test server.
 *
 * The obvious fix, `copyDefaults(true)` plus `saveConfig()`, would merge the missing keys but flatten
 * the file - and this config's comments *are* its documentation, so a `follow:` block arriving with
 * no explanation is barely better than it not arriving at all.
 *
 * So instead: keep the shipped file verbatim, comments and ordering intact, and re-apply whatever
 * values the admin had set. New options arrive fully documented.
 *
 * ⚠️ The trade: the file is re-ordered to match the default, and any comments the admin added
 * themselves are lost. Hence the backup, every time.
 */
internal object ConfigMigration {

    fun run(plugin: JavaPlugin): List<String> {
        val file = File(plugin.dataFolder, "config.yml")
        if (!file.isFile) return emptyList()

        val shippedText = plugin.getResource("config.yml")
            ?.use { InputStreamReader(it, StandardCharsets.UTF_8).readText() }
            ?: return emptyList()

        val live = YamlConfiguration.loadConfiguration(file)
        val shipped = YamlConfiguration.loadConfiguration(shippedText.reader())

        // Only leaf keys matter: a missing section is reported through its children anyway, and
        // treating sections as values would try to copy them over each other.
        val shippedLeaves = shipped.getKeys(true).filterNot { shipped.get(it) is ConfigurationSection }
        val missing = shippedLeaves.filterNot { live.contains(it) }
        if (missing.isEmpty()) return emptyList()

        val backup = File(plugin.dataFolder, "config.yml.bak")
        file.copyTo(backup, overwrite = true)

        // Write the shipped file as-is, then put the admin's values back on top of it.
        file.writeText(shippedText, StandardCharsets.UTF_8)
        val merged = YamlConfiguration.loadConfiguration(file)
        shippedLeaves
            .filter { live.contains(it) }
            .forEach { merged.set(it, live.get(it)) }
        merged.save(file)

        plugin.logger.info(
            "Config updated with ${missing.size} new option(s), previous file kept as config.yml.bak"
        )
        missing.forEach { plugin.logger.info("  added: $it") }
        return missing
    }
}
