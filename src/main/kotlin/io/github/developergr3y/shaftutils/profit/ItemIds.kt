package io.github.developergr3y.shaftutils.profit

/**
 * Item display name -> SkyBlock item id ("Rough Jade Gemstone" -> "ROUGH_JADE_GEM"), for looking up prices.
 * Sack and loot messages only give names. Ids seen on real items in your inventory win; otherwise the usual naming
 * rules are applied, with a few known exceptions.
 */
object ItemIds {
    private val learned = mutableMapOf<String, String>()

    private val gem = Regex("^(Rough|Flawed|Fine|Flawless|Perfect) (\\w+) Gemstone$")
    private val book = Regex("^(?:Enchanted Book \\()?([A-Za-z' ]+?) ([IVX]+)\\)?$")
    private val numerals = mapOf("I" to 1, "II" to 2, "III" to 3, "IV" to 4, "V" to 5, "VI" to 6, "VII" to 7, "VIII" to 8, "IX" to 9, "X" to 10)
    private val books = setOf("Ice Cold", "Lapidary", "Pristine", "Prismatic", "Compact")

    private val exceptions = mapOf(
        "Glacite Powder" to "GLACITE_POWDER",
        "Mithril Powder" to "MITHRIL_POWDER",
        "Gemstone Powder" to "GEMSTONE_POWDER",
    )

    /** Remember what a name really maps to (from an item's own id). */
    fun learn(name: String, id: String) {
        if (id.isNotEmpty()) learned[name] = id
    }

    fun idFor(name: String): String {
        learned[name]?.let { return it }
        exceptions[name]?.let { return it }
        gem.matchEntire(name)?.let { return "${it.groupValues[1].uppercase()}_${it.groupValues[2].uppercase()}_GEM" }
        book.matchEntire(name)?.let { m ->
            val enchant = m.groupValues[1]
            val level = numerals[m.groupValues[2]]
            if (enchant in books && level != null) return "ENCHANTMENT_${enchant.uppercase().replace(' ', '_')}_$level"
        }
        return name.uppercase().replace("'", "").replace(Regex("[^A-Z0-9]+"), "_").trim('_')
    }

    /** "Jade" for the shaft type code "JADE", "Topaz" for "TOPA", ... */
    fun shaftName(type: String?): String = when (type) {
        "TOPA" -> "Topaz"
        "SAPP" -> "Sapphire"
        "AMET" -> "Amethyst"
        "AMBE" -> "Amber"
        "JADE" -> "Jade"
        "TITA" -> "Titanium"
        "UMBE" -> "Umber"
        "TUNG" -> "Tungsten"
        "FAIR" -> "Fairy"
        "LITT" -> "Littlefoot's Den"
        "RUBY" -> "Ruby"
        "ONYX" -> "Onyx"
        "AQUA" -> "Aquamarine"
        "CITR" -> "Citrine"
        "PERI" -> "Peridot"
        "JASP" -> "Jasper"
        "OPAL" -> "Opal"
        else -> type ?: "?"
    }
}
