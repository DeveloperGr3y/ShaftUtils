package io.github.developergr3y.shaftutils.gems

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.profit.ItemIds
import io.github.developergr3y.shaftutils.profit.ShaftProfit
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Compat
import io.github.developergr3y.shaftutils.util.stripFormatting
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.world.entity.player.Inventory

/** The gemstones that come as a Perfect, with their colour. */
enum class Gem(val label: String, val colour: String, val rgb: Int) {
    RUBY("Ruby", "§c", 0xFF5555),
    AMBER("Amber", "§6", 0xFFAA00),
    SAPPHIRE("Sapphire", "§b", 0x55FFFF),
    JADE("Jade", "§a", 0x55FF55),
    AMETHYST("Amethyst", "§5", 0xAA00AA),
    TOPAZ("Topaz", "§e", 0xFFFF55),
    JASPER("Jasper", "§d", 0xFF55FF),
    OPAL("Opal", "§f", 0xFFFFFF),
    ONYX("Onyx", "§8", 0x777777),
    AQUAMARINE("Aquamarine", "§3", 0x00AAAA),
    CITRINE("Citrine", "§6", 0xFFC040),
    PERIDOT("Peridot", "§2", 0x00AA00),
    ;

    override fun toString() = label

    fun next() = entries[(ordinal + 1) % entries.size]
    fun previous() = entries[(ordinal + entries.size - 1) % entries.size]

    companion object {
        fun from(label: String) = entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
    }
}

/**
 * How close you are to a Perfect gemstone, counting what's in your sacks and inventory.
 *
 * Chat never says how many gems your sacks hold, only what changes. So the totals come from opening the Gemstones
 * Sack (each gem's lore lists how many of each tier are stored), are kept between games, and then follow the
 * "[Sacks]" messages (added and removed) and PRISTINE! drops. Opening the sack again corrects any drift.
 *
 * Everything is counted in rough-gem equivalents: 80 rough = 1 flawed, 80 flawed = 1 fine, 80 fine = 1 flawless,
 * and 5 flawless (plus a crystal) = 1 perfect.
 *
 * Crystals aren't inventory items: the "Crystal Hollows Crystals" item lists each one as "Onyx ✔ Found" or
 * "Jasper ✖ Not Found". That's read whenever a menu showing it is open, and remembered.
 */
object PerfectGems {
    val tiers = listOf("Rough", "Flawed", "Fine", "Flawless")
    private val weights = listOf(1L, 80L, 6_400L, 512_000L)
    const val FLAWLESS = 512_000L
    const val PERFECT = 5 * FLAWLESS

    /** Gains within this long count towards the rate. */
    private const val RATE_WINDOW_MS = 10 * 60_000L
    /** Auto mode tracks the gem you've gained most of within this long. */
    private const val AUTO_WINDOW_MS = 5 * 60_000L
    /** PRISTINE! gems are counted straight away, then skipped when the same gems turn up in a sack message. */
    private const val SET_ASIDE_MS = 60_000L
    /** Sack totals changed by chat are saved at most this often (they're kept between games). */
    private const val SAVE_MS = 30_000L

    private val gemItem = Regex("^(Rough|Flawed|Fine|Flawless|Perfect) (\\w+) Gemstone$")
    private val sacksHeader = Regex("^\\[Sacks] ")
    private val sackLine = Regex("^\\s*([+-][\\d,]+) (.+?) \\((.+)\\)\\s*$")
    private val pristine = Regex("^PRISTINE! You found (.+?)(?: x([\\d,]+))?!")
    private val supercraft = Regex("^You Supercrafted (.+?)(?: x([\\d,]+))?!$")
    /** Lore of a gem in the Gemstones Sack, e.g. " Rough: 18,432 (...)"; single-item sacks say "Stored: 18,432/...". */
    private val tierLore = Regex("^\\s*(Rough|Flawed|Fine|Flawless): ([\\d,]+)")
    private val storedLore = Regex("^\\s*Stored: ([\\d,]+)")
    /** "  Onyx ✔ Found" / "  Jasper ✖ Not Found" in the Crystal Hollows Crystals item. */
    private val crystalLore = Regex("^\\s*(\\w+) \\S+ (Found|Not Found)\\s*$")

    private val config get() = ShaftUtils.config.perfect

