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
        return build(gem, total, PerfectGems.hasCrystal(gem), PerfectGems.sessionGained(gem), PerfectGems.ratePerHour(gem), PerfectGems.synced(gem))
    }

    override fun previewLines() = build(Gem.JASPER, 2_360_832, false, 186_000, 310_000.0, synced = true)

    private fun build(gem: Gem, total: Long, crystal: Boolean?, session: Long, rate: Double?, synced: Boolean): List<PanelLine> {
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
        // You can only hold one of each crystal.
        lines += PanelLine.row(
            "§7Crystal",
            when (crystal) {
                true -> "§a1§8/1 §a✔"
                false -> "§c0§8/1 §c✘"
                null -> "§8? (see below)"
            },
        )
        lines += PanelLine.row("§7This session", "§a+${compact(session)} rough")
        lines += if (rate == null || rate <= 0) {
            PanelLine.row(if (ready > 0) "§7Next in" else "§7ETA", "§8mine to see")
        } else {
            val hours = (PerfectGems.PERFECT - rest) / rate
            PanelLine.row(if (ready > 0) "§7Next in" else "§7ETA", "§f${time(hours)} §8@ ${compact(rate.toLong())}/h")
        }
        if (!synced) lines += PanelLine("§eOpen your Gemstones Sack to count it")
        if (crystal == null) lines += PanelLine("§eOpen a menu with Crystal Hollows Crystals")
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
        val top = y
        val bottom = y + 8
        val colour = if (lapped) 0x55FF55 else rgb
        for (i in 0 until 5) {
            val left = x + i * (segment + gap)
            // Empty track, with a dark rim so it reads as a slot.
            rounded(g, left, top, left + segment, bottom, 0xFF16161A.toInt())
            rounded(g, left + 1, top + 1, left + segment - 1, bottom - 1, if (lapped) 0xFF2F5A22.toInt() else 0xFF2E2E36.toInt())
            val part = (fill - i).coerceIn(0.0, 1.0)
            val right = left + 1 + ((segment - 2) * part).toInt()
            if (right - (left + 1) < 1) continue
            // The fill: a lighter top edge and a darker bottom edge give it a bit of depth.
            rounded(g, left + 1, top + 1, right, bottom - 1, argb(colour))
            g.fill(left + 2, top + 1, right - 1, top + 2, argb(shade(colour, 1.35)))
            g.fill(left + 2, bottom - 2, right - 1, bottom - 1, argb(shade(colour, 0.7)))
        }
    }

    /** A box with its four corner pixels left out, so it looks rounded. */
    private fun rounded(g: GuiGraphicsExtractor, x0: Int, y0: Int, x1: Int, y1: Int, colour: Int) {
        if (x1 - x0 < 3 || y1 - y0 < 3) {
            g.fill(x0, y0, x1, y1, colour)
            return
        }
        g.fill(x0 + 1, y0, x1 - 1, y1, colour)
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, colour)
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, colour)
    }

    private fun argb(rgb: Int) = 0xFF000000.toInt() or rgb

    /** Lighter (factor > 1, towards white) or darker (factor < 1) version of a colour. */
    private fun shade(rgb: Int, factor: Double): Int {
        fun channel(shift: Int): Int {
            val c = rgb shr shift and 0xFF
            val v = if (factor >= 1) c + (255 - c) * (factor - 1) else c * factor
            return v.toInt().coerceIn(0, 255) shl shift
        }
        return channel(16) or channel(8) or channel(0)
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
