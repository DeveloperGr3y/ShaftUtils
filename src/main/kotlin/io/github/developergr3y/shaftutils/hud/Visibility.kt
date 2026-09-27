package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.gems.PerfectGems
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import io.github.developergr3y.shaftutils.util.Compat
import io.github.developergr3y.shaftutils.util.Location
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.component.DataComponents

enum class ShowWhere(private val label: String) {
    MINING_ISLANDS("Mining islands"),
    MINING_AND_CRIMSON("Mining islands + Crimson Isle"),
    SHAFTS("Glacite Mineshafts only"),
    ANYWHERE("Anywhere in SkyBlock"),
    ;

    override fun toString() = label
}

enum class ShowWhen(private val label: String) {
    MINING("Mining or tool in hand"),
    TOOL("Tool in hand only"),
    ALWAYS("Always"),
    ;

    override fun toString() = label
}

/** Shared "should this tracker show right now?" rules for the Where / Show When settings. */
object Visibility {
    /** Mined something within this long counts as mining. */
    private const val MINING_MS = 5 * 60_000L
    private val tools = Regex("PICKAXE|DRILL|GAUNTLET|PICKONIMBUS")

    fun allowed(where: ShowWhere, showWhen: ShowWhen): Boolean {
        // Nothing shows outside SkyBlock (the GUI editor still shows everything).
        if (!Location.onSkyBlock && !Mineshaft.inShaft) return false
        when (where) {
            ShowWhere.MINING_ISLANDS -> if (!Location.onMiningIsland) return false
            ShowWhere.MINING_AND_CRIMSON -> if (!Location.onMiningIsland && Location.area != "Crimson Isle") return false
            ShowWhere.SHAFTS -> if (!Mineshaft.inShaft) return false
            ShowWhere.ANYWHERE -> {}
        }
        // With your inventory open it always shows, so the tracker's buttons can be reached.
        if (Compat.screen is AbstractContainerScreen<*>) return true
        return when (showWhen) {
            ShowWhen.ALWAYS -> true
            ShowWhen.TOOL -> holdingTool()
            ShowWhen.MINING -> Mineshaft.inShaft || System.currentTimeMillis() - PerfectGems.lastGain < MINING_MS || holdingTool()
        }
    }

    /** A pickaxe, drill or gauntlet, by its SkyBlock item id. */
    private fun holdingTool(): Boolean {
        val stack = Minecraft.getInstance().player?.mainHandItem ?: return false
        val id = stack.get(DataComponents.CUSTOM_DATA)?.copyTag()?.getStringOr("id", "").orEmpty()
        return tools.containsMatchIn(id)
    }
}
