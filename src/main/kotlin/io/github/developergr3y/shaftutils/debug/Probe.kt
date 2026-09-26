package io.github.developergr3y.shaftutils.debug

import com.google.gson.Gson
import io.github.developergr3y.shaftutils.ShaftUtils
import java.io.File
import java.time.LocalDate

/**
 * Research log for working out how corpses and the Organ Donor ding behave. One JSON object per line in
 * config/shaftutils/probe/<date>.jsonl, e.g.
 *   {"t":1727350000000,"event":"sound","id":"minecraft:block.note_block.harp","pitch":1.2,...}
 * Only written while Debug > Probe Logging is on. Nothing here is shown in game or sent anywhere.
 */
object Probe {
    private val gson = Gson()
    private val dir = File(ShaftUtils.configDir, "probe")

    val enabled get() = ShaftUtils.config.debug.probe

    fun log(event: String, vararg fields: Pair<String, Any?>) {
        if (!enabled) return
        val entry = linkedMapOf<String, Any?>("t" to System.currentTimeMillis(), "event" to event)
        fields.forEach { (k, v) -> entry[k] = if (v is Double) round(v) else v }
        try {
            dir.mkdirs()
            File(dir, "${LocalDate.now()}.jsonl").appendText(gson.toJson(entry) + "\n")
        } catch (e: Exception) {
            ShaftUtils.logger.warn("Probe log failed", e)
        }
    }

    private fun round(value: Double) = Math.round(value * 100) / 100.0

    val folder: File get() = dir
}
