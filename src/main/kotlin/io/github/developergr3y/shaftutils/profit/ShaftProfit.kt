package io.github.developergr3y.shaftutils.profit

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.shaft.CorpseType
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Compat
import io.github.developergr3y.shaftutils.util.colourBefore
import io.github.developergr3y.shaftutils.util.firstColour
import io.github.developergr3y.shaftutils.util.stripFormatting
import io.github.developergr3y.shaftutils.util.toLegacyString
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.ClickEvent
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.world.entity.player.Inventory
import kotlin.math.abs

/**
 * Profit per mineshaft: what you mined, what the corpses gave, minus the keys used to open them.
 *
 * Items come from three places (the usual way SkyBlock profit trackers work):
 *  - "[Sacks] +1,234 items" messages: hovering them lists every item added, e.g. "+1,234 Rough Jade Gemstone (...)".
 *    They arrive every ~30s, and Hypixel drops the unsent batch when you change server, so whatever went into your
 *    sacks in the last few seconds before leaving isn't counted (usually little: you're walking out by then).
 *  - Your inventory: anything new that didn't go to your sacks.
 *  - The corpse loot summary in chat, counted as corpse loot. Those items also turn up in your sacks/inventory later,
 *    so they're set aside and not counted a second time as mining.
 * Moving items out of your sacks ("Moved 64 X from your Sacks to your inventory.") isn't a gain, and neither is
 * crafting: "You Supercrafted Flawed Jade Gemstone x4,490!" sets those aside, since the gems they're made from were
 * already counted when you mined them.
 */
object ShaftProfit {
    class Session(val code: String, val startedAt: Long) {
        var endedAt: Long? = null
        val mining = linkedMapOf<String, Long>()
        val corpseLoot = linkedMapOf<String, Long>()
        val keysUsed = linkedMapOf<CorpseType, Int>()
        var corpsesOpened = 0
    }

    /** A moment after leaving before posting, for anything still arriving from the shaft (not a late sack batch: none comes). */
    private const val TAIL_MS = 1_500L
    /** Corpse loot and sack withdrawals are set aside for this long while they turn up in sacks/inventory. */
    private const val SET_ASIDE_MS = 60_000L

    private val sacksHeader = Regex("^\\[Sacks] ")
    private val sackLine = Regex("^\\s*([+-][\\d,]+) (.+?) \\((.+)\\)\\s*$")
    private val moved = Regex("^Moved ([\\d,]+) (.+?) from your Sacks to your inventory\\.?$")
    /** "PRISTINE! You found ❁ Flawed Jasper Gemstone x4!" */
    private val pristine = Regex("^PRISTINE! You found (.+?)(?: x([\\d,]+))?!$")
    private val supercraft = Regex("^You Supercrafted (.+?)(?: x([\\d,]+))?!$")
    private val lootLine = Regex("^\\s+(.+?)(?: x([\\d,]+))?\\s*$")

    var session: Session? = null
        private set
    /** The shaft you most recently left, shown by the profit panel until the next one starts. */
    var lastFinished: Session? = null
        private set
    /** Every shaft since you started the game (for the panel's Session view). */
    val finished = mutableListOf<Session>()

    /** Hypixel puts icon glyphs (private-use characters) before some names, e.g. gemstones. Strip them. */
    private val icons = Regex("[\\uE000-\\uF8FF]")
    fun cleanName(name: String) = name.replace(icons, "").replace(Regex("\\s+"), " ").trim()
    private var lastSession = -1

    /** Items set aside: name -> (amount, until). Mining gains of these are skipped until they've been used up. */
    private val setAside = mutableMapOf<String, Pair<Long, Long>>()
    /** Inventory-only set-asides (sack withdrawals). */
    private val setAsideInventory = mutableMapOf<String, Pair<Long, Long>>()

    private var inventoryBaseline: Map<String, Long>? = null
    private var readingLootUntil = 0L

    fun register() {
        ClientReceiveMessageEvents.GAME.register { message, overlay -> if (!overlay) onChat(message) }
    }

    fun tick(client: Minecraft) {
        val config = ShaftUtils.config.profit
        if (!config.enabled) return
        val now = System.currentTimeMillis()
        val current = session

        if (Mineshaft.inShaft && Mineshaft.session != lastSession) {
            current?.let { finish(it) }
            lastSession = Mineshaft.session
            session = Session(Mineshaft.code ?: "?", now)
            setAside.clear()
            setAsideInventory.clear()
            inventoryBaseline = null
            Prices.refreshIfStale()
        } else if (current != null && !Mineshaft.inShaft) {
            if (current.endedAt == null) current.endedAt = now
            if (now - current.endedAt!! > TAIL_MS) finish(current)
        }
        if (session != null && Mineshaft.inShaft) readInventory(client)
    }

