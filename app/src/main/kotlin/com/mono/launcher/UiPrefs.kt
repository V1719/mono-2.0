package com.mono.launcher

import android.content.Context
import android.content.SharedPreferences
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Every customisation knob lives here. The home screen re-reads these each time it
 * comes to the front, so changes made in Customize show up as soon as you go back.
 */
class UiPrefs(context: Context) {
    private val p: SharedPreferences = context.getSharedPreferences("mono_ui", Context.MODE_PRIVATE)

    private fun intPref(key: String, def: Int): ReadWriteProperty<Any?, Int> =
        object : ReadWriteProperty<Any?, Int> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Int = p.getInt(key, def)
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
                p.edit().putInt(key, value).apply()
            }
        }

    private fun boolPref(key: String, def: Boolean): ReadWriteProperty<Any?, Boolean> =
        object : ReadWriteProperty<Any?, Boolean> {
            override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = p.getBoolean(key, def)
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
                p.edit().putBoolean(key, value).apply()
            }
        }

    // ---- Look ----
    var useWallpaper by boolPref("use_wallpaper", true)
    var bgColor by intPref("bg_color", 0xFF131314.toInt())
    var scrimPercent by intPref("scrim_percent", 45)
    var scrimLight by boolPref("scrim_light", false)
    var textColor by intPref("text_color", 0xFFFFFFFF.toInt())
    var accentFromWallpaper by boolPref("accent_wallpaper", true)
    var accentColor by intPref("accent_color", DEFAULT_ACCENT)
    var font by intPref("font", 0)

    // ---- Home ----
    var favSizeSp by intPref("fav_size", 30)
    var rowGapDp by intPref("row_gap", 10)
    var favAlign by intPref("fav_align", 0)          // 0 left, 1 centre, 2 right
    var favPosition by intPref("fav_position", 0)    // 0 bottom, 1 centre, 2 top
    var showClock by boolPref("show_clock", true)
    var clockSizeSp by intPref("clock_size", 48)
    var clockFormat by intPref("clock_format", 0)    // 0 system, 1 24h, 2 12h
    var showDate by boolPref("show_date", true)
    var notifPreviews by boolPref("notif_previews", true)
    var showMedia by boolPref("show_media", true)
    var showCalendar by boolPref("show_calendar", true)
    var showAllAppsButton by boolPref("show_all_apps_button", true)

    // ---- App list + scrolling ----
    var listSizeSp by intPref("list_size", 22)
    var indexLeft by boolPref("index_left", false)
    var wavePercent by intPref("wave_percent", 100)
    var flingPercent by intPref("fling_percent", 100)
    var haptics by boolPref("haptics", true)

    fun applyPreset(index: Int) {
        val preset = PRESETS[index]
        useWallpaper = preset.wallpaper
        scrimLight = preset.scrimLight
        scrimPercent = preset.scrim
        bgColor = preset.bg
        textColor = preset.text
    }

    fun resetAll() {
        p.edit().clear().apply()
    }

    class Preset(
        val name: String,
        val wallpaper: Boolean,
        val scrimLight: Boolean,
        val scrim: Int,
        val bg: Int,
        val text: Int
    )

    companion object {
        val PRESETS = listOf(
            Preset("Wallpaper - dark", true, false, 45, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
            Preset("Wallpaper - light", true, true, 40, 0xFFFFFFFF.toInt(), 0xFF111111.toInt()),
            Preset("AMOLED black", false, false, 0, 0xFF000000.toInt(), 0xFFFFFFFF.toInt()),
            Preset("Dark", false, false, 0, 0xFF131314.toInt(), 0xFFF0EFEC.toInt()),
            Preset("Light", false, true, 0, 0xFFF7F6F3.toInt(), 0xFF1C1C1C.toInt()),
            Preset("Midnight", false, false, 0, 0xFF0B1220.toInt(), 0xFFE6EDF7.toInt()),
            Preset("Forest", false, false, 0, 0xFF0E1A14.toInt(), 0xFFE3F1E8.toInt()),
            Preset("Sepia", false, false, 0, 0xFF2B2118.toInt(), 0xFFF1E4D0.toInt())
        )
    }
}
