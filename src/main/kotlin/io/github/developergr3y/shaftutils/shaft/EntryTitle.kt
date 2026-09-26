package io.github.developergr3y.shaftutils.shaft

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.util.Compat
import net.minecraft.client.DeltaTracker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import kotlin.math.sin

/**
 * A title when you enter a shaft, once the tab list says what corpses it has:
 *
 *     Umber Shaft
 *     3 Lapis · 1 Umber
 *
 * Crystal shafts read "Jasper Crystal", "Peridot Crystal", ... in their gem's colours.
 * Jasper (and Jasper Crystal) and Vanguard (Fairy) shafts are the good ones, so they get a bigger title, a longer
 * hold and a fanfare.
 *
 * A glow around the screen edges goes with it, in the gem's colour: a quick fade for most shafts, and a gold pulse
 * for as long as the title shows for the special ones.
 */
object EntryTitle {
    private val gems = mapOf(
        "TOPA" to ("Topaz" to "§e"), "SAPP" to ("Sapphire" to "§9"), "AMET" to ("Amethyst" to "§5"),
        "AMBE" to ("Amber" to "§6"), "JADE" to ("Jade" to "§a"), "TITA" to ("Titanium" to "§f"),
        "UMBE" to ("Umber" to "§6"), "TUNG" to ("Tungsten" to "§7"), "FAIR" to ("Vanguard" to "§b"),
        "LITT" to ("Littlefoot's Den" to "§2"), "RUBY" to ("Ruby" to "§c"), "ONYX" to ("Onyx" to "§8"),
        "AQUA" to ("Aquamarine" to "§3"), "CITR" to ("Citrine" to "§e"), "PERI" to ("Peridot" to "§2"),
        "JASP" to ("Jasper" to "§d"), "OPAL" to ("Opal" to "§f"),
    )
    private val special = setOf("JASP", "FAIR")

    /** How long to wait for the tab list's corpse list before showing the title without it. */
    private const val WAIT_MS = 4_000L

    /** RGB of each "§" colour, for the screen border. */
    private val rgb = mapOf(
        "§2" to 0x00AA00, "§3" to 0x00AAAA, "§5" to 0xAA00AA, "§6" to 0xFFAA00, "§7" to 0xAAAAAA, "§8" to 0x555555,
        "§9" to 0x5555FF, "§a" to 0x55FF55, "§b" to 0x55FFFF, "§c" to 0xFF5555, "§d" to 0xFF55FF, "§e" to 0xFFFF55, "§f" to 0xFFFFFF,
    )
    private const val GOLD = 0xFFAA00

    // The border for the title on screen now.
    private var borderStart = 0L
    private var borderColour = 0xFFFFFF
    private var borderSpecial = false
    /** How long the current title stays up, so a special shaft's border can last as long. */
    private var titleMs = 0L

    private var shownFor = -1
    private var enteredAt = 0L
    private var session = -1

    /** Test titles waiting to be shown (from /shaftutils testtitle), one every few seconds. */
    private val queue = ArrayDeque<Pair<String, List<CorpseType>>>()
    private var nextTestAt = 0L

    /** Examples of each kind of title, for checking how they look. */
    fun examples(): List<Pair<String, List<CorpseType>>> = listOf(
        "UMBE_1" to listOf(CorpseType.LAPIS, CorpseType.LAPIS, CorpseType.LAPIS, CorpseType.UMBER),
        "PERI_C" to listOf(CorpseType.LAPIS, CorpseType.TUNGSTEN),
        "JASP_1" to listOf(CorpseType.LAPIS, CorpseType.LAPIS, CorpseType.UMBER),
        "JASP_C" to listOf(CorpseType.LAPIS, CorpseType.TUNGSTEN, CorpseType.UMBER),
        "FAIR_1" to listOf(CorpseType.VANGUARD, CorpseType.LAPIS, CorpseType.LAPIS),
        "LITT_L" to listOf(CorpseType.LAPIS, CorpseType.TUNGSTEN, CorpseType.UMBER, CorpseType.UMBER),
    )

    /** Queue test titles: one for [code], or every example if null. */
    fun test(code: String?) {
        queue.clear()
        if (code == null) {
            queue.addAll(examples())
        } else {
            val sample = examples().firstOrNull { it.first.substringBefore('_') == code.substringBefore('_') }?.second
                ?: listOf(CorpseType.LAPIS, CorpseType.LAPIS, CorpseType.UMBER)
            queue.add(code to sample)
        }
        nextTestAt = 0L
    }