    // --- sources ---

    private fun onChat(message: Component) {
        val s = session ?: return
        if (!ShaftUtils.config.profit.enabled) return
        val text = message.string.stripFormatting()
        val now = System.currentTimeMillis()

        if (sacksHeader.containsMatchIn(text)) {
            for (hover in hovers(message).distinctBy { it.string }) {
                var adding = true
                for (legacyLine in hover.toLegacyString().lines()) {
                    val line = legacyLine.stripFormatting()
                    if (line.contains("Removed items", ignoreCase = true)) adding = false
                    if (line.contains("Added items", ignoreCase = true)) adding = true
                    val m = sackLine.find(line) ?: continue
                    val amount = m.groupValues[1].replace(",", "").toLong()
                    if (adding && amount > 0) addMining(s, cleanName(m.groupValues[2]), amount, fromInventory = false)
                }
            }
            return
        }
        pristine.find(text)?.let {
            // Counted now, so nothing is lost when you leave before the next sack batch; set aside so the same gems
            // aren't counted again when they reach your sacks.
            val name = cleanName(it.groupValues[1])
            val amount = it.groupValues[2].replace(",", "").toLongOrNull() ?: 1
            s.mining.merge(name, amount, Long::plus)
            setAside[name] = ((setAside[name]?.first ?: 0) + amount) to now + SET_ASIDE_MS
            return
        }
        supercraft.find(text)?.let {
            val name = cleanName(it.groupValues[1])
            val amount = it.groupValues[2].replace(",", "").toLongOrNull() ?: 1
            setAside[name] = ((setAside[name]?.first ?: 0) + amount) to now + SET_ASIDE_MS
            return
        }
        moved.find(text)?.let {
            val name = cleanName(it.groupValues[2])
            val amount = it.groupValues[1].replace(",", "").toLong()
            setAsideInventory[name] = ((setAsideInventory[name]?.first ?: 0) + amount) to now + SET_ASIDE_MS
            return
        }
        if (text.contains("CORPSE LOOT", ignoreCase = true)) {
            readingLootUntil = now + 1_500
            inRewards = false
            text.lines().drop(1).forEach { readLootLine(s, it, now) }
            return
        }
        if (now < readingLootUntil) {
            text.lines().forEach { readLootLine(s, it, now) }
            // Loot lines are coloured by rarity.
            for (legacyLine in message.toLegacyString().lines()) {
                val m = lootLine.matchEntire(legacyLine.stripFormatting()) ?: continue
                val name = cleanName(m.groupValues[1].removePrefix("+"))
                ItemIds.learnColour(name, colourBefore(legacyLine, name))
            }
        }
    }

    /** Between "REWARDS" and the closing ▬▬▬ border of a corpse loot message. */
    private var inRewards = false
    /** Lines in the loot block that aren't items (milestones, XP, bonus drop notes). */
    private val notAnItem = Regex("(?i)milestone|\\bxp\\b|bonus drop|skyblock xp|rng meter|you summoned")

    private fun readLootLine(s: Session, line: String, now: Long) {
        if (line.isBlank()) return
        if (line.trim().all { it == '▬' }) {
            // Closing border: the loot list is over (milestone rewards can follow in a separate block).
            if (inRewards) readingLootUntil = 0
            inRewards = false
            return
        }
        if (line.trim().equals("REWARDS", ignoreCase = true)) {
            inRewards = true
            return
        }
        if (!inRewards || notAnItem.containsMatchIn(line)) return
        val m = lootLine.matchEntire(line) ?: return
        val name = cleanName(m.groupValues[1].removePrefix("+"))
        val amount = m.groupValues[2].replace(",", "").toLongOrNull() ?: 1
        if (name.isEmpty() || name.length > 48) return
        s.corpseLoot.merge(name, amount, Long::plus)
        setAside[name] = ((setAside[name]?.first ?: 0) + amount) to now + SET_ASIDE_MS
    }

    /** A corpse was really looted (tab list confirmed): count the key it needed. */
    fun onCorpseLooted(type: CorpseType) {
        val s = session ?: return
        s.corpsesOpened++
        if (type.key != null) s.keysUsed.merge(type, 1, Int::plus)
    }

