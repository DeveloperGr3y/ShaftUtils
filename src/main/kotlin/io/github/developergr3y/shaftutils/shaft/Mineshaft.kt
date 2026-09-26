package io.github.developergr3y.shaftutils.shaft

import io.github.developergr3y.shaftutils.debug.Probe
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
 * Checked once a second; resets when you change world.
 */
object Mineshaft {
    private val codePattern = Regex("\\b([A-Z]{4})_([12CL])\\b")
    private val corpseLine = Regex("^\\s*(Lapis|Umber|Tungsten|Vanguard): (NOT LOOTED|LOOTED)\\s*$", RegexOption.IGNORE_CASE)
    private const val CHECK_INTERVAL_MS = 1_000L

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
    private var lastCorpses: List<CorpseStatus> = emptyList()

    fun tick(client: Minecraft) {
        val level = client.level
        if (level !== lastLevel) {
            lastLevel = level
            if (code != null) Probe.log("shaft_leave", "code" to code)
            code = null
            corpses = emptyList()
        }
        val now = System.currentTimeMillis()
        if (level == null || now - lastCheck < CHECK_INTERVAL_MS) return
        lastCheck = now

        val sidebar = sidebarLines(client)
        val found = sidebar.firstNotNullOfOrNull { codePattern.find(it)?.value }
        if (found != code) {
            code = found
            if (found != null) {
                session++
                Probe.log("shaft_enter", "code" to found, "scoreboard" to sidebar)
            }
        }

        if (!inShaft) return
        val tab = client.connection?.listedOnlinePlayers
            ?.mapNotNull { it.tabListDisplayName?.string?.stripFormatting() } ?: emptyList()
        corpses = tab.mapNotNull { line ->
            corpseLine.find(line)?.let { m ->
                CorpseType.fromLabel(m.groupValues[1])?.let { CorpseStatus(it, m.groupValues[2].equals("LOOTED", true)) }
            }
        }
        if (corpses != lastCorpses) {
            lastCorpses = corpses
            Probe.log("tab_corpses", "code" to code, "corpses" to corpses.map { "${it.type.label}:${if (it.looted) "LOOTED" else "NOT LOOTED"}" })
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