    fun tick() {
        val now = System.currentTimeMillis()
        if (queue.isNotEmpty() && now >= nextTestAt) {
            val (code, corpses) = queue.removeFirst()
            show(code, corpses)
            nextTestAt = now + 5_000
        }
        val config = ShaftUtils.config.title
        if (!config.enabled || !Mineshaft.inShaft) return
        if (session != Mineshaft.session) {
            session = Mineshaft.session
            enteredAt = now
        }
        if (shownFor == session) return
        val corpses = Mineshaft.corpses
        if (corpses.isEmpty() && now - enteredAt < WAIT_MS) return
        shownFor = session
        val code = Mineshaft.code ?: return
        if (!config.shafts.shows(code.substringBefore('_'))) return
        show(code, corpses.map { it.type })
    }

    /** Shows the title for [code] with these corpses (also used by /shaftutils testtitles). */
    fun show(code: String, corpses: List<CorpseType>) {
        val type = code.substringBefore('_')
        val crystal = code.endsWith("_C")
        val (name, colour) = gems[type] ?: (type to "§f")
        val isSpecial = type in special
        // "Umber Shaft", "Jasper Crystal" (crystal shafts keep their gem's colour and theming), "Littlefoot's Den".
        val shaft = when {
            crystal -> "$name Crystal"
            type == "LITT" -> name
            else -> "$name Shaft"
        }

        val title = if (isSpecial) "§6§l✦ $colour§l${shaft.uppercase()} §6§l✦" else "$colour§l$shaft"
        val counts = corpses.groupingBy { it }.eachCount().entries
            .sortedByDescending { it.key.ordinal }
            .joinToString(" §8· ") { (t, n) -> "§f$n ${t.formatted}" }
        val subtitle = when {
            counts.isEmpty() -> "§7No corpses listed"
            isSpecial -> "$counts §8· §6§lLucky!"
            else -> counts
        }
        val seconds = if (isSpecial) ShaftUtils.config.title.specialSeconds else ShaftUtils.config.title.seconds
        titleMs = (seconds * 1000).toLong()
        if (isSpecial) Compat.title(Component.literal(title), Component.literal(subtitle), 10, (seconds * 20).toInt(), 20)
        else Compat.title(Component.literal(title), Component.literal(subtitle), 5, (seconds * 20).toInt(), 15)

        borderStart = System.currentTimeMillis()
        borderColour = rgb[colour] ?: 0xFFFFFF
        borderSpecial = isSpecial

        if (ShaftUtils.config.title.sounds) {
            if (isSpecial) {
                sound("ui.toast.challenge_complete", 1.0f)
                sound("entity.player.levelup", 1.2f)
            } else {
                sound("block.note_block.chime", 1.4f, 0.6f)
            }
        }
    }

    /** Draws the screen border for the current title (called every frame from the HUD). */
    fun render(graphics: GuiGraphicsExtractor, @Suppress("UNUSED_PARAMETER") deltaTracker: DeltaTracker) {
        if (!ShaftUtils.config.title.border || borderStart == 0L) return
        val since = System.currentTimeMillis() - borderStart
        // Special: pulses gold / gem colour while the title is up, then fades. Others: a quick fade.
        val (duration, fadeFrom) = if (borderSpecial) titleMs + 1_200L to titleMs else minOf(1_200L, titleMs + 500L) to 0L
        if (since > duration) return
        val fade = if (since < fadeFrom) 1f else 1f - (since - fadeFrom).toFloat() / (duration - fadeFrom)
        val colour = if (borderSpecial) {
            val pulse = (sin(since / 180.0) + 1) / 2 // 0..1, roughly twice a second
            blend(GOLD, borderColour, pulse.toFloat())
        } else {
            borderColour
        }
        val boost = if (borderSpecial) 1.3f else 1f
        val w = graphics.guiWidth()
        val h = graphics.guiHeight()
        for ((thickness, strength) in listOf(10 to 0.18f, 5 to 0.3f, 2 to 0.55f)) {
            val c = withAlpha(colour, (strength * boost * fade).coerceAtMost(1f))
            graphics.fill(0, 0, w, thickness, c)
            graphics.fill(0, h - thickness, w, h, c)
            graphics.fill(0, 0, thickness, h, c)
            graphics.fill(w - thickness, 0, w, h, c)
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int) = ((a shr shift and 0xFF) * (1 - t) + (b shr shift and 0xFF) * t).toInt()
        return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    private fun withAlpha(rgb: Int, alpha: Float) = ((alpha * 255).toInt().coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)

    private fun sound(name: String, pitch: Float, volume: Float = 1f) {
        val sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(name)) ?: return
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, pitch, volume))
    }
}
