package io.github.developergr3y.shaftutils.routes

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.developergr3y.shaftutils.ShaftUtils
import net.minecraft.core.BlockPos
import java.io.File

/** One stop on a route: a block, an optional label, and an optional colour (0xRRGGBB). */
class RoutePoint(val pos: BlockPos, val name: String?, val colour: Int?)

/** A loaded route and where it came from (your own file, or one of Mining Cult's that ship with the mod). */
class Route(val points: List<RoutePoint>, val builtIn: Boolean)

/**
 * Mineshaft routes: an ordered list of waypoints per shaft code.
 *
 *  - Built in: Mining Cult's routes, in assets/shaftutils/routes/ (credited in the Routes settings).
 *  - Yours: config/shaftutils/routes/, one file per shaft code (TOPA_1.json, RUBY_C.json, ...). Yours win.
 * A file named CRYSTAL.json is used for any crystal shaft (codes ending _C) that has no file of its own.
 *
 * Accepted formats (so routes from other places work as-is):
 *  - The common ordered-waypoint list: [{"x":1,"y":2,"z":3,"r":0,"g":1,"b":0,"options":{"name":"1"}}, ...]
 *    (r/g/b as 0-1 or 0-255; name optional; entries are used in the order given, or by numeric name if all have one)
 *  - Plain objects: [{"x":1,"y":2,"z":3}, ...] or [[1,2,3], ...]
 *  - Text, one point per line: "1 2 3", "1,2,3" or "1, 2, 3 label"
 */
object Routes {
    val dir get() = File(ShaftUtils.configDir, "routes")
    private val cache = mutableMapOf<String, Route?>()

    /** The route for a shaft code: yours if you've added one, else the built-in one (if enabled), else null. */
    fun forShaft(code: String): Route? {
        if (code in cache) return cache[code]
        val route = userRoute(code) ?: if (ShaftUtils.config.routes.useBuiltIn) builtInRoute(code) else null
        cache[code] = route
        return route
    }

    private fun userRoute(code: String): Route? {
        val file = File(dir, "$code.json").takeIf { it.exists() }
            ?: if (code.endsWith("_C")) File(dir, "CRYSTAL.json").takeIf { it.exists() } else null
        return file?.let {
            try {
                Route(parse(it.readText()), builtIn = false)
            } catch (e: Exception) {
                ShaftUtils.logger.error("Could not read route ${it.name}", e)
                ShaftUtils.chat("§cCouldn't read route §f${it.name}§c: ${e.message}")
                null
            }
        }
    }

    private fun builtInRoute(code: String): Route? {
        val names = listOfNotNull(code, "CRYSTAL".takeIf { code.endsWith("_C") })
        for (name in names) {
            val text = javaClass.getResourceAsStream("/assets/shaftutils/routes/$name.json")?.reader()?.use { it.readText() } ?: continue
            return Route(parse(text), builtIn = true)
        }
        return null
    }

    /** Parses [text] and saves it as the route for [code]. Returns how many points it has. */
    fun import(code: String, text: String): Int {
        val points = parse(text)
        require(points.isNotEmpty()) { "no points found" }
        dir.mkdirs()
        File(dir, "$code.json").writeText(text.trim() + "\n")
        cache.remove(code)
        return points.size
    }

    fun delete(code: String): Boolean {
        cache.remove(code)
        return File(dir, "$code.json").delete()
    }

    fun list(): List<String> = dir.listFiles { f -> f.extension == "json" }?.map { it.nameWithoutExtension }?.sorted().orEmpty()

    fun reload() = cache.clear()

    private val types = mapOf(
        "TOPAZ" to "TOPA", "SAPPHIRE" to "SAPP", "AMETHYST" to "AMET", "AMBER" to "AMBE", "JADE" to "JADE",
        "TITANIUM" to "TITA", "UMBER" to "UMBE", "TUNGSTEN" to "TUNG", "FAIRY" to "FAIR", "LITTLEFOOT" to "LITT",
        "RUBY" to "RUBY", "ONYX" to "ONYX", "AQUAMARINE" to "AQUA", "CITRINE" to "CITR", "PERIDOT" to "PERI",
        "JASPER" to "JASP", "OPAL" to "OPAL",
    )
    private val variants = mapOf("1" to "1", "ONE" to "1", "2" to "2", "TWO" to "2", "C" to "C", "CRYSTAL" to "C", "L" to "L")

    /**
     * Turns what you typed into a shaft code: "TOPA_1" stays, "amber_1" -> "AMBE_1", "jasper crystal" -> "JASP_C",
     * "CRYSTAL" stays (the shared crystal route). Null if it isn't a real shaft.
     */
    fun normaliseCode(input: String): String? {
        val parts = input.trim().uppercase().split(Regex("[\\s_\\-]+")).filter { it.isNotEmpty() }
        if (parts == listOf("CRYSTAL")) return "CRYSTAL"
        if (parts.size != 2) return null
        val type = types[parts[0]] ?: parts[0].takeIf { it in types.values } ?: return null
        val variant = variants[parts[1]] ?: return null
        if ((type == "LITT") != (variant == "L")) return null
        return "${type}_$variant"
    }

    fun parse(text: String): List<RoutePoint> {
        val trimmed = text.trim()
        if (trimmed.startsWith("[") || trimmed.startsWith("{")) {
            val json = JsonParser.parseString(trimmed)
            val array = when {
                json.isJsonArray -> json.asJsonArray
                json.isJsonObject && json.asJsonObject.has("waypoints") -> json.asJsonObject.getAsJsonArray("waypoints")
                else -> throw IllegalArgumentException("expected a list of waypoints")
            }
            return fromJson(array)
        }
        return trimmed.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
            val coords = parts.take(3).map { it.toDoubleOrNull() ?: return@mapNotNull null }
            if (coords.size < 3) return@mapNotNull null
            RoutePoint(BlockPos.containing(coords[0], coords[1], coords[2]), parts.drop(3).joinToString(" ").ifEmpty { null }, null)
        }
    }

    private fun fromJson(array: JsonArray): List<RoutePoint> {
        val points = array.mapNotNull { point(it) }
        // Some tools save the list unordered with the order in the names ("1", "2", ...); honour that if so.
        val numbers = points.map { it.name?.trim()?.toDoubleOrNull() }
        return if (points.isNotEmpty() && numbers.all { it != null }) points.sortedBy { it.name!!.trim().toDouble() } else points
    }

    private fun point(e: JsonElement): RoutePoint? {
        if (e.isJsonArray) {
            val a = e.asJsonArray
            if (a.size() < 3) return null
            return RoutePoint(BlockPos.containing(a[0].asDouble, a[1].asDouble, a[2].asDouble), null, null)
        }
        if (!e.isJsonObject) return null
        val o = e.asJsonObject
        if (!o.has("x") || !o.has("y") || !o.has("z")) return null
        val name = (o.getAsJsonObject("options")?.get("name") ?: o.get("name"))?.takeIf { !it.isJsonNull }?.asString
        return RoutePoint(BlockPos.containing(o["x"].asDouble, o["y"].asDouble, o["z"].asDouble), name, colour(o))
    }

    private fun colour(o: JsonObject): Int? {
        if (!o.has("r") || !o.has("g") || !o.has("b")) return null
        val values = listOf("r", "g", "b").map { o[it].asDouble }
        val scaled = if (values.all { it <= 1.0 }) values.map { it * 255 } else values
        val (r, g, b) = scaled.map { it.toInt().coerceIn(0, 255) }
        return (r shl 16) or (g shl 8) or b
    }
}