    private fun readInventory(client: Minecraft) {
        val player = client.player ?: return
        // While a menu is open, items move to and from your cursor; count again from scratch afterwards.
        if (Compat.screen != null) {
            inventoryBaseline = null
            return
        }
        val counts = mutableMapOf<String, Long>()
        val inventory = player.inventory
        // Hotbar and main inventory only: armour isn't loot, and Hypixel resends it (e.g. animated dyes change it
        // whenever you open your inventory), which would look like new items.
        for (i in 0 until Inventory.INVENTORY_SIZE) {
            val stack = inventory.getItem(i)
            if (stack.isEmpty) continue
            val name = cleanName(stack.hoverName.string.stripFormatting())
            if (name == "Enchanted Book") continue
            ItemIds.learnColour(name, firstColour(stack.hoverName.toLegacyString()))
            val id = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "").orEmpty()
            ItemIds.learn(name, id)
            counts.merge(name, stack.count.toLong(), Long::plus)
        }
        val before = inventoryBaseline
        inventoryBaseline = counts
        if (before == null) return
        val s = session ?: return
        for (name in counts.keys + before.keys) {
            val change = (counts[name] ?: 0) - (before[name] ?: 0)
            when {
                change > 0 -> addMining(s, name, change, fromInventory = true)
                // Used up while mining (e.g. Compact turning Hard Stone into Enchanted Hard Stone, which then goes to
                // your sacks and is counted there). Keys are already charged to the corpse, so they don't count.
                change < 0 && !name.endsWith(" Key") -> removeMining(s, name, -change)
            }
        }
    }

    private fun addMining(s: Session, name: String, amount: Long, fromInventory: Boolean) {
        var left = amount
        val now = System.currentTimeMillis()
        for (ledger in if (fromInventory) listOf(setAsideInventory, setAside) else listOf(setAside)) {
            val (held, until) = ledger[name] ?: continue
            if (now > until) {
                ledger.remove(name)
                continue
            }
            val used = minOf(held, left)
            left -= used
            if (held - used > 0) ledger[name] = (held - used) to until else ledger.remove(name)
            if (left == 0L) return
        }
        s.mining.merge(name, left, Long::plus)
    }

    private fun removeMining(s: Session, name: String, amount: Long) {
        s.mining.merge(name, -amount, Long::plus)
        if (s.mining[name] == 0L) s.mining.remove(name)
    }

    // --- totals ---

    class Totals(val mining: Double, val loot: Double, val keys: Double, val unpriced: Set<String>) {
        val corpses get() = loot - keys
        val total get() = mining + corpses
    }

    /** Several shafts added together (the Session view). Its start/end span the time actually spent in shafts. */
    fun combine(sessions: List<Session>): Session? {
        if (sessions.isEmpty()) return null
        val now = System.currentTimeMillis()
        val millis = sessions.sumOf { (it.endedAt ?: now) - it.startedAt }
        val out = Session("${sessions.size}", now - millis).also { it.endedAt = now }
        for (s in sessions) {
            s.mining.forEach { (k, v) -> out.mining.merge(k, v, Long::plus) }
            s.corpseLoot.forEach { (k, v) -> out.corpseLoot.merge(k, v, Long::plus) }
            s.keysUsed.forEach { (k, v) -> out.keysUsed.merge(k, v, Int::plus) }
            out.corpsesOpened += s.corpsesOpened
        }
        return out
    }

    /** Value of [amount] of [name] at the chosen prices, or null if it isn't on the bazaar. */
    fun value(name: String, amount: Long) = Prices.sellValue(ItemIds.idFor(name))?.let { it * amount }

    fun resetSession() {
        finished.clear()
        lastFinished = null
    }

    fun totals(s: Session): Totals {
        val unpriced = mutableSetOf<String>()
        fun value(items: Map<String, Long>) = items.entries.sumOf { (name, amount) ->
            val price = Prices.sellValue(ItemIds.idFor(name))
            if (price == null) {
                if (!name.contains("Powder", ignoreCase = true)) unpriced += name
                0.0
            } else price * amount
        }
        val keys = s.keysUsed.entries.sumOf { (type, n) ->
            val id = ItemIds.idFor(type.key!!)
            (Prices.buyCost(id) ?: 0.0.also { unpriced += type.key }) * n
        }
        return Totals(value(s.mining), value(s.corpseLoot), keys, unpriced)
    }

    private fun finish(s: Session) {
        session = null
        val end = s.endedAt ?: System.currentTimeMillis()
        if (s.endedAt == null) s.endedAt = end
        if (s.mining.isEmpty() && s.corpseLoot.isEmpty() && s.keysUsed.isEmpty()) return
        lastFinished = s
        finished += s
        val t = totals(s)
        val minutes = (end - s.startedAt) / 60_000.0
        if (ShaftUtils.config.profit.chatSummary) postSummary(s, t, minutes)
    }

    private fun postSummary(s: Session, t: Totals, minutes: Double) {
        val config = ShaftUtils.config.profit
        val shaft = shaftLabel(s.code)
        val perHour = if (minutes > 0.5) " §8(${duration(minutes)}, ${coins(t.total / minutes * 60)}/h)" else ""
        val lines = mutableListOf("§fProfit from §a$shaft§f: ${colour(t.total)}${coins(t.total)}$perHour")
        if (config.breakdown) {
            val keys = if (t.keys > 0) " − ${coins(t.keys)} keys" else ""
            lines += " §7Corpses: ${colour(t.corpses)}${coins(t.corpses)} §8(${s.corpsesOpened} opened: ${coins(t.loot)} loot$keys)"
            val top = s.mining.entries.sortedByDescending { (name, n) -> (Prices.sellValue(ItemIds.idFor(name)) ?: 0.0) * n }
                .take(3).joinToString { (name, n) -> "$name ×${compact(n)}" }
            lines += " §7Mining: ${colour(t.mining)}${coins(t.mining)}${if (top.isNotEmpty()) " §8($top)" else ""}"
            if (t.unpriced.isNotEmpty()) lines += " §8Not priced: ${t.unpriced.take(5).joinToString()}"
            if (!Prices.loaded) lines += " §cBazaar prices haven't loaded yet, so values may show as 0."
        }
        lines.forEach { ShaftUtils.chat(it) }
        if (config.shareButtons) {
            val plain = "[ShaftUtils] Profit from $shaft: ${coins(t.total)} (Corpses ${coins(t.corpses)}, Mining ${coins(t.mining)})"
            Compat.chat.addClientSystemMessage(
                Component.literal("§b[ShaftUtils] ")
                    .append(button("§e[Copy]", ClickEvent.CopyToClipboard(plain), "Copy: §f$plain"))
                    .append(Component.literal(" "))
                    .append(button("§d[Party]", ClickEvent.SuggestCommand("/pc $plain"), "Puts this in your chat box:\n§f/pc $plain"))
                    .append(Component.literal(" "))
                    .append(button("§2[Guild]", ClickEvent.SuggestCommand("/gc $plain"), "Puts this in your chat box:\n§f/gc $plain")),
            )
        }
    }

    /** "Jade shaft", "Ruby Crystal shaft". */
    fun shaftLabel(code: String): String {
        val type = code.substringBefore('_')
        val crystal = if (code.endsWith("_C")) " Crystal" else ""
        return "${ItemIds.shaftName(type)}$crystal shaft"
    }

    private fun button(label: String, click: ClickEvent, hover: String) = Component.literal(label).withStyle {
        it.withClickEvent(click).withHoverEvent(HoverEvent.ShowText(Component.literal("§7$hover")))
    }

    /** Every hover text in a message (Hypixel puts the sack item list in one). */
    fun hovers(c: Component): List<Component> {
        val out = mutableListOf<Component>()
        fun walk(x: Component) {
            (x.style.hoverEvent as? HoverEvent.ShowText)?.let { out += it.value() }
            x.siblings.forEach { walk(it) }
        }
        walk(c)
        return out
    }

    fun coins(v: Double): String {
        val a = abs(v)
        val sign = if (v < 0) "-" else ""
        return sign + when {
            a >= 1e9 -> "%.2fb".format(a / 1e9)
            a >= 1e6 -> "%.1fm".format(a / 1e6)
            a >= 1e3 -> "%.0fk".format(a / 1e3)
            else -> "%.0f".format(a)
        }
    }

    private fun compact(n: Long) = if (n >= 1000) "%.1fk".format(n / 1000.0) else n.toString()
    private fun colour(v: Double) = if (v < 0) "§c" else "§6"
    fun duration(minutes: Double) = "${minutes.toInt()}m ${((minutes % 1) * 60).toInt()}s"
}
