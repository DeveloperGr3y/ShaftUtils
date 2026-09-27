package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.profit.ItemIds
import io.github.developergr3y.shaftutils.profit.PriceType
import io.github.developergr3y.shaftutils.profit.Prices
import io.github.developergr3y.shaftutils.profit.ShaftProfit
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import net.minecraft.client.Minecraft

/**
 * What mining is making, laid out like a table:
 *
 *   [Insta-buy] [Shaft]
 *   Shaft Profit · Jasper
 *   Flawed Jasper        ×12,007      8.0m
 *   Rough Jasper         ×59,882      421k
 *   Ench Glacite             ×48      86k
 *   3 more…                           52k
 *   Corpse loot (2)                   1.1m
 *   Keys                             -3.0m
 *   Profit                            6.7m
 *   Profit/h                         35.0m
 *   Time                          11m 28s
 *
 * [Shaft] shows the shaft you're in (or the last one for a few minutes after leaving); [Session] adds up every shaft
 * since you started the game. The toggles only show (on a line above the title) while your inventory is open.
 */
object ProfitPanel : Panel("Shaft Profit") {
    private val config get() = ShaftUtils.config.profit
    private const val NAME_WIDTH = 110

    override var position: HudPosition
        get() = config.panelPosition
        set(value) {
            config.panelPosition = value
        }

    override fun defaultPosition() = HudPosition(x = 5, y = 150)

    private fun shaft(): ShaftProfit.Session? {
        ShaftProfit.session?.let { return it }
        val last = ShaftProfit.lastFinished ?: return null
        val ended = last.endedAt ?: return null
        return last.takeIf { System.currentTimeMillis() - ended < config.keepMinutes * 60_000L }
    }

    private fun session(): ShaftProfit.Session? = ShaftProfit.combine(ShaftProfit.finished + listOfNotNull(ShaftProfit.session))

    private fun shown(): ShaftProfit.Session? = if (config.sessionView) session() else shaft()

    // Shaft view: while there's a shaft to show. Session view: while any shaft this game has anything.
    override fun visible() = config.enabled && config.showPanel && Visibility.allowed(config.where, config.showWhen) &&
        (shaft() != null || (config.sessionView && session() != null))

    override fun lines(): List<PanelLine> {
        val s = shown() ?: return header("§8nothing yet")
        val t = ShaftProfit.totals(s)
        val end = s.endedAt ?: System.currentTimeMillis()
        val minutes = (end - s.startedAt) / 60_000.0
        val lines = mutableListOf<PanelLine>()

        val what = if (config.sessionView) {
            "§7${s.code} shaft${if (s.code == "1") "" else "s"}"
        } else {
            val last = if (s.endedAt != null && !Mineshaft.inShaft) " §8(last)" else ""
            "§7" + ShaftProfit.shaftLabel(s.code).removeSuffix(" shaft") + last
        }
        lines += header(what)

        // Items, most valuable first, rarity-coloured, then "N more…".
        val items = s.mining.entries.filter { it.value > 0 }
            .map { (name, n) -> Triple(name, n, ShaftProfit.value(name, n)) }
            .sortedByDescending { it.third ?: 0.0 }
        items.take(config.itemsShown).forEach { (name, n, value) ->
            lines += PanelLine.row(
                "  " + ItemIds.colourFor(name) + fit(ItemIds.shortName(name)),
                "§7×" + "%,d".format(n),
                value?.let { "§6" + ShaftProfit.coins(it) } ?: "§8?",
            )
        }
        val rest = items.drop(config.itemsShown)
        if (rest.isNotEmpty()) {
            lines += PanelLine.row(
                "  §7${rest.size} more…", "",
                "§6" + ShaftProfit.coins(rest.sumOf { it.third ?: 0.0 }),
                tooltip = rest.map { (name, n, v) -> "${ItemIds.colourFor(name)}$name §7×${"%,d".format(n)} §6${v?.let { ShaftProfit.coins(it) } ?: "?"}" },
            )
        }

        if (s.corpsesOpened > 0 || t.loot > 0) {
            lines += PanelLine.row(
                "§7Corpse loot §8(${s.corpsesOpened})", "", "§6" + ShaftProfit.coins(t.loot),
                tooltip = s.corpseLoot.entries.map { (name, n) -> "${ItemIds.colourFor(name)}$name §7×${"%,d".format(n)}" },
            )
        }
        if (t.keys > 0) lines += PanelLine.row("§7Keys", "", "§c-" + ShaftProfit.coins(t.keys))

        lines += PanelLine.row("§e§lProfit", "", "${colour(t.total)}§l${ShaftProfit.coins(t.total)}")
        if (minutes > 0.5) lines += PanelLine.row("§7Profit/h", "", colour(t.total) + ShaftProfit.coins(t.total / minutes * 60))
        lines += PanelLine.row("§7Time", "", "§b" + ShaftProfit.duration(minutes))
        if (t.unpriced.isNotEmpty()) {
            lines += PanelLine(
                listOf(Cell("§8${t.unpriced.size} not on the bazaar")),
                tooltip = listOf("§7Not priced (not on the bazaar):") + t.unpriced.map { "§f$it" },
            )
        }
        if (ShaftProfit.sackDataMissing()) {
            lines += PanelLine(
                listOf(Cell("§c⚠ No sack messages")),
                tooltip = listOf(
                    "§7Nothing from your sacks has been counted.",
                    "§7Turn on §fSack Notifications§7 in Hypixel's",
                    "§e/settings§7 → §fChat Settings§7.",
                ),
            )
            lines += PanelLine("§7Turn on §fSack Notifications §8(/settings)")
        }
        if (!Prices.loaded) lines += PanelLine("§cBazaar prices still loading…")
        return lines
    }

