package com.glance.parcel.platform.paper.command

import org.bukkit.NamespacedKey

/**
 * Region keys as builders type them.
 *
 * A bare `tavern` means `parcel:tavern` - the shared namespace regions live in by default, because
 * a region is geometry that any number of plugins can bind to rather than something one plugin owns.
 * An explicit `motif:tavern` is still accepted, for regions a plugin did create for itself.
 */
internal object Keys {

    const val SHARED_NAMESPACE = "parcel"

    fun parse(input: String): NamespacedKey? {
        val trimmed = input.trim().lowercase()
        if (trimmed.isEmpty()) return null

        val separator = trimmed.indexOf(':')
        return if (separator < 0) {
            NamespacedKey(SHARED_NAMESPACE, trimmed)
        } else {
            val namespace = trimmed.substring(0, separator)
            val key = trimmed.substring(separator + 1)
            if (namespace.isEmpty() || key.isEmpty()) null else NamespacedKey(namespace, key)
        }
    }

    /** Renders a key the short way when it is in the shared namespace. */
    fun display(key: NamespacedKey): String =
        if (key.namespace == SHARED_NAMESPACE) key.key else key.toString()
}
