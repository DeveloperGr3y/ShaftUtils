package io.github.developergr3y.shaftutils.shaft

import io.github.developergr3y.shaftutils.util.stripFormatting
import net.minecraft.client.Minecraft
import net.minecraft.world.level.Level
import net.minecraft.world.scores.DisplaySlot
import net.minecraft.world.scores.PlayerTeam

enum class CorpseType(val label: String, val colour: String, val rgb: Int, val helmetIds: Set<String>, val key: String?) {
    LAPIS("Lapis", "§9", 0xFF5555FF.toInt(), setOf("LAPIS_ARMOR_HELMET"), null),
    UMBER("Umber", "§6", 0xFFFFAA00.toInt(), setOf("ARMOR_OF_YOG_HELMET"), "Umber Key"),
    TUNGSTEN("Tungsten", "§7", 0xFFAAAAAA.toInt(), setOf("MINERAL_HELMET"), "Tungsten Key"),
    VANGUARD("Vanguard", "§f", 0xFFFFFFFF.toInt(), setOf("VANGUARD_HELMET"), "Skeleton Key"),
    ;

    val formatted get() = "$colour$label"

    companion object {
        fun fromLabel(label: String) = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
        fun fromHelmetId(id: String) = entries.firstOrNull { id in it.helmetIds }
    }
}

/** One corpse line from the tab list, e.g. "Lapis: NOT LOOTED". */
data class CorpseStatus(val type: CorpseType, val looted: Boolean)

/**
 * Which Glacite Mineshaft you're in, and what corpses it has.
 *  - Shaft code: the scoreboard has a line containing e.g. "TOPA_1", "RUBY_C" (crystal) or "LITT_L".
 *  - Corpses: the tab list's Frozen Corpses widget has a line per corpse, e.g. "Lapis: NOT LOOTED".
 * A shaft is its own server, so the code can only change with the world: the scoreboard is only read for a little
 * while after joining a world, until the code turns up. In a shaft the tab list is checked four times a second for
 * corpse updates (the entry title waits on it). Nothing is checked outside shafts.
 */
object Mineshaft {
    private val codePattern = Regex("\\b([A-Z]{4})_([12CL])\\b")
    private val corpseLine = Regex("^\\s*(Lapis|Umber|Tungsten|Vanguard): (NOT LOOTED|LOOTED)\\s*$", RegexOption.IGNORE_CASE)
    private const val CHECK_INTERVAL_MS = 250L
    /** How long after joining a world to look for a shaft code on the scoreboard. */
    private const val SEARCH_MS = 15_000L

    /** e.g. "TOPA_1"; null when not in a mineshaft (or the code isn't on the scoreboard). */
    var code: String? = null
        private set

    /** Shaft type ("TOPA") and variant as the spawn data names them ("ONE", "TWO", "CRYSTAL"). */
    val type get() = code?.substringBefore('_')
    val variant
        get() = when (code?.substringAfter('_')) {
            "1" -> "ONE"
            "2" -> "TWO"
            "C" -> "CRYSTAL"
            "L" -> "ONE"
            else -> null
        }

    val inShaft get() = code != null

    var corpses: List<CorpseStatus> = emptyList()
        private set

    /** Bumped every time you arrive in a new shaft, so other features can reset. */
    var session = 0
        private set

    private var lastCheck = 0L
    private var lastLevel: Level? = null
    private var searchUntil = 0L

    fun tick(client: Minecraft) {
        val level = client.level
        val now = System.currentTimeMillis()
        if (level !== lastLevel) {
            lastLevel = level
            code = null
            corpses = emptyList()
            searchUntil = now + SEARCH_MS
        }
        if (level == null || now - lastCheck < CHECK_INTERVAL_MS) return
        if (code == null && now > searchUntil) return // not a shaft: nothing to check until the next world
        lastCheck = now

        if (code == null) {
            code = sidebarLines(client).firstNotNullOfOrNull { codePattern.find(it)?.value }
            if (code != null) session++
        }

        if (!inShaft) return
        val tab = client.connection?.listedOnlinePlayers
            ?.mapNotNull { it.tabListDisplayName?.string?.stripFormatting() } ?: emptyList()
        corpses = tab.mapNotNull { line ->
            corpseLine.find(line)?.let { m ->
                CorpseType.fromLabel(m.groupValues[1])?.let { CorpseStatus(it, m.groupValues[2].equals("LOOTED", true)) }
            }
        }
    }

    /** The sidebar's lines as shown on screen, top to bottom, without formatting. */
    private fun sidebarLines(client: Minecraft): List<String> {
        val scoreboard = client.level?.scoreboard ?: return emptyList()
        val objective = scoreboard.getDisplayObjective(DisplaySlot.SIDEBAR) ?: return emptyList()
        return scoreboard.listPlayerScores(objective)
            .filter { !it.isHidden }
            .sortedByDescending { it.value() }
            .map { entry ->
                val text = entry.display() ?: PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName())
                text.string.stripFormatting().trim()
            }
    }
}
