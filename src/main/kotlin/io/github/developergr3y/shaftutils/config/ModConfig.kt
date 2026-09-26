package io.github.developergr3y.shaftutils.config

import com.google.gson.annotations.Expose
import io.github.developergr3y.shaftutils.ShaftUtils
import io.github.notenoughupdates.moulconfig.Config
import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
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
    @Category(name = "Corpse Finder", desc = "Spawn spots that clear as you check them, and corpse waypoints once seen.")
    var corpses = CorpseConfig()

    @Expose
    @JvmField
    @Category(name = "Debug", desc = "Data gathering while we work out how corpses and the ding behave.")
    var debug = DebugConfig()
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

    @Expose
    @JvmField
    @ConfigOption(name = "Panel X", desc = "Status panel position from the left.")
    @ConfigEditorSlider(minValue = 0f, maxValue = 600f, minStep = 5f)
    var statusX = 5

    @Expose
    @JvmField
    @ConfigOption(name = "Panel Y", desc = "Status panel position from the top.")
    @ConfigEditorSlider(minValue = 0f, maxValue = 400f, minStep = 5f)
    var statusY = 60
}

class DebugConfig {
    @Expose
    @JvmField
    @ConfigOption(name = "Probe Logging", desc = "Log dings, corpse sightings and shaft info to config/shaftutils/probe.")
    @ConfigEditorBoolean
    var probe = true

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
