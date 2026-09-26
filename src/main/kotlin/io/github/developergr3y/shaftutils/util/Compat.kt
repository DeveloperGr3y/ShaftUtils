package io.github.developergr3y.shaftutils.util

import net.minecraft.client.Camera
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.components.ChatComponent
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * The only place that knows about Minecraft API differences between versions.
 * The `//?` comments are Stonecutter switches: when building 26.1 it swaps which branch is commented out.
 */
object Compat {
    private val mc get() = Minecraft.getInstance()

    //? if >=26.2 {
    val screen: Screen? get() = mc.gui.screen()
    fun setScreen(screen: Screen?) = mc.gui.setScreen(screen)
    val chat: ChatComponent get() = mc.gui.hud.chat
    val camera: Camera get() = mc.gameRenderer.mainCamera()
    fun title(title: Component, subtitle: Component, fadeIn: Int, stay: Int, fadeOut: Int) = mc.gui.hud.run {
        setTimes(fadeIn, stay, fadeOut)
        setSubtitle(subtitle)
        setTitle(title)
    }
    fun subtitle(subtitle: Component) = mc.gui.hud.setSubtitle(subtitle)
    //?} else {
    /*val screen: Screen? get() = mc.screen
    fun setScreen(screen: Screen?) = mc.setScreen(screen)
    val chat: ChatComponent get() = mc.gui.chat
    val camera: Camera get() = mc.gameRenderer.getMainCamera()
    fun title(title: Component, subtitle: Component, fadeIn: Int, stay: Int, fadeOut: Int) = mc.gui.run {
        setTimes(fadeIn, stay, fadeOut)
        setSubtitle(subtitle)
        setTitle(title)
    }
    fun subtitle(subtitle: Component) = mc.gui.setSubtitle(subtitle)
    *///?}
}
