package io.github.developergr3y.shaftutils.config

import com.google.gson.annotations.Expose
import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.developergr3y.shaftutils.hud.HudPosition
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Accordion
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
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
    @Category(name = "GUI", desc = "Move and resize the status panel (§e/shaftutils gui§7).")
    var gui = GuiConfig()

    @Expose
    @JvmField
    @Category(name = "Corpse Finder", desc = "Spawn spots that clear as you check them, and corpse waypoints once seen.")
    var corpses = CorpseConfig()

    @Expose
    @JvmField
    @Category(name = "Shaft Title", desc = "A title when you enter a shaft: its type and what corpses it has.")
    var title = TitleConfig()

    @Expose
    @JvmField
    @Category(name = "Routes", desc = "Your own ordered mining routes, loaded per shaft type when you arrive.")
    var routes = RoutesConfig()

    @Expose
    @JvmField
    @Category(name = "Debug", desc = "Data gathering while we work out how corpses and the ding behave.")
    var debug = DebugConfig()
}

class GuiConfig {
    @Transient
    @JvmField
    @ConfigOption(name = "Edit GUI Locations", desc = "Drag the status panel to move it, scroll to resize. Also: §e/shaftutils gui")
    @ConfigEditorButton(buttonText = "Edit")
    val edit = Runnable { ShaftUtils.openHudEditor() }

    @Transient
    @JvmField
    @ConfigOption(name = "Reset GUI Locations", desc = "Put the status panel back to its default position and size.")
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
    @ConfigOption(name = "Status Panel", desc = "Show the shaft, its corpses, spots left and the Organ Donor ding.")
    @ConfigEditorBoolean
    var showStatus = true

    // Not shown as an option; set in GUI > Edit GUI Locations.
    @Expose @JvmField var statusPosition = HudPosition()
}

class TitleConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Enabled", desc = "Show the shaft type and its corpses on screen when you enter a shaft.")
    @ConfigEditorBoolean
    var enabled = true

    @Expose
    @JvmField
    @ConfigOption(name = "Sounds", desc = "Play a sound with it (a fanfare for Jasper and Vanguard shafts).")
    @ConfigEditorBoolean
    var sounds = true

    @Expose
    @JvmField
    @ConfigOption(name = "Screen Border", desc = "A glow around the screen edges in the gem's colour (gold pulse for special shafts).")
    @ConfigEditorBoolean
    var border = true

    @Expose
    @JvmField
    @ConfigOption(name = "Title Time", desc = "Seconds the title stays on screen.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 10f, minStep = 0.5f)
    var seconds = 2.5f

    @Expose
    @JvmField
    @ConfigOption(name = "Special Title Time", desc = "Seconds the title stays for Jasper and Vanguard shafts.")
    @ConfigEditorSlider(minValue = 1f, maxValue = 15f, minStep = 0.5f)
    var specialSeconds = 4.5f

    @Expose
    @JvmField
    @ConfigOption(name = "Shafts", desc = "Which shafts get a title. Each gem covers its shaft and its crystal shaft.")
    @Accordion
    var shafts = TitleShafts()
}

/** Which shaft types show an entry title (a gem covers its regular and crystal shafts). */
class TitleShafts {
    @Transient
    @JvmField
    @ConfigOption(name = "Only Jasper & Vanguard", desc = "Turn every shaft off except Jasper and Vanguard.")
    @ConfigEditorButton(buttonText = "Set")
    val onlySpecial = Runnable { setAll(false); jasper = true; vanguard = true; ShaftUtils.saveConfig() }

    @Transient
    @JvmField
    @ConfigOption(name = "All Shafts", desc = "Turn every shaft on.")
    @ConfigEditorButton(buttonText = "Set")
    val all = Runnable { setAll(true); ShaftUtils.saveConfig() }

