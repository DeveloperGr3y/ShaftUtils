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
 *  - Ours: assets/shaftutils/corpse_spawns.json, spots we've seen corpses at ourselves. Ships in every build.
 *  - Personal: assets/shaftutils/corpse_spawns_personal.json, extra data only in personal builds (`-Ppersonal`).
 *  - Learned: config/shaftutils/learned_spawns.json, corpses you've seen that *our* data doesn't have yet.
 *
 * All crystal shafts share one layout, so a CRYSTAL spot from any gem counts for every crystal shaft.
 * `/shaftutils export` merges ours + learned (never the personal data) into one file, ready to replace ours.
 */
object SpawnData {
    private val gson = GsonBuilder().setPrettyPrinting().create()
    private val learnedFile get() = File(ShaftUtils.configDir, "learned_spawns.json")

    private val bundled: MutableMap<String, MutableMap<String, MutableList<BlockPos>>> = mutableMapOf()
    private val personal: MutableMap<String, MutableMap<String, MutableList<BlockPos>>> = mutableMapOf()
    private val learned: MutableMap<String, MutableMap<String, MutableList<BlockPos>>> = mutableMapOf()

    /** Two spots closer than this are the same spot (corpses sit slightly differently each time). */
    private const val SAME_SPOT = 2.5

    fun load() {
        javaClass.getResourceAsStream("/assets/shaftutils/corpse_spawns.json")?.reader()?.use { read(JsonParser.parseReader(it).asJsonObject, bundled) }
        javaClass.getResourceAsStream("/assets/shaftutils/corpse_spawns_personal.json")?.reader()?.use { read(JsonParser.parseReader(it).asJsonObject, personal) }
        if (learnedFile.exists()) {
            try {
                learnedFile.reader().use { read(JsonParser.parseReader(it).asJsonObject, learned) }
            } catch (e: Exception) {
                ShaftUtils.logger.error("Could not read ${learnedFile.path}", e)
            }
        }
        ShaftUtils.logger.info("Loaded ${count(bundled)} bundled, ${count(personal)} personal and ${count(learned)} learned corpse spawn spots")
    }

    private fun from(source: Map<String, Map<String, List<BlockPos>>>, type: String, variant: String): List<BlockPos> =
        if (variant == "CRYSTAL") source.values.flatMap { it[variant].orEmpty() } else source[type]?.get(variant).orEmpty()

    /** Every known spot for a shaft, from all sources, without near-duplicates. */
    fun spots(type: String, variant: String): List<BlockPos> {
        val result = mutableListOf<BlockPos>()
        for (source in listOf(bundled, learned, personal)) {
            from(source, type, variant).filter { pos -> result.none { it.distSqr(pos) <= SAME_SPOT * SAME_SPOT } }.forEach { result += it }
        }
        return result
    }

    /** In our own data (bundled or learned). The personal data doesn't count, so recording keeps growing ours. */
    private fun isOurs(type: String, variant: String, pos: BlockPos) =
        (from(bundled, type, variant) + from(learned, type, variant)).any { it.distSqr(pos) <= SAME_SPOT * SAME_SPOT }

    /** Saves [pos] as a new spot if our data doesn't have it yet. Returns true if it was new. */
    fun learn(type: String, variant: String, pos: BlockPos): Boolean {
        if (isOurs(type, variant, pos)) return false
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
