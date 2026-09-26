package io.github.developergr3y.shaftutils.hud

import com.google.gson.annotations.Expose
import io.github.developergr3y.shaftutils.ShaftUtils
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiGraphicsExtractor

/** Where a panel sits (GUI pixels) and how big it is. Set in /shaftutils gui. */
class HudPosition(
    @Expose @JvmField var x: Int = 5,
    @Expose @JvmField var y: Int = 60,
    @Expose @JvmField var scale: Float = 1f,
)

/** One line of a panel. Lines with [onClick] are buttons while your inventory is open. */
class PanelLine(val text: String, val onClick: (() -> Unit)? = null)

/**
 * An on-screen panel: a small box of text lines at a saved position and scale. Subclasses say what to show;
 * this draws it, and handles hovering and clicking its buttons while your inventory is open.
 */
abstract class Panel(val label: String) {
    abstract var position: HudPosition
    abstract fun defaultPosition(): HudPosition

    /** Should it show right now (on the HUD, and over your inventory)? */
    abstract fun visible(): Boolean
    abstract fun lines(): List<PanelLine>

    /** Example lines for the editor when there's nothing real to show. */
    abstract fun previewLines(): List<PanelLine>

    /** Size as last drawn, unscaled. */
    var width = 0
        private set
    var height = 0
        private set
    private var drawn: List<PanelLine> = emptyList()

    fun draw(graphics: GuiGraphicsExtractor, lines: List<PanelLine>, mouseX: Double = -1.0, mouseY: Double = -1.0, interactive: Boolean = false) {
        if (lines.isEmpty()) return
        val font = Minecraft.getInstance().font
        drawn = lines
        width = lines.maxOf { font.width(it.text) } + PADDING * 2
        height = lines.size * LINE_HEIGHT + PADDING * 2 - 1
        val hovered = if (interactive) lineAt(mouseX, mouseY) else -1
        val pose = graphics.pose()
        pose.pushMatrix()
        pose.translate(position.x.toFloat(), position.y.toFloat())
        pose.scale(position.scale)
        if (ShaftUtils.config.gui.panelBackground) graphics.fill(0, 0, width, height, 0x80000000.toInt())
        lines.forEachIndexed { i, line ->
            val y = PADDING + i * LINE_HEIGHT
            if (i == hovered && line.onClick != null) graphics.fill(PADDING - 1, y - 1, width - PADDING + 1, y + LINE_HEIGHT - 1, 0x40FFFFFF)
            graphics.text(font, line.text, PADDING, y, -1, true)
        }
        pose.popMatrix()
    }

    fun contains(mouseX: Double, mouseY: Double) =
        mouseX >= position.x && mouseY >= position.y &&
            mouseX <= position.x + width * position.scale && mouseY <= position.y + height * position.scale

    private fun lineAt(mouseX: Double, mouseY: Double): Int {
        if (!contains(mouseX, mouseY)) return -1
        val localY = (mouseY - position.y) / position.scale - PADDING
        val index = (localY / LINE_HEIGHT).toInt()
        return if (localY >= 0 && index in drawn.indices) index else -1
    }

    /** Runs the clicked line's action. True if a button was clicked. */
    fun click(mouseX: Double, mouseY: Double): Boolean {
        val action = drawn.getOrNull(lineAt(mouseX, mouseY))?.onClick ?: return false
        action()
        return true
    }

    companion object {
        const val PADDING = 3
        const val LINE_HEIGHT = 10
    }
}