    @Expose @JvmField @ConfigOption(name = "Jasper", desc = "Jasper and Jasper Crystal shafts.") @ConfigEditorBoolean var jasper = true
    @Expose @JvmField @ConfigOption(name = "Vanguard", desc = "Vanguard (Fairy) shafts.") @ConfigEditorBoolean var vanguard = true
    @Expose @JvmField @ConfigOption(name = "Opal", desc = "Opal and Opal Crystal shafts.") @ConfigEditorBoolean var opal = true
    @Expose @JvmField @ConfigOption(name = "Ruby", desc = "Ruby and Ruby Crystal shafts.") @ConfigEditorBoolean var ruby = true
    @Expose @JvmField @ConfigOption(name = "Onyx", desc = "Onyx and Onyx Crystal shafts.") @ConfigEditorBoolean var onyx = true
    @Expose @JvmField @ConfigOption(name = "Aquamarine", desc = "Aquamarine and Aquamarine Crystal shafts.") @ConfigEditorBoolean var aquamarine = true
    @Expose @JvmField @ConfigOption(name = "Citrine", desc = "Citrine and Citrine Crystal shafts.") @ConfigEditorBoolean var citrine = true
    @Expose @JvmField @ConfigOption(name = "Peridot", desc = "Peridot and Peridot Crystal shafts.") @ConfigEditorBoolean var peridot = true
    @Expose @JvmField @ConfigOption(name = "Topaz", desc = "Topaz shafts.") @ConfigEditorBoolean var topaz = true
    @Expose @JvmField @ConfigOption(name = "Sapphire", desc = "Sapphire shafts.") @ConfigEditorBoolean var sapphire = true
    @Expose @JvmField @ConfigOption(name = "Amethyst", desc = "Amethyst shafts.") @ConfigEditorBoolean var amethyst = true
    @Expose @JvmField @ConfigOption(name = "Amber", desc = "Amber shafts.") @ConfigEditorBoolean var amber = true
    @Expose @JvmField @ConfigOption(name = "Jade", desc = "Jade shafts.") @ConfigEditorBoolean var jade = true
    @Expose @JvmField @ConfigOption(name = "Titanium", desc = "Titanium shafts.") @ConfigEditorBoolean var titanium = true
    @Expose @JvmField @ConfigOption(name = "Umber", desc = "Umber shafts.") @ConfigEditorBoolean var umber = true
    @Expose @JvmField @ConfigOption(name = "Tungsten", desc = "Tungsten shafts.") @ConfigEditorBoolean var tungsten = true
    @Expose @JvmField @ConfigOption(name = "Littlefoot's Den", desc = "Littlefoot's Den.") @ConfigEditorBoolean var littlefoot = true

    /** Whether a shaft type code ("JASP", "OPAL", ...) shows a title. Unknown types do. */
    fun shows(type: String): Boolean = when (type) {
        "JASP" -> jasper
        "FAIR" -> vanguard
        "OPAL" -> opal
        "RUBY" -> ruby
        "ONYX" -> onyx
        "AQUA" -> aquamarine
        "CITR" -> citrine
        "PERI" -> peridot
        "TOPA" -> topaz
        "SAPP" -> sapphire
        "AMET" -> amethyst
        "AMBE" -> amber
        "JADE" -> jade
        "TITA" -> titanium
        "UMBE" -> umber
        "TUNG" -> tungsten
        "LITT" -> littlefoot
        else -> true
    }

    private fun setAll(on: Boolean) {
        jasper = on; vanguard = on; opal = on; ruby = on; onyx = on; aquamarine = on; citrine = on; peridot = on
        topaz = on; sapphire = on; amethyst = on; amber = on; jade = on; titanium = on; umber = on; tungsten = on
        littlefoot = on
    }
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

class DebugConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Probe Logging", desc = "Log dings, corpse sightings and shaft info to config/shaftutils/probe.")
    @ConfigEditorBoolean
    var probe = false

    @Expose
    @JvmField
    @ConfigOption(name = "Record New Spots", desc = "Save corpses seen away from known spots as new spawn spots.")
    @ConfigEditorBoolean
    var recordSpots = true

    @Expose
    @JvmField
    @ConfigOption(name = "Log All Sounds", desc = "Also log every other sound in a shaft (big logs; for research).")
    @ConfigEditorBoolean
    var logAllSounds = false

    @Transient
    @JvmField
    @ConfigOption(name = "Export Spawn Spots", desc = "Write known + learned spots to one file. Also: §e/shaftutils export")
    @ConfigEditorButton(buttonText = "Export")
    val export = Runnable { ShaftUtils.exportSpots() }
}