    /** "Shaft Profit · Jasper   [Insta-buy] [Shaft]" with the two switches as buttons. */
    /** The title, with the price / view switches on a line above it while your inventory is open. */
    private fun header(what: String): List<PanelLine> {
        val title = PanelLine("§6§lShaft Profit $what")
        if (!inInventory) return listOf(title)
        val buttons = PanelLine(
            listOf(
                Cell("§8[§f${config.priceType}§8]") { switchPrices() },
                Cell("§8[§f${if (config.sessionView) "Session" else "Shaft"}§8]") { switchView() },
            ),
        )
        return listOf(buttons, title)
    }

    override fun previewLines() = listOf(
        PanelLine("§6§lShaft Profit §7Jasper"),
        PanelLine.row("  §aFlawed Jasper", "§7×12,007", "§68.0m"),
        PanelLine.row("  §fRough Jasper", "§7×59,882", "§6421k"),
        PanelLine.row("  §9Ench Glacite", "§7×48", "§687k"),
        PanelLine.row("  §73 more…", "", "§652k"),
        PanelLine.row("§7Corpse loot §8(2)", "", "§61.1m"),
        PanelLine.row("§7Keys", "", "§c-3.0m"),
        PanelLine.row("§e§lProfit", "", "§6§l6.7m"),
        PanelLine.row("§7Profit/h", "", "§635.0m"),
        PanelLine.row("§7Time", "", "§b11m 28s"),
    )

    private fun switchPrices() {
        config.priceType = if (config.priceType == PriceType.INSTANT_SELL) PriceType.SELL_OFFER else PriceType.INSTANT_SELL
        ShaftUtils.saveConfig()
    }

    private fun switchView() {
        config.sessionView = !config.sessionView
        ShaftUtils.saveConfig()
    }

    /** Cuts a name down to the column width with "…". */
    private fun fit(name: String): String {
        val font = Minecraft.getInstance().font
        if (font.width(name) <= NAME_WIDTH) return name
        var cut = name
        while (cut.isNotEmpty() && font.width("$cut…") > NAME_WIDTH) cut = cut.dropLast(1)
        return "${cut.trimEnd()}…"
    }

    private fun colour(v: Double) = if (v < 0) "§c" else "§6"
}
