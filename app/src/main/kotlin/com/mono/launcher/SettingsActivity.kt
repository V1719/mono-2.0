package com.mono.launcher

import android.Manifest
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat

/**
 * Everything about how Mono looks and behaves. Changes are saved the moment you make
 * them; go back to the home screen to see them applied.
 */
class SettingsActivity : AppCompatActivity() {

    private lateinit var ui: UiPrefs
    private lateinit var content: LinearLayout
    private var fg = 0

    private val calendarPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = UiPrefs(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        fg = if (night) 0xFFEDEDED.toInt() else 0xFF1B1B1B.toInt()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(20), dp(16), dp(20), dp(40))
        val scroll = ScrollView(this)
        scroll.addView(content)
        setContentView(scroll)

        ViewCompat.setOnApplyWindowInsetsListener(scroll) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        build()
    }

    private fun rebuild() {
        content.removeAllViews()
        build()
    }

    private fun build() {
        addTitle("Customize")
        addNote("Changes save instantly. Go back to your home screen to see them.")

        addSection("Theme")
        addAction("Preset", "Start from a ready-made look, then adjust below") { presetDialog() }
        addChoice("Background", listOf("Wallpaper", "Solid colour"), { if (ui.useWallpaper) 0 else 1 }) {
            ui.useWallpaper = it == 0
        }
        addColor("Solid background colour", { ui.bgColor }) { ui.bgColor = it }
        addSlider("Wallpaper dimming", 0, 95, "%", { ui.scrimPercent }) { ui.scrimPercent = it }
        addToggle("Light dimming", "Dim the wallpaper with white instead of black (use with dark text)",
            { ui.scrimLight }) { ui.scrimLight = it }
        addColor("Text colour", { ui.textColor }) { ui.textColor = it }
        addAccent()
        addChoice("Font", Fonts.names, { ui.font }) { ui.font = it }

        addSection("Home screen")
        addChoice("Favourites position", listOf("Bottom", "Middle", "Top"), { ui.favPosition }) { ui.favPosition = it }
        addChoice("Favourites alignment", listOf("Left", "Centre", "Right"), { ui.favAlign }) { ui.favAlign = it }
        addSlider("Favourites text size", 18, 48, "sp", { ui.favSizeSp }) { ui.favSizeSp = it }
        addSlider("Row spacing", 0, 28, "dp", { ui.rowGapDp }) { ui.rowGapDp = it }
        addToggle("Show clock", null, { ui.showClock }) { ui.showClock = it }
        addSlider("Clock size", 28, 90, "sp", { ui.clockSizeSp }) { ui.clockSizeSp = it }
        addChoice("Clock format", listOf("Follow system", "24-hour", "12-hour"), { ui.clockFormat }) {
            ui.clockFormat = it
        }
        addToggle("Show date", null, { ui.showDate }) { ui.showDate = it }
        addToggle("Notification previews", "Needs notification access (see Manage)", { ui.notifPreviews }) {
            ui.notifPreviews = it
        }
        addToggle("Now playing card", "Needs notification access (see Manage)", { ui.showMedia }) {
            ui.showMedia = it
        }
        addToggle("Next calendar event", "Asks for calendar permission", { ui.showCalendar }) { on ->
            ui.showCalendar = on
            if (on && !CalendarHelper.hasPermission(this)) {
                calendarPermission.launch(Manifest.permission.READ_CALENDAR)
            }
        }
        addToggle("\"All apps\" button", "Swiping up always works either way", { ui.showAllAppsButton }) {
            ui.showAllAppsButton = it
        }

        addSection("App list and scrolling")
        addSlider("List text size", 14, 32, "sp", { ui.listSizeSp }) { ui.listSizeSp = it }
        addChoice("Alphabet side", listOf("Right", "Left"), { if (ui.indexLeft) 1 else 0 }) {
            ui.indexLeft = it == 1
        }
        addSlider("Alphabet wave strength", 0, 150, "%", { ui.wavePercent }) { ui.wavePercent = it }
        addSlider("Scroll speed", 50, 200, "%", { ui.flingPercent }) { ui.flingPercent = it }
        addToggle("Haptic ticks while scrubbing", null, { ui.haptics }) { ui.haptics = it }

        addSection("Manage")
        addAction("Notification access", "Required for previews and Now playing") {
            try {
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            } catch (e: Exception) {
                Toast.makeText(this, "Can't open that on this device", Toast.LENGTH_SHORT).show()
            }
        }
        addNote("Hidden apps: open All apps, tap the \u22EE menu, then Hidden apps.")
        addAction("Reset everything to defaults", null) {
            AlertDialog.Builder(this)
                .setTitle("Reset all customization?")
                .setPositiveButton("Reset") { _, _ ->
                    ui.resetAll()
                    rebuild()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    // ------------------------------------------------------------ dialogs

    private fun presetDialog() {
        val names = UiPrefs.PRESETS.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Preset")
            .setItems(names) { _, which ->
                ui.applyPreset(which)
                rebuild()
            }
            .show()
    }

    private fun pickColor(title: String, allowWallpaper: Boolean, onPick: (Int?) -> Unit) {
        val names = ArrayList<String>()
        if (allowWallpaper) names.add("From wallpaper")
        for (s in SWATCHES) names.add(s.first)
        names.add("Custom hex...")
        val offset = if (allowWallpaper) 1 else 0
        AlertDialog.Builder(this)
            .setTitle(title)
            .setItems(names.toTypedArray()) { _, which ->
                when {
                    allowWallpaper && which == 0 -> onPick(null)
                    which == names.lastIndex -> hexDialog(title, onPick)
                    else -> onPick(SWATCHES[which - offset].second)
                }
            }
            .show()
    }

    private fun hexDialog(title: String, onPick: (Int?) -> Unit) {
        val input = EditText(this)
        input.hint = "#RRGGBB"
        input.setSingleLine()
        input.setPadding(dp(24), dp(16), dp(24), dp(16))
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(input)
            .setPositiveButton("Apply") { _, _ ->
                var raw = input.text.toString().trim()
                if (!raw.startsWith("#")) raw = "#$raw"
                try {
                    onPick(Color.parseColor(raw))
                } catch (e: IllegalArgumentException) {
                    Toast.makeText(this, "That isn't a valid colour", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ------------------------------------------------------------ row builders

    private fun label(text: String, sizeSp: Float, alpha: Float = 1f, bold: Boolean = false): TextView {
        val tv = TextView(this)
        tv.text = text
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        tv.setTextColor(fg)
        tv.alpha = alpha
        if (bold) tv.setTypeface(tv.typeface, android.graphics.Typeface.BOLD)
        return tv
    }

    private fun rowBox(horizontal: Boolean = false): LinearLayout {
        val row = LinearLayout(this)
        row.orientation = if (horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        if (horizontal) row.gravity = Gravity.CENTER_VERTICAL
        row.setPadding(0, dp(12), 0, dp(12))
        val tv = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackground, tv, true)
        if (tv.resourceId != 0) row.setBackgroundResource(tv.resourceId)
        return row
    }

    private fun addTitle(text: String) {
        val t = label(text, 30f, bold = true)
        t.setPadding(0, dp(8), 0, dp(4))
        content.addView(t)
    }

    private fun addNote(text: String) {
        val t = label(text, 13f, alpha = 0.6f)
        t.setPadding(0, dp(4), 0, dp(8))
        content.addView(t)
    }

    private fun addSection(text: String) {
        val t = label(text.uppercase(), 12f, alpha = 0.55f, bold = true)
        t.setPadding(0, dp(28), 0, dp(4))
        content.addView(t)
    }

    private fun addAction(title: String, sub: String?, onClick: () -> Unit) {
        val row = rowBox()
        row.addView(label(title, 16f))
        if (sub != null) row.addView(label(sub, 13f, alpha = 0.6f))
        row.setOnClickListener { onClick() }
        content.addView(row)
    }

    private fun addChoice(title: String, options: List<String>, get: () -> Int, set: (Int) -> Unit) {
        val row = rowBox()
        row.addView(label(title, 16f))
        val sub = label(options.getOrElse(get()) { "" }, 13f, alpha = 0.6f)
        row.addView(sub)
        row.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(title)
                .setSingleChoiceItems(options.toTypedArray(), get()) { dialog, which ->
                    set(which)
                    sub.text = options[which]
                    dialog.dismiss()
                }
                .show()
        }
        content.addView(row)
    }

    private fun addToggle(title: String, sub: String?, get: () -> Boolean, set: (Boolean) -> Unit) {
        val row = rowBox(horizontal = true)
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label(title, 16f))
        if (sub != null) texts.addView(label(sub, 13f, alpha = 0.6f))
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = SwitchCompat(this)
        sw.isChecked = get()
        sw.setOnCheckedChangeListener { _, checked -> set(checked) }
        row.addView(sw)
        row.setOnClickListener { sw.toggle() }
        content.addView(row)
    }

    private fun addSlider(title: String, lo: Int, hi: Int, unit: String, get: () -> Int, set: (Int) -> Unit) {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setPadding(0, dp(10), 0, dp(6))
        val current = get().coerceIn(lo, hi)
        val t = label("$title: $current$unit", 16f)
        val bar = SeekBar(this)
        bar.max = hi - lo
        bar.progress = current - lo
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + lo
                t.text = "$title: $value$unit"
                if (fromUser) set(value)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        box.addView(t)
        box.addView(bar)
        content.addView(box)
    }

    private fun dot(color: Int): GradientDrawable {
        val d = GradientDrawable()
        d.shape = GradientDrawable.OVAL
        d.setColor(color)
        d.setStroke(dp(1), 0x66808080)
        return d
    }

    private fun swatch(color: Int): View {
        val v = View(this)
        v.layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
        v.background = dot(color)
        return v
    }

    private fun addColor(title: String, get: () -> Int, set: (Int) -> Unit) {
        val row = rowBox(horizontal = true)
        val t = label(title, 16f)
        row.addView(t, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = swatch(get())
        row.addView(sw)
        row.setOnClickListener {
            pickColor(title, false) { picked ->
                if (picked != null) {
                    set(picked)
                    sw.background = dot(picked)
                }
            }
        }
        content.addView(row)
    }

    private fun addAccent() {
        val row = rowBox(horizontal = true)
        val texts = LinearLayout(this)
        texts.orientation = LinearLayout.VERTICAL
        texts.addView(label("Accent colour", 16f))
        val sub = label(accentSummary(), 13f, alpha = 0.6f)
        texts.addView(sub)
        row.addView(texts, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        val sw = swatch(if (ui.accentFromWallpaper) DEFAULT_ACCENT else ui.accentColor)
        row.addView(sw)
        row.setOnClickListener {
            pickColor("Accent colour", true) { picked ->
                if (picked == null) {
                    ui.accentFromWallpaper = true
                    sw.background = dot(DEFAULT_ACCENT)
                } else {
                    ui.accentFromWallpaper = false
                    ui.accentColor = picked
                    sw.background = dot(picked)
                }
                sub.text = accentSummary()
            }
        }
        content.addView(row)
    }

    private fun accentSummary(): String =
        if (ui.accentFromWallpaper) "Taken from your wallpaper" else "Fixed colour"

    private companion object {
        val SWATCHES = listOf(
            "White" to 0xFFFFFFFF.toInt(),
            "Off-white" to 0xFFF0EFEC.toInt(),
            "Black" to 0xFF000000.toInt(),
            "Charcoal" to 0xFF1C1C1C.toInt(),
            "Slate blue" to 0xFF8AA0C4.toInt(),
            "Sky" to 0xFF6CB6FF.toInt(),
            "Mint" to 0xFF7BD8A8.toInt(),
            "Teal" to 0xFF4FD1C5.toInt(),
            "Lime" to 0xFFC6F135.toInt(),
            "Amber" to 0xFFFFC857.toInt(),
            "Orange" to 0xFFFF9F5A.toInt(),
            "Coral" to 0xFFFF7A7A.toInt(),
            "Pink" to 0xFFFF8FC7.toInt(),
            "Lavender" to 0xFFB79CFF.toInt()
        )
    }
}