    /** Gem item name -> how many are in your inventory right now. */
    private var inventory: Map<String, Long> = emptyMap()
    private val setAside = mutableMapOf<String, Pair<Long, Long>>()
    /** Supercrafted gems: they still change your totals when they reach your sacks, but aren't newly mined. */
    private val crafted = mutableMapOf<String, Pair<Long, Long>>()
    /** A gain of some gem, in rough equivalents. */
    private class Gain(val at: Long, val gem: Gem, val rough: Long)
    private val gains = ArrayDeque<Gain>()
    /** Rough equivalents gained of each gem since the game started. */
    private val sessionGained = mutableMapOf<Gem, Long>()
    var lastGain = 0L
        private set
    /** Auto mode's last pick, kept while there's nothing newer to go on. */
    private var autoGem: Gem? = null
    private var dirtySince = 0L

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) onChat(message) }
    }

    fun tick(client: Minecraft) {
        if (!config.enabled) return
        val player = client.player ?: return
        val counts = mutableMapOf<String, Long>()
        val inv = player.inventory
        for (i in 0 until Inventory.INVENTORY_SIZE) { // hotbar and main inventory, not armour
            val stack = inv.getItem(i)
            if (stack.isEmpty) continue
            val name = ShaftProfit.cleanName(stack.hoverName.string.stripFormatting())
            if (gemItem.matches(name)) counts.merge(name, stack.count.toLong(), Long::plus)
        }
        inventory = counts
        (Compat.screen as? AbstractContainerScreen<*>)?.let {
            readSack(it)
            readCrystals(it)
        }
        if (dirtySince != 0L && System.currentTimeMillis() - dirtySince > SAVE_MS) {
            dirtySince = 0L
            ShaftUtils.saveConfig()
        }
    }

    /** With a sack open: read the stored amounts from its items' lore. */
    private fun readSack(screen: AbstractContainerScreen<*>) {
        if (!screen.title.string.contains("Sack", ignoreCase = true)) return
        var changed = false
        for (slot in screen.menu.slots) {
            if (slot.container === Minecraft.getInstance().player?.inventory) continue
            val stack = slot.item
            if (stack.isEmpty) continue
            val name = ShaftProfit.cleanName(stack.hoverName.string.stripFormatting())
            val lore = stack.get(DataComponents.LORE)?.lines()?.map { it.string.stripFormatting() } ?: continue
            if (gemItem.matches(name)) {
                // One gem tier per item.
                lore.firstNotNullOfOrNull { storedLore.find(it) }?.let { changed = set(name, it.groupValues[1]) || changed }
                continue
            }
            // One item per gem, listing its tiers.
            val gem = Gem.entries.firstOrNull { name.contains(it.label, ignoreCase = true) } ?: continue
            for (line in lore) {
                val m = tierLore.find(line) ?: continue
                changed = set("${m.groupValues[1]} ${gem.label} Gemstone", m.groupValues[2]) || changed
            }
        }
        if (changed) ShaftUtils.saveConfig()
    }

    /** With any menu open that shows the Crystal Hollows Crystals item: which crystals you have. */
    private fun readCrystals(screen: AbstractContainerScreen<*>) {
        for (slot in screen.menu.slots) {
            val stack = slot.item
            if (stack.isEmpty || !stack.hoverName.string.stripFormatting().contains("Crystal Hollows Crystals")) continue
            val lore = stack.get(DataComponents.LORE)?.lines()?.map { it.string.stripFormatting() } ?: continue
            val found = mutableSetOf<String>()
            var seen = false
            for (line in lore) {
                val m = crystalLore.find(line) ?: continue
                val gem = Gem.from(m.groupValues[1]) ?: continue
                seen = true
                if (m.groupValues[2] == "Found") found += gem.label
            }
            if (seen && (found != config.crystals || !config.crystalsKnown)) {
                config.crystals = found
                config.crystalsKnown = true
                ShaftUtils.saveConfig()
            }
            return
        }
    }

    private fun set(name: String, amount: String): Boolean {
        val value = amount.replace(",", "").toLongOrNull() ?: return false
        setAside.remove(name)
        val gem = gemItem.matchEntire(name)?.groupValues?.get(2) ?: return false
        val known = config.synced.add(gem)
        return (config.sacks.put(name, value) != value) || known
    }

    private fun onChat(message: Component) {
        if (!config.enabled) return
        val text = message.string.stripFormatting()
        val now = System.currentTimeMillis()
        if (sacksHeader.containsMatchIn(text)) {
            for (hover in ShaftProfit.hovers(message).distinctBy { it.string }) {
                for (line in hover.string.lines()) {
                    val m = sackLine.find(line) ?: continue
                    val name = ShaftProfit.cleanName(m.groupValues[2])
                    if (!gemItem.matches(name)) continue
                    var amount = m.groupValues[1].replace(",", "").replace("+", "").toLong()
                    if (amount > 0) amount = use(setAside, name, amount, now)
                    if (amount > 0) {
                        val mined = use(crafted, name, amount, now)
                        if (mined < amount) change(name, amount - mined, now, gained = false)
                        amount = mined
                    }
                    if (amount != 0L) change(name, amount, now)
                }
            }
            return
        }
        supercraft.find(text)?.let {
            val name = ShaftProfit.cleanName(it.groupValues[1])
            if (!gemItem.matches(name)) return
            val amount = it.groupValues[2].replace(",", "").toLongOrNull() ?: 1
            crafted[name] = ((crafted[name]?.first ?: 0) + amount) to now + SET_ASIDE_MS
            return
        }
        pristine.find(text)?.let {
            val name = ShaftProfit.cleanName(it.groupValues[1])
            if (!gemItem.matches(name)) return
            val amount = it.groupValues[2].replace(",", "").toLongOrNull() ?: 1
            change(name, amount, now)
            setAside[name] = ((setAside[name]?.first ?: 0) + amount) to now + SET_ASIDE_MS
        }
    }

    /** Take up to [amount] of [name] from a ledger of expected arrivals; returns what's left over. */
    private fun use(ledger: MutableMap<String, Pair<Long, Long>>, name: String, amount: Long, now: Long): Long {
        val (held, until) = ledger[name] ?: return amount
        if (now > until) {
            ledger.remove(name)
            return amount
        }
        val used = minOf(held, amount)
        if (held - used > 0) ledger[name] = (held - used) to until else ledger.remove(name)
        return amount - used
    }

    /** Apply a sack change; [gained] = false for gems that came from crafting rather than mining. */
    private fun change(name: String, amount: Long, now: Long, gained: Boolean = true) {
        config.sacks[name] = ((config.sacks[name] ?: 0) + amount).coerceAtLeast(0)
        if (dirtySince == 0L) dirtySince = now
        val m = gemItem.matchEntire(name) ?: return
        val gem = Gem.from(m.groupValues[2]) ?: return
        if (amount > 0 && gained) {
            val rough = amount * weight(m.groupValues[1])
            sessionGained.merge(gem, rough, Long::plus)
            gains.addLast(Gain(now, gem, rough))
            lastGain = now
        }
    }

    private fun weight(tier: String) = weights.getOrElse(tiers.indexOf(tier)) { 0L }

    /** Has the Gemstones Sack been opened for this gem, so its sack totals are known? */
    fun synced(gem: Gem) = gem.label in config.synced

    /** Everything you have of [gem], in rough gems. */
    fun roughTotal(gem: Gem): Long = tiers.sumOf { tier ->
        val name = "$tier ${gem.label} Gemstone"
        ((config.sacks[name] ?: 0) + (inventory[name] ?: 0)) * weight(tier)
    }

    /** Whether you have [gem]'s crystal, or null if the Crystal Hollows Crystals item hasn't been seen yet. */
    fun hasCrystal(gem: Gem): Boolean? = if (config.crystalsKnown) gem.label in config.crystals else null

    fun sessionGained(gem: Gem) = sessionGained[gem] ?: 0L

    /** Rough gems of [gem] per hour over the last few minutes of mining, or null before there's enough to tell. */
    fun ratePerHour(gem: Gem): Double? {
        val now = System.currentTimeMillis()
        while (gains.isNotEmpty() && now - gains.first().at > RATE_WINDOW_MS) gains.removeFirst()
        val recent = gains.filter { it.gem == gem }
        if (recent.isEmpty()) return null
        val span = maxOf(now - recent.first().at, 60_000L)
        return recent.sumOf { it.rough } * 3_600_000.0 / span
    }

    /**
     * The gem the tracker shows. In auto mode: the gem you've gained most of in the last few minutes, else the gem of
     * the shaft you're in, else the last one it picked; otherwise the one you chose.
     */
    fun target(): Gem {
        if (!config.auto) return config.target
        val now = System.currentTimeMillis()
        val recent = gains.filter { now - it.at <= AUTO_WINDOW_MS }.groupBy { it.gem }
            .maxByOrNull { (_, g) -> g.sumOf { it.rough } }?.key
        val shaft = Mineshaft.type?.let { Gem.from(ItemIds.shaftName(it)) }
        return (recent ?: shaft ?: autoGem ?: config.target).also { autoGem = it }
    }
}
