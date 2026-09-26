package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.profit.ItemIds
import io.github.developergr3y.shaftutils.profit.PriceType
import io.github.developergr3y.shaftutils.profit.Prices
import io.github.developergr3y.shaftutils.profit.ShaftProfit
import io.github.developergr3y.shaftutils.shaft.Mineshaft

/**
 * What the current shaft has made so far (or the last one, for a few minutes after you leave): total and per hour,
 * the top mined items, and corpse loot minus keys. Click the Prices line with your inventory open to switch between
 * insta-buy and insta-sell prices.
 */
object ProfitPanel : Panel("Shaft Profit") {
    private val config get() = ShaftUtils.config.profit

    override var position: HudPosition
        get() = config.panelPosition
        set(value) {
            config.panelPosition = value
        }

    override fun defaultPosition() = HudPosition(x = 5, y = 150)

    private fun shown(): ShaftProfit.Session? {
        ShaftProfit.session?.let { return it }
        val last = ShaftProfit.lastFinished ?: return null
        val ended = last.endedAt ?: return null
        return last.takeIf { System.currentTimeMillis() - ended < config.keepMinutes * 60_000L }
    }

    override fun visible() = config.enabled && config.showPanel && shown() != null

    override fun lines(): List<PanelLine> {
        val s = shown() ?: return emptyList()
        val t = ShaftProfit.totals(s)
        val end = s.endedAt ?: System.currentTimeMillis()
        val minutes = (end - s.startedAt) / 60_000.0
        val finished = if (s.endedAt != null && !Mineshaft.inShaft) " §8(last shaft)" else ""
        val lines = mutableListOf(
            PanelLine("§6§lShaft Profit §7${ShaftProfit.shaftLabel(s.code).removeSuffix(" shaft")} §8${ShaftProfit.duration(minutes)}$finished"),
            PanelLine("§7Prices: §f[${config.priceType}] §8(click to switch)") { switchPrices() },
        )
        val perHour = if (minutes > 0.5) " §8(${ShaftProfit.coins(t.total / minutes * 60)}/h)" else ""
        lines += PanelLine("§7Total: ${colour(t.total)}${ShaftProfit.coins(t.total)}$perHour")
        lines += PanelLine("§7Mining: ${colour(t.mining)}${ShaftProfit.coins(t.mining)}")
        s.mining.entries
            .map { (name, n) -> Triple(name, n, (Prices.sellValue(ItemIds.idFor(name)) ?: 0.0) * n) }
            .filter { it.third > 0 }
            .sortedByDescending { it.third }
            .take(config.itemsShown)
            .forEach { (name, n, value) -> lines += PanelLine(" ${ItemIds.colourFor(name)}$name §8×${compact(n)} §6${ShaftProfit.coins(value)}") }
        val keys = if (t.keys > 0) " §8· keys §c-${ShaftProfit.coins(t.keys)}" else ""
        lines += PanelLine("§7Corpses: ${colour(t.corpses)}${ShaftProfit.coins(t.corpses)} §8(${s.corpsesOpened} opened)")
        if (s.corpsesOpened > 0 || t.loot > 0) lines += PanelLine(" §8loot §6${ShaftProfit.coins(t.loot)}$keys")
        if (t.unpriced.isNotEmpty()) lines += PanelLine("§8${t.unpriced.size} item${if (t.unpriced.size == 1) "" else "s"} not on the bazaar")
        if (!Prices.loaded) lines += PanelLine("§cBazaar prices still loading…")
        return lines
    }

    override fun previewLines() = listOf(
        PanelLine("§6§lShaft Profit §7Jade §88m 12s"),
        PanelLine("§7Prices: §f[Insta-sell] §8(click to switch)"),
        PanelLine("§7Total: §612.3m §8(90m/h)"),
        PanelLine("§7Mining: §68.1m"),
        PanelLine(" §aFlawed Jade Gemstone §8×140 §66.2m"),
        PanelLine(" §9Enchanted Glacite §8×40 §61.5m"),
        PanelLine("§7Corpses: §64.2m §8(2 opened)"),
        PanelLine(" §8loot §65.1m §8· keys §c-0.9m"),
    )

    private fun switchPrices() {
        config.priceType = if (config.priceType == PriceType.INSTANT_SELL) PriceType.SELL_OFFER else PriceType.INSTANT_SELL
        ShaftUtils.saveConfig()
    }

    private fun colour(v: Double) = if (v < 0) "§c" else "§6"
    private fun compact(n: Long) = if (n >= 1000) "%.1fk".format(n / 1000.0) else n.toString()
}
