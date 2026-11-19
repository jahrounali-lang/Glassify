package com.liquidclock.model

import kotlinx.serialization.Serializable
import java.util.UUID

/** One saved clock design. All 0..100 values are percentages; defaults follow the spec. */
@Serializable
data class ClockConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Default",
    // Liquid glass
    val bezel: Float = 25f, val refraction: Float = 80f, val blur: Float = 0f, val frost: Float = 0f,
    val radius: Float = 95f, val opacity: Float = 100f, val edge: Float = 60f, val reflection: Float = 50f,
    val thickness: Float = 50f, val highlight: Float = 38f, val shadow: Float = 30f,
    // Typography
    val fontSource: String = "pixel", val fontWeight: Float = 75f, val fontSize: Float = 100f,
    val letterSpacing: Float = 33f, val lineHeight: Float = 50f, val digitSpacing: Float = 0f,
    // Layout (percent of screen)
    val width: Float = 70f, val height: Float = 34f, val posX: Float = 50f, val posY: Float = 30f,
    val rotation: Float = 50f, val orientation: Int = 0, val hAlign: Int = 1, val vAlign: Int = 1,
    // Time & date
    val hour12: Boolean = false, val seconds: Boolean = false, val showDate: Boolean = true,
    val dateFormat: String = "EEE, d MMM", val language: String = "", val leadingZero: Boolean = true,
    val wallpaper: String = "aurora",
) {
    fun rotationDeg() = (rotation - 50f) * 0.9f
    fun sizeMul() = 0.2f + fontSize / 100f * 0.8f
    fun letterEm() = -0.1f + letterSpacing / 100f * 0.3f
    fun digitEm() = digitSpacing / 100f * 0.2f
    fun lineMul() = 0.85f + lineHeight / 100f * 0.6f
    fun weight() = (100 + fontWeight * 8f).toInt().coerceIn(100, 900)
}
