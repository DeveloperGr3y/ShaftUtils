package io.github.developergr3y.shaftutils.shaft

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.util.Compat
import net.minecraft.client.Minecraft
import net.minecraft.client.resources.sounds.SimpleSoundInstance
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier

/**
 * A title when you enter a shaft, once the tab list says what corpses it has:
 *
 *     Umber Shaft
 *     3 Lapis · 1 Umber
 *
 * Crystal shafts read "Jasper Crystal", "Peridot Crystal", ... in their gem's colours.
 * Jasper (and Jasper Crystal) and Vanguard (Fairy) shafts are the good ones, so they get a bigger title, a longer
 * hold and a fanfare.
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
        show(Mineshaft.code ?: return, corpses.map { it.type })
    }

    /** Shows the title for [code] with these corpses (also used by /shaftutils testtitle). */
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
        if (isSpecial) Compat.title(Component.literal(title), Component.literal(subtitle), 10, 80, 20)
        else Compat.title(Component.literal(title), Component.literal(subtitle), 5, 45, 15)

        if (ShaftUtils.config.title.sounds) {
            if (isSpecial) {
                sound("ui.toast.challenge_complete", 1.0f)
                sound("entity.player.levelup", 1.2f)
            } else {
                sound("block.note_block.chime", 1.4f, 0.6f)
            }
        }
    }

    private fun sound(name: String, pitch: Float, volume: Float = 1f) {
        val sound = BuiltInRegistries.SOUND_EVENT.getValue(Identifier.withDefaultNamespace(name)) ?: return
        Minecraft.getInstance().soundManager.play(SimpleSoundInstance.forUI(sound, pitch, volume))
    }
}
