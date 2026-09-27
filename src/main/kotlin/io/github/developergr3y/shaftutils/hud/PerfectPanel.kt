package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.gems.Gem
import io.github.developergr3y.shaftutils.gems.PerfectGems
import net.minecraft.client.gui.GuiGraphicsExtractor

/**
 * Progress towards the Perfect gemstone you've picked:
 *
 *   [Auto] [◂] [▸]              (inventory open only)
 *   Perfect Gem Tracker
 *   ❁ Perfect Jasper ×0.9              92.2%
 *   [█████][█████][█████][█████][███  ]
 *   Progress                4.61 / 5 flawless
 *   Crystal                             0/1 ✘
 *   This session                 +186k rough
 *   ETA                        39m @ 310k/h
 *
 * The bar is five segments, one per flawless. Once you have enough for a Perfect it says how many you can craft,
 * the bar goes dim green and the next Perfect fills over it. [Auto] follows the gem you're mining; [◂] [▸] or the
 * gem's name pick one yourself (inventory open).
 */
object PerfectPanel : Panel("Perfect Gem Tracker") {
    private val config get() = ShaftUtils.config.perfect
    override var position: HudPosition
        get() = config.panelPosition
        set(value) {
            config.panelPosition = value
        }

    override fun defaultPosition() = HudPosition(x = 5, y = 250)

    override fun visible() = config.enabled && Visibility.allowed(config.where, config.showWhen)

    override fun lines(): List<PanelLine> {
        val gem = PerfectGems.target()
        val total = PerfectGems.roughTotal(gem)
        return build(gem, total, PerfectGems.crystals(gem), PerfectGems.sessionGained(gem), PerfectGems.ratePerHour(gem), PerfectGems.synced(gem))
    }

    override fun previewLines() = build(Gem.JASPER, 2_360_832, 0, 186_000, 310_000.0, synced = true)

    private fun build(gem: Gem, total: Long, crystals: Int, session: Long, rate: Double?, synced: Boolean): List<PanelLine> {
        val perfects = total.toDouble() / PerfectGems.PERFECT
        val ready = (total / PerfectGems.PERFECT).toInt()
        val rest = total - ready * PerfectGems.PERFECT
        val flawless = rest.toDouble() / PerfectGems.FLAWLESS

        val lines = mutableListOf<PanelLine>()
        val changeTip = listOf(
            "§eClick §7[◂] [▸] or the gem's name to pick a gem",
            "§a[Auto]§7: follow the gem you're mining",
            "§7Or: §e/shaftutils perfect <gem|auto>",
        )
        val auto = Cell(if (config.auto) "§a[Auto]" else "§8[Auto]") {
            config.auto = !config.auto
            ShaftUtils.saveConfig()
        }
        if (inInventory) {
            lines += PanelLine(listOf(auto, Cell("§e[◂]") { change(Gem::previous) }, Cell("§e[▸]") { change(Gem::next) }), tooltip = changeTip)
        }
        lines += PanelLine("§6§lPerfect Gem Tracker")
        val count = "${if (ready > 0) "§a" else "§7"}×${trim(perfects)}"
        lines += PanelLine(
            listOf(
                Cell("${gem.colour}❁ Perfect ${gem.label} $count") { change(Gem::next) },
                Cell(if (ready > 0) "" else "§f${"%.1f".format(perfects * 100)}%"),
            ),
            columns = true,
            tooltip = if (inInventory) changeTip else null,
        )
        if (ready > 0) lines += PanelLine("§a✔ Can craft $ready×")
        lines += PanelLine.painted { g, x, y, width -> bar(g, x, y, width, flawless, ready > 0, gem.rgb) }
        lines += PanelLine.row(if (ready > 0) "§7Next one" else "§7Progress", "§f${"%.2f".format(flawless)}§8 / 5 flawless")
        val needed = maxOf(1, ready)
        lines += PanelLine.row("§7Crystal", if (crystals >= needed) "§a$crystals§8/$needed §a✔" else "§c$crystals§8/$needed §c✘")
        lines += PanelLine.row("§7This session", "§a+${compact(session)} rough")
        lines += if (rate == null || rate <= 0) {
            PanelLine.row(if (ready > 0) "§7Next in" else "§7ETA", "§8mine to see")
        } else {
            val hours = (PerfectGems.PERFECT - rest) / rate
            PanelLine.row(if (ready > 0) "§7Next in" else "§7ETA", "§f${time(hours)} §8@ ${compact(rate.toLong())}/h")
        }
        if (!synced) lines += PanelLine("§eOpen your Gemstones Sack to count it")
        return lines
    }

    /** Pick a gem yourself (leaves auto mode, starting from the gem it was showing). */
    private fun change(step: (Gem) -> Gem) {
        config.target = step(PerfectGems.target())
        config.auto = false
        ShaftUtils.saveConfig()
    }

    /** Five segments, one per flawless; [fill] is how many flawless you have towards the next Perfect. */
    private fun bar(g: GuiGraphicsExtractor, x: Int, y: Int, width: Int, fill: Double, lapped: Boolean, rgb: Int) {
        val gap = 2
        val segment = (width - gap * 4) / 5
        val top = y + 1
        val bottom = y + 7
        for (i in 0 until 5) {
            val left = x + i * (segment + gap)
            g.fill(left, top, left + segment, bottom, if (lapped) 0xFF2F5A22.toInt() else 0xFF2A2A30.toInt())
            val part = (fill - i).coerceIn(0.0, 1.0)
            if (part > 0) g.fill(left, top, left + (segment * part).toInt(), bottom, 0xFF000000.toInt() or (if (lapped) 0x55FF55 else rgb))
        }
    }

    /** 0.92 -> "0.9", 2.0 -> "2", 3.46 -> "3.5". */
    private fun trim(v: Double) = "%.1f".format(v).removeSuffix(".0")

    private fun compact(n: Long) = when {
        n >= 1_000_000 -> "%.2fm".format(n / 1_000_000.0)
        n >= 1_000 -> "%.1fk".format(n / 1_000.0)
        else -> n.toString()
    }

    private fun time(hours: Double): String {
        val minutes = (hours * 60).toLong()
        return if (minutes >= 60) "${minutes / 60}h ${minutes % 60}m" else "${minutes}m"
    }
}
