package io.github.developergr3y.shaftutils.hud

import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.shaft.Mineshaft
import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.screens.Screen
import net.minecraft.client.input.KeyEvent
import net.minecraft.client.input.MouseButtonEvent
import net.minecraft.network.chat.Component
import java.util.Locale

private const val GLFW_KEY_R = 82

/** Drag the status panel to move it, scroll over it to resize, R to reset. Saved as you go. */
class HudEditScreen : Screen(Component.literal("Move ShaftUtils displays")) {
    private var dragging = false
    private var grabX = 0.0
    private var grabY = 0.0

    private val position get() = ShaftUtils.config.corpses.statusPosition

    override fun extractRenderState(graphics: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractRenderState(graphics, mouseX, mouseY, delta)
        StatusHud.draw(graphics, if (Mineshaft.inShaft) StatusHud.lines() else StatusHud.previewLines())
        val hovered = StatusHud.contains(mouseX.toDouble(), mouseY.toDouble())
        val w = (StatusHud.width * position.scale).toInt()
        val h = (StatusHud.height * position.scale).toInt()
        graphics.outline(position.x - 1, position.y - 1, w + 2, h + 2, if (hovered || dragging) -1 else 0xFF888888.toInt())
        if (hovered) {
            val scale = String.format(Locale.ROOT, "%.1f", position.scale)
            graphics.text(font, "Status panel (x$scale)", position.x, position.y + h + 3, 0xFFAAAAAA.toInt(), true)
        }
        graphics.centeredText(font, "Drag to move · Scroll to resize · R to reset · Esc to save", width / 2, 10, -1)
    }

    override fun mouseClicked(event: MouseButtonEvent, doubleClick: Boolean): Boolean {
        if (event.button() == 0 && StatusHud.contains(event.x(), event.y())) {
            dragging = true
            grabX = event.x() - position.x
            grabY = event.y() - position.y
            return true
        }
        return super.mouseClicked(event, doubleClick)
    }

    override fun mouseDragged(event: MouseButtonEvent, dragX: Double, dragY: Double): Boolean {
        if (!dragging) return super.mouseDragged(event, dragX, dragY)
        val maxX = width - (StatusHud.width * position.scale).toInt()
        val maxY = height - (StatusHud.height * position.scale).toInt()
        position.x = (event.x() - grabX).toInt().coerceIn(0, maxOf(0, maxX))
        position.y = (event.y() - grabY).toInt().coerceIn(0, maxOf(0, maxY))
        return true
    }

    override fun mouseReleased(event: MouseButtonEvent): Boolean {
        if (dragging) ShaftUtils.saveConfig()
        dragging = false
        return super.mouseReleased(event)
    }

    override fun mouseScrolled(mouseX: Double, mouseY: Double, scrollX: Double, scrollY: Double): Boolean {
        if (!StatusHud.contains(mouseX, mouseY)) return false
        position.scale = (position.scale + scrollY.toFloat() * 0.1f).coerceIn(0.5f, 3.0f)
        ShaftUtils.saveConfig()
        return true
    }

    override fun keyPressed(event: KeyEvent): Boolean {
        if (event.key() == GLFW_KEY_R) {
            ShaftUtils.resetHud()
            return true
        }
        return super.keyPressed(event)
    }

    override fun isPauseScreen() = false

    override fun removed() {
        ShaftUtils.saveConfig()
        super.removed()
    }
}
