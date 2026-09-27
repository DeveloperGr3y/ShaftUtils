package io.github.developergr3y.shaftutils.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.developergr3y.shaftutils.ShaftUtils
import java.io.File

/**
 * Settings saved before the Helpers / Trackers tabs had each feature at the top level ("corpses", "profit", ...).
 * Move them under "helpers" / "trackers" before the config loads, so nobody loses their settings.
 */
object ConfigMigration {
    private val helpers = listOf("corpses", "title", "routes", "fossils")
    private val trackers = listOf("profit", "perfect")

    fun run(file: File) {
        if (!file.exists()) return
        try {
            val root = JsonParser.parseString(file.readText()).asJsonObject
            if (root.has("helpers") || root.has("trackers")) return
            if ((helpers + trackers).none { root.has(it) }) return
            root.add("helpers", move(root, helpers))
            root.add("trackers", move(root, trackers))
            file.writeText(GsonBuilder().setPrettyPrinting().create().toJson(root))
            ShaftUtils.logger.info("Moved settings into the Helpers / Trackers groups")
        } catch (e: Exception) {
            ShaftUtils.logger.warn("Couldn't move old settings into the new groups", e)
        }
    }

    private fun move(root: JsonObject, keys: List<String>) = JsonObject().also { group ->
        for (key in keys) root.remove(key)?.let { group.add(key, it) }
    }
}
