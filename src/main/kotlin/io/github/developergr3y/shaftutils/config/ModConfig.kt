package io.github.developergr3y.shaftutils.config

import com.google.gson.annotations.Expose
import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.hud.HudPosition
import io.github.developergr3y.shaftutils.profit.KeyPriceType
import io.github.developergr3y.shaftutils.profit.PriceType
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorInfoText
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import io.github.notenoughupdates.moulconfig.common.text.StructuredText

/**
 * Everything shown in /shaftutils. MoulConfig builds the screen from these annotations:
 * @Category = a tab on the left, @ConfigOption + @ConfigEditor* = one setting.
 */
class ModConfig : Config() {
    override fun getTitle(): StructuredText = StructuredText.of("ShaftUtils ${ShaftUtils.version} §7by §bGr3y_VVolf")

    override fun saveNow() = ShaftUtils.saveConfig()

    @Expose
    @JvmField
    @Category(name = "GUI", desc = "Move and resize the panels (§e/shaftutils gui§7).")
    var gui = GuiConfig()

    @Expose
    @JvmField
    @Category(name = "Corpse Finder", desc = "Spawn spots that clear as you check them, and corpse waypoints once seen.")
    var corpses = CorpseConfig()

    @Expose
    @JvmField
    @Category(name = "Routes", desc = "Your own ordered mining routes, loaded per shaft type when you arrive.")
    var routes = RoutesConfig()

    @Expose
    @JvmField
    @Category(name = "Profit", desc = "What each shaft made: mining, corpse loot, minus the keys you used.")
    var profit = ProfitConfig()

    @Expose
    @JvmField
    @Category(name = "Debug", desc = "Recording new corpse spots, and exporting them to share.")
    var debug = DebugConfig()
}

class GuiConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Panel Background", desc = "Draw a dark background behind the Corpse Helper and Shaft Profit panels.")
    @ConfigEditorBoolean
    var panelBackground = true

    @Transient
    @JvmField
    @ConfigOption(name = "Edit GUI Locations", desc = "Drag the panels to move them, scroll to resize. Also: §e/shaftutils gui")
    @ConfigEditorButton(buttonText = "Edit")
    val edit = Runnable { ShaftUtils.openHudEditor() }

    @Transient
    @JvmField
    @ConfigOption(name = "Reset GUI Locations", desc = "Put the panels back to their default positions and sizes.")
    @ConfigEditorButton(buttonText = "Reset")
    val reset = Runnable { ShaftUtils.resetHud() }
}

class CorpseConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Enabled", desc = "Turn the corpse finder on in Glacite Mineshafts.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Show Spawn Spots", desc = "Waypoints at every spot a corpse can spawn. They clear once checked.")
    @ConfigEditorBoolean
    var showSpots = true

    @Expose
    @JvmField
    @ConfigOption(name = "Show Corpses", desc = "Waypoint on each corpse you've seen, with whether you have its key.")
    @ConfigEditorBoolean
    var showCorpses = true

    @Expose
    @JvmField
    @ConfigOption(name = "Hide Looted", desc = "Remove a corpse's waypoint once you've looted it.")
    @ConfigEditorBoolean
    var hideLooted = true

    @Expose
    @JvmField
    @ConfigOption(name = "Chat on Find", desc = "Post a chat message when you spot a corpse.")
    @ConfigEditorBoolean
    var announce = true

    @Expose
    @JvmField
    @ConfigOption(name = "Use Organ Donor", desc = "Use the talisman's ding: clear spots when silent, point to the corpse.")
    @ConfigEditorBoolean
    var useOrganDonor = true

    @Expose
    @JvmField
    @ConfigOption(name = "Show Estimate", desc = "Waypoint where the dings put the corpse when no known spot fits.")
    @ConfigEditorBoolean
    var showEstimate = true

    @Expose
    @JvmField
    @ConfigOption(name = "Corpse Helper", desc = "Panel listing the shaft's corpses, whether they're looted, and the Organ Donor.")
    @ConfigEditorBoolean
    var showStatus = true

    @Expose
    @JvmField
    @ConfigOption(name = "Hide When All Looted", desc = "Hide the Corpse Helper once every corpse in the shaft is looted.")
    @ConfigEditorBoolean
    var hideWhenLooted = true

    // Not shown as an option; set in GUI > Edit GUI Locations.
    @Expose @JvmField var statusPosition = HudPosition(x = 5, y = 60)
}

class RoutesConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Enabled", desc = "Load a route for the shaft you enter. Add your own with §e/shaftutils route§7.")
    @ConfigEditorBoolean
    var enabled = true

    @Transient
    @JvmField
    @ConfigOption(name = "Built-in routes by Mining Cult", desc = "The routes that come with ShaftUtils are made by the Mining Cult community. Thank you!")
    @ConfigEditorInfoText(infoTitle = "Credits")
    var credit = false

    @Expose
    @JvmField
    @ConfigOption(name = "Use Built-in Routes", desc = "Use Mining Cult's routes when you haven't added your own for a shaft.")
    @ConfigEditorBoolean
    var useBuiltIn = true

    @Expose
    @JvmField
    @ConfigOption(name = "Next Point Key", desc = "Skip to the next point on the route.")
    @ConfigEditorKeybind(defaultKey = -1)
    var nextKey = -1

    @Expose
    @JvmField
    @ConfigOption(name = "Previous Point Key", desc = "Go back to the previous point on the route.")
    @ConfigEditorKeybind(defaultKey = -1)
    var backKey = -1

    @Expose
    @JvmField
    @ConfigOption(name = "Advance Distance", desc = "Move to the next point once you're this many blocks from the current one.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 8f, minStep = 0.5f)
    var advanceDistance = 3.0f

    @Expose
    @JvmField
    @ConfigOption(name = "Points Ahead", desc = "How many upcoming points to show after the current one.")
    @ConfigEditorSlider(minValue = 0f, maxValue = 5f, minStep = 1f)
    var pointsAhead = 2

    @Expose
    @JvmField
    @ConfigOption(name = "Line to Point", desc = "Draw a line from your crosshair to the current point.")
    @ConfigEditorBoolean
    var showTracer = true

    @Expose
    @JvmField
    @ConfigOption(name = "Path Line", desc = "Join the current and upcoming points with a line.")
    @ConfigEditorBoolean
    var showPath = true

    @Transient
    @JvmField
    @ConfigOption(name = "Open Routes Folder", desc = "One file per shaft code, e.g. TOPA_1.json. CRYSTAL.json covers crystal shafts.")
    @ConfigEditorButton(buttonText = "Open")
    val openFolder = Runnable { ShaftUtils.openRoutesFolder() }
}

class ProfitConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Enabled", desc = "Track what each shaft makes: mining, corpse loot, minus keys used.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Chat Summary", desc = "Post the shaft's profit in chat when you leave it.")
    @ConfigEditorBoolean
    var chatSummary = true

    @Expose
    @JvmField
    @ConfigOption(name = "Show Breakdown", desc = "Add corpse and mining lines under the total.")
    @ConfigEditorBoolean
    var breakdown = true

    @Expose
    @JvmField
    @ConfigOption(name = "Share Buttons", desc = "Add [Copy] [Party] [Guild] buttons. They only fill your chat box.")
    @ConfigEditorBoolean
    var shareButtons = true

    @Expose
    @JvmField
    @ConfigOption(name = "Profit Panel", desc = "Panel with this shaft's profit so far: total, top items, corpses.")
    @ConfigEditorBoolean
    var showPanel = true

    @Expose
    @JvmField
    @ConfigOption(name = "Items Shown", desc = "How many of the most valuable items the panel lists (the rest are \"N more\").")
    @ConfigEditorSlider(minValue = 0f, maxValue = 10f, minStep = 1f)
    var itemsShown = 5

    @Expose
    @JvmField
    @ConfigOption(name = "Keep After Leaving", desc = "Minutes to keep showing the last shaft's profit after you leave.")
    @ConfigEditorSlider(minValue = 0f, maxValue = 30f, minStep = 1f)
    var keepMinutes = 5

    @Expose
    @JvmField
    @ConfigOption(name = "Item Prices", desc = "Insta-buy or insta-sell bazaar prices. Also: click it on the panel.")
    @ConfigEditorDropdown
    var priceType = PriceType.SELL_OFFER

    @Expose
    @JvmField
    @ConfigOption(name = "Key Prices", desc = "Cost keys at the bazaar instant buy or buy order price.")
    @ConfigEditorDropdown
    var keyPriceType = KeyPriceType.INSTANT_BUY

    // Not shown as options: the panel's Shaft / Session switch, and its position (GUI > Edit GUI Locations).
    @Expose @JvmField var sessionView = false
    @Expose @JvmField var panelPosition = HudPosition(x = 5, y = 150)
}

class DebugConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Record New Spots", desc = "Save corpses seen away from known spots as new spawn spots.")
    @ConfigEditorBoolean
    var recordSpots = true

    @Transient
    @JvmField
    @ConfigOption(name = "Export Spawn Spots", desc = "Write known + learned spots to one file. Also: §e/shaftutils export")
    @ConfigEditorButton(buttonText = "Export")
    val export = Runnable { ShaftUtils.exportSpots() }
}
