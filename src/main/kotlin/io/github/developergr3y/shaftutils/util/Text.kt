package io.github.developergr3y.shaftutils.util

import net.minecraft.network.chat.Component
import net.minecraft.network.chat.Style
import java.util.Optional

private val formatting = Regex("§.")
private val colourCode = Regex("§[0-9a-f]")

/** "§6Lapis§r: §cNOT LOOTED" -> "Lapis: NOT LOOTED". */
fun String.stripFormatting() = replace(formatting, "")

/** RGB value -> "§" code for Minecraft's 16 chat colours. */
private val legacyCodes = mapOf(
    0x000000 to '0', 0x0000AA to '1', 0x00AA00 to '2', 0x00AAAA to '3',
    0xAA0000 to '4', 0xAA00AA to '5', 0xFFAA00 to '6', 0xAAAAAA to '7',
    0x555555 to '8', 0x5555FF to '9', 0x55FF55 to 'a', 0x55FFFF to 'b',
    0xFF5555 to 'c', 0xFF55FF to 'd', 0xFFFF55 to 'e', 0xFFFFFF to 'f',
)

/** A chat component as a "§"-coded string, keeping its colours (getString() drops them). */
fun Component.toLegacyString(): String {
    val out = StringBuilder()
    visit({ style: Style, text: String ->
        if (text.isNotEmpty()) {
            style.color?.value?.let { legacyCodes[it] }?.let { out.append('§').append(it) }
            out.append(text)
        }
        Optional.empty<Unit>()
    }, Style.EMPTY)
    return out.toString()
}

/** The last colour code before [name] in [legacy], e.g. "§9" for "  +11 §9Enchanted Glacite (...)". */
fun colourBefore(legacy: String, name: String): String? {
    val index = legacy.indexOf(name)
    if (index < 0) return null
    return colourCode.findAll(legacy.substring(0, index)).lastOrNull()?.value
}

/** The first colour code in [legacy], e.g. an item name's rarity colour. */
fun firstColour(legacy: String): String? = colourCode.find(legacy)?.value
