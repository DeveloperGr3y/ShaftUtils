package io.github.developergr3y.shaftutils.corpse

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.developergr3y.shaftutils.ShaftUtils
import net.minecraft.core.BlockPos
import java.io.File

/**
 * Where corpses can spawn, per shaft type and variant: {"TOPA": {"ONE": ["-131,26,-192", ...]}}.
 *
 *  - Bundled: assets/shaftutils/corpse_spawns.json (seeded from meowdding's list; personal use only).
 *  - Learned: config/shaftutils/learned_spawns.json, spots recorded in debug mode that the bundled list is missing.
 *
 * `/shaftutils export` writes both merged into one file in the same format, ready to replace the bundled one.
 */
object SpawnData {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val learnedFile get() = File(ShaftUtils.configDir, "learned_spawns.json")

    private val bundled: MutableMap<String, MutableMap<String, MutableList<BlockPos>>> = mutableMapOf()
    private val learned: MutableMap<String, MutableMap<String, MutableList<BlockPos>>> = mutableMapOf()

    /** Two spots closer than this are the same spot (corpses sit slightly differently each time). */
    private const val SAME_SPOT = 2.5

    fun load() {
        javaClass.getResourceAsStream("/assets/shaftutils/corpse_spawns.json")?.reader()?.use { read(JsonParser.parseReader(it).asJsonObject, bundled) }
        if (learnedFile.exists()) {
            try {
                learnedFile.reader().use { read(JsonParser.parseReader(it).asJsonObject, learned) }
            } catch (e: Exception) {
                ShaftUtils.logger.error("Could not read ${learnedFile.path}", e)
            }
        }
        ShaftUtils.logger.info("Loaded ${count(bundled)} bundled and ${count(learned)} learned corpse spawn spots")
    }

    fun spots(type: String, variant: String): List<BlockPos> =
        (bundled[type]?.get(variant).orEmpty() + learned[type]?.get(variant).orEmpty()).distinct()

    fun isKnown(type: String, variant: String, pos: BlockPos) = spots(type, variant).any { it.distSqr(pos) <= SAME_SPOT * SAME_SPOT }

    /** Saves [pos] as a new spot if it isn't one already. Returns true if it was new. */
    fun learn(type: String, variant: String, pos: BlockPos): Boolean {
        if (isKnown(type, variant, pos)) return false
        learned.getOrPut(type) { mutableMapOf() }.getOrPut(variant) { mutableListOf() }.add(pos)
        save(learned, learnedFile)
        return true
    }

    fun learnedCount() = count(learned)

    /** Bundled + learned, in the bundled file's format. Returns the file written. */
    fun export(): File {
        val merged = mutableMapOf<String, MutableMap<String, MutableList<BlockPos>>>()
        for (source in listOf(bundled, learned)) {
            for ((type, variants) in source) for ((variant, list) in variants) {
                val target = merged.getOrPut(type) { mutableMapOf() }.getOrPut(variant) { mutableListOf() }
                list.filter { pos -> target.none { it.distSqr(pos) <= SAME_SPOT * SAME_SPOT } }.forEach { target += it }
            }
        }
        val file = File(ShaftUtils.configDir, "corpse_spawns.export.json")
        save(merged, file)
        return file
    }

    private fun read(json: JsonObject, into: MutableMap<String, MutableMap<String, MutableList<BlockPos>>>) {
        for ((type, variants) in json.entrySet()) {
            for ((variant, list) in variants.asJsonObject.entrySet()) {
                into.getOrPut(type) { mutableMapOf() }.getOrPut(variant) { mutableListOf() } +=
                    list.asJsonArray.mapNotNull { parse(it.asString) }
            }
        }
    }

    private fun save(data: Map<String, Map<String, List<BlockPos>>>, file: File) {
        val json = JsonObject()
        for ((type, variants) in data.toSortedMap()) {
            val v = JsonObject()
            for ((variant, list) in variants.toSortedMap()) {
                v.add(variant, gson.toJsonTree(list.map { "${it.x},${it.y},${it.z}" }))
            }
            json.add(type, v)
        }
        file.parentFile.mkdirs()
        file.writeText(gson.toJson(json))
    }

    private fun parse(s: String): BlockPos? {
        val parts = s.split(",").mapNotNull { it.trim().toIntOrNull() }
        return if (parts.size == 3) BlockPos(parts[0], parts[1], parts[2]) else null
    }

    private fun count(data: Map<String, Map<String, List<BlockPos>>>) = data.values.sumOf { v -> v.values.sumOf { it.size } }
}
