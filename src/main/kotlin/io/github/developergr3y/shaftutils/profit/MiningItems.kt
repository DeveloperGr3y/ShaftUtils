package io.github.developergr3y.shaftutils.profit

/**
 * The items the profit tracker counts as mined (by SkyBlock item id). Anything else that turns up in your sacks or
 * inventory in a shaft (keys, lanterns, tools swapped in by a loadout, armour) isn't mining profit and is ignored.
 * Rare drops from chat ("RARE DROP! ...") and corpse loot are counted separately, whatever they are.
 */
object MiningItems {
    private val gems = listOf("RUBY", "AMETHYST", "JADE", "SAPPHIRE", "AMBER", "TOPAZ", "JASPER", "OPAL", "ONYX", "AQUAMARINE", "CITRINE", "PERIDOT")
        .flatMap { gem -> listOf("ROUGH", "FLAWED", "FINE", "FLAWLESS").map { "${it}_${gem}_GEM" } }

    private val ids = gems.toSet() + setOf(
        // Ores and blocks, with their enchanted forms.
        "COBBLESTONE", "ENCHANTED_COBBLESTONE", "COAL", "ENCHANTED_COAL", "ENCHANTED_COAL_BLOCK",
        "IRON_INGOT", "ENCHANTED_IRON", "ENCHANTED_IRON_BLOCK", "GOLD_INGOT", "ENCHANTED_GOLD", "ENCHANTED_GOLD_BLOCK",
        "INK_SACK-4", "ENCHANTED_LAPIS_LAZULI", "ENCHANTED_LAPIS_LAZULI_BLOCK",
        "REDSTONE", "ENCHANTED_REDSTONE", "ENCHANTED_REDSTONE_BLOCK", "EMERALD", "ENCHANTED_EMERALD", "ENCHANTED_EMERALD_BLOCK",
        "DIAMOND", "ENCHANTED_DIAMOND", "ENCHANTED_DIAMOND_BLOCK", "OBSIDIAN", "ENCHANTED_OBSIDIAN",
        "ICE", "ENCHANTED_ICE", "ENCHANTED_PACKED_ICE", "HARD_STONE", "ENCHANTED_HARD_STONE",
        "MITHRIL_ORE", "ENCHANTED_MITHRIL", "TITANIUM_ORE", "ENCHANTED_TITANIUM",
        "GLACITE", "ENCHANTED_GLACITE", "UMBER", "ENCHANTED_UMBER", "TUNGSTEN", "ENCHANTED_TUNGSTEN",
        // Mining drops.
        "REFINED_MINERAL", "GLOSSY_GEMSTONE", "STARFALL", "MITHRIL_GOURMAND", "RARE_DIAMOND", "SUSPICIOUS_SCRAP",
        "DYE_EMERALD", "DYE_FOSSIL", "DYE_FROSTBITTEN", "DYE_JADE", "DYE_NYANZA", "PET_ITEM_PURE_MITHRIL_GEM",
        "SLUDGE_JUICE", "WORM_MEMBRANE", "DWARVEN_OS_BLOCK_BRAN", "PREHISTORIC_EGG", "PICKONIMBUS", "ASCENSION_ROPE",
        "WISHING_COMPASS", "OIL_BARREL", "JUNGLE_HEART", "TREASURITE", "YOGGIE",
        "GOBLIN_EGG", "GOBLIN_EGG_GREEN", "GOBLIN_EGG_YELLOW", "GOBLIN_EGG_RED", "GOBLIN_EGG_BLUE",
        "ELECTRON_TRANSMITTER", "FTX_3070", "ROBOTRON_REFLECTOR", "CONTROL_SWITCH", "SYNTHETIC_HEART", "SUPERLITE_MOTOR",
    )

    /** Is this item (by id, e.g. "ROUGH_JADE_GEM" or "INK_SACK:4") counted as mining profit? */
    fun counts(id: String) = id.replace(':', '-') in ids
}
