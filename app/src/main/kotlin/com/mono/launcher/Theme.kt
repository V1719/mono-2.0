package com.mono.launcher

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.View

/** Used until a wallpaper colour or a custom accent is available. */
const val DEFAULT_ACCENT: Int = 0xFF8AA0C4.toInt()

fun Context.dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()
fun View.dp(value: Int): Int = context.dp(value)

object Fonts {
    val names = listOf("Light", "Regular", "Medium", "Condensed", "Thin", "Serif", "Monospace", "Casual")
    private val families = listOf(
        "sans-serif-light", "sans-serif", "sans-serif-medium", "sans-serif-condensed",
        "sans-serif-thin", "serif", "monospace", "casual"
    )
    private val cache = HashMap<Int, Typeface>()

    fun typeface(index: Int): Typeface = cache.getOrPut(index) {
        Typeface.create(families.getOrElse(index) { "sans-serif" }, Typeface.NORMAL)
    }
}

/** Every colour the home screen uses, resolved from the user's settings. */
data class UiTheme(
    val windowBackground: Int,
    val sheetBackground: Int,
    val text: Int,
    val textSecondary: Int,
    val textTertiary: Int,
    val surface: Int,
    val accent: Int,
    val lightBackground: Boolean
) {
    companion object {
        fun resolve(ui: UiPrefs, wallpaperAccent: Int?): UiTheme {
            val text = ui.textColor
            val scrimBase = if (ui.scrimLight) Color.WHITE else Color.BLACK
            val scrimAlpha = ui.scrimPercent.coerceIn(0, 100) * 255 / 100

            val window: Int
            val sheet: Int
            val light: Boolean
            if (ui.useWallpaper) {
                window = withAlpha(scrimBase, scrimAlpha)
                sheet = withAlpha(scrimBase, 55)
                light = ui.scrimLight
            } else {
                window = ui.bgColor or 0xFF000000.toInt()
                sheet = window
                light = isLight(window)
            }

            val accent = if (ui.accentFromWallpaper) {
                tune(wallpaperAccent ?: DEFAULT_ACCENT, light)
            } else {
                ui.accentColor
            }
            return UiTheme(
                windowBackground = window,
                sheetBackground = sheet,
                text = text,
                textSecondary = withAlpha(text, 178),
                textTertiary = withAlpha(text, 125),
                surface = withAlpha(text, 30),
                accent = accent,
                lightBackground = light
            )
        }

        fun withAlpha(color: Int, alpha: Int): Int =
            Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

        private fun isLight(c: Int): Boolean =
            (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) > 150

        /** Keeps a wallpaper-derived accent readable on whatever background it lands on. */
        private fun tune(color: Int, lightBackground: Boolean): Int {
            val hsv = FloatArray(3)
            Color.colorToHSV(color, hsv)
            if (hsv[1] < 0.12f) {
                return if (lightBackground) 0xFF333333.toInt() else 0xFFDDDDDD.toInt()
            }
            hsv[1] = hsv[1].coerceIn(0.3f, 0.8f)
            hsv[2] = if (lightBackground) hsv[2].coerceAtMost(0.55f) else hsv[2].coerceAtLeast(0.8f)
            return Color.HSVToColor(hsv)
        }
    }
}
