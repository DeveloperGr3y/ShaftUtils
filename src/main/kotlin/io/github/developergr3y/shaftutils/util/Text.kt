package io.github.developergr3y.shaftutils.util

private val formatting = Regex("§.")

/** "§6Lapis§r: §cNOT LOOTED" -> "Lapis: NOT LOOTED". */
fun String.stripFormatting() = replace(formatting, "")
