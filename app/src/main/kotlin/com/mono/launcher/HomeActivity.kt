package com.mono.launcher

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mono.launcher.databinding.ActivityHomeBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import android.text.format.DateFormat

class HomeActivity : AppCompatActivity() {

    private lateinit var b: ActivityHomeBinding
    private lateinit var repo: AppRepository
    private lateinit var prefs: PrefsStore
    private lateinit var ui: UiPrefs
    private lateinit var favoritesAdapter: RowAdapter
    private lateinit var allAdapter: RowAdapter

    private var allAppsRaw: List<AppInfo> = emptyList()
    private var visibleApps: List<AppInfo> = emptyList()
    private var loaded = false
    private var sheetOpen = false
    private var query = ""
    private var wallpaperAccent: Int? = null
    private var look: UiTheme? = null
    private var receiverRegistered = false

    private val packageReceiver = PackageChangeReceiver {
        AppRepository.clearLabelCache()
        refreshApps()
    }
    private val notifListener: () -> Unit = { b.root.post { onNotificationsChanged() } }

    private val backCallback = object : OnBackPressedCallback(true) {
        override fun handleOnBackPressed() {
            if (sheetOpen) closeSheet()
            // On the home screen there is nowhere to go back to: do nothing on purpose.
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        b = ActivityHomeBinding.inflate(layoutInflater)
        setContentView(b.root)

        repo = AppRepository(this)
        prefs = PrefsStore(this)
        ui = UiPrefs(this)

        favoritesAdapter = RowAdapter(
            showSubtitle = true,
            onClick = ::launchApp,
            onLongClick = ::showAppMenu,
            onSubtitleClick = ::openNotification,
            onWebSearch = ::webSearch
        )
        allAdapter = RowAdapter(
            showSubtitle = false,
            onClick = ::launchApp,
            onLongClick = ::showAppMenu,
            onSubtitleClick = ::openNotification,
            onWebSearch = ::webSearch
        )

        b.favoritesList.layoutManager = LinearLayoutManager(this)
        b.favoritesList.adapter = favoritesAdapter
        b.favoritesList.itemAnimator = null
        b.allAppsList.layoutManager = LinearLayoutManager(this)
        b.allAppsList.adapter = allAdapter
        b.allAppsList.itemAnimator = null
        b.allAppsList.setHasFixedSize(true)
        b.allAppsList.setItemViewCacheSize(20)

        ViewCompat.setOnApplyWindowInsetsListener(b.root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val pad = dp(24)
            b.homeContainer.setPadding(pad + bars.left, pad + bars.top, pad + bars.right, dp(12) + bars.bottom)
            b.sheetContent.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, ime.bottom))
            insets
        }

        setupGestures()

        b.allAppsButton.setOnClickListener { openSheet() }
        b.closeButton.setOnClickListener { closeSheet() }
        b.moreButton.setOnClickListener { showMainMenu(b.moreButton) }
        b.notifHint.setOnClickListener { safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
        b.notifHint.setOnLongClickListener {
            prefs.setDismissedNotifHint()
            b.notifHint.visibility = View.GONE
            true
        }
        b.mediaPlayPause.setOnClickListener {
            val media = MediaHelper.activeMedia(this) ?: return@setOnClickListener
            if (media.isPlaying) media.controller.transportControls.pause()
            else media.controller.transportControls.play()
            b.root.postDelayed({ refreshMediaCard() }, 250)
        }

        b.searchBox.doAfterTextChanged {
            query = it?.toString().orEmpty()
            renderList()
        }
        b.searchBox.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_GO ||
                actionId == EditorInfo.IME_ACTION_DONE
            ) {
                submitSearch()
                true
            } else false
        }
        b.alphabet.onLetterSelected = { letter -> jumpToLetter(letter) }

        onBackPressedDispatcher.addCallback(this, backCallback)

        applyAppearance()
        val cached = AppRepository.cached
        if (cached.isNotEmpty()) setApps(cached)   // paint instantly, refresh in the background
    }

    override fun onStart() {
        super.onStart()
        if (!receiverRegistered) {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_REPLACED)
                addDataScheme("package")
            }
            ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
        }
        NotificationTracker.addListener(notifListener)
    }

    override fun onResume() {
        super.onResume()
        applyAppearance()          // picks up anything changed in Customize
        refreshWallpaperAccent()
        refreshApps()
        refreshNextEvent()
        refreshMediaCard()
        b.notifHint.visibility =
            if (!notificationAccessGranted() && !prefs.dismissedNotifHint()) View.VISIBLE else View.GONE
        renderHome()
        renderList()
    }

    override fun onStop() {
        super.onStop()
        if (receiverRegistered) {
            unregisterReceiver(packageReceiver)
            receiverRegistered = false
        }
        NotificationTracker.removeListener(notifListener)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while already here: close the app list and jump back to the top.
        if (intent.hasCategory(Intent.CATEGORY_HOME)) {
            if (sheetOpen) closeSheet()
            b.favoritesList.scrollToPosition(0)
        }
    }

    // ---------------------------------------------------------------- appearance

    private fun applyAppearance() {
        val t = UiTheme.resolve(ui, wallpaperAccent)
        look = t
        val face = Fonts.typeface(ui.font)

        window.setBackgroundDrawable(ColorDrawable(t.windowBackground))
        WindowCompat.getInsetsController(window, b.root).apply {
            isAppearanceLightStatusBars = t.lightBackground
            isAppearanceLightNavigationBars = t.lightBackground
        }

        // clock + date
        b.timeClock.visibility = if (ui.showClock) View.VISIBLE else View.GONE
        b.timeClock.setTextSize(TypedValue.COMPLEX_UNIT_SP, ui.clockSizeSp.toFloat())
        b.timeClock.typeface = face
        b.timeClock.setTextColor(t.text)
        when (ui.clockFormat) {
            1 -> { b.timeClock.format12Hour = "HH:mm"; b.timeClock.format24Hour = "HH:mm" }
            2 -> { b.timeClock.format12Hour = "h:mm"; b.timeClock.format24Hour = "h:mm" }
            else -> { b.timeClock.format12Hour = "h:mm"; b.timeClock.format24Hour = "HH:mm" }
        }
        val datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "EEEEMMMMd")
        b.dateClock.format12Hour = datePattern
        b.dateClock.format24Hour = datePattern
        b.dateClock.visibility = if (ui.showDate) View.VISIBLE else View.GONE
        b.dateClock.typeface = face
        b.dateClock.setTextColor(t.textSecondary)

        b.nextEventText.typeface = face
        b.nextEventText.setTextColor(t.accent)
        b.notifHint.typeface = face
        b.notifHint.setTextColor(t.textTertiary)
        b.emptyHome.typeface = face
        b.emptyHome.setTextColor(t.textTertiary)

        // where the favourites sit
        val vertical = when (ui.favPosition) {
            1 -> Gravity.CENTER_VERTICAL
            2 -> Gravity.TOP
            else -> Gravity.BOTTOM
        }
        (b.favoritesList.layoutParams as FrameLayout.LayoutParams).gravity = vertical
        (b.emptyHome.layoutParams as FrameLayout.LayoutParams).gravity = vertical
        b.favoritesList.requestLayout()
        b.emptyHome.requestLayout()

        val favGravity = when (ui.favAlign) {
            1 -> Gravity.CENTER_HORIZONTAL
            2 -> Gravity.END
            else -> Gravity.START
        }
        b.emptyHome.gravity = favGravity
        val listGravity = if (ui.indexLeft) Gravity.END else Gravity.START

        favoritesAdapter.style = RowStyle(
            ui.favSizeSp.toFloat(), face, t.text, t.textSecondary, t.accent, dp(ui.rowGapDp), favGravity
        )
        allAdapter.style = RowStyle(
            ui.listSizeSp.toFloat(), face, t.text, t.textSecondary, t.accent, dp(ui.rowGapDp), listGravity
        )

        val fling = ui.flingPercent / 100f
        b.favoritesList.flingMultiplier = fling
        b.allAppsList.flingMultiplier = fling

        // app list + scrubber
        b.alphabet.configure(t.accent, t.textTertiary, !ui.indexLeft, ui.wavePercent / 100f, ui.haptics)
        (b.alphabet.layoutParams as FrameLayout.LayoutParams).gravity =
            if (ui.indexLeft) Gravity.START else Gravity.END
        b.alphabet.requestLayout()
        if (ui.indexLeft) b.allAppsList.setPadding(dp(56), 0, dp(24), dp(24))
        else b.allAppsList.setPadding(dp(24), 0, dp(56), dp(24))

        b.sheet.setBackgroundColor(t.sheetBackground)
        b.searchBox.setTextColor(t.text)
        b.searchBox.setHintTextColor(t.textTertiary)
        b.searchBox.typeface = face
        b.searchBox.background = rounded(t.surface, 14)
        b.moreButton.setTextColor(t.textSecondary)
        b.closeButton.setTextColor(t.accent)

        // media card
        b.mediaCard.background = rounded(t.surface, 14)
        b.mediaTitle.setTextColor(t.text)
        b.mediaTitle.typeface = face
        b.mediaArtist.setTextColor(t.textSecondary)
        b.mediaArtist.typeface = face
        b.mediaPlayPause.imageTintList = ColorStateList.valueOf(t.text)

        b.allAppsButton.visibility = if (ui.showAllAppsButton) View.VISIBLE else View.GONE
        b.allAppsButton.setTextColor(t.textTertiary)
        b.allAppsButton.typeface = face
    }

    private fun rounded(color: Int, radiusDp: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }

    // ---------------------------------------------------------------- gestures

    private fun setupGestures() {
        val homeDetector = buildDetector(longPress = true)
        val listDetector = buildDetector(longPress = false)
        b.homeContainer.setOnTouchListener { _, ev -> homeDetector.onTouchEvent(ev) }
        // Lets swipe up/down work even when the gesture starts on top of the favourites list.
        b.favoritesList.addOnItemTouchListener(object : RecyclerView.SimpleOnItemTouchListener() {
            override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
                listDetector.onTouchEvent(e)
                return false
            }
        })
    }

    private fun buildDetector(longPress: Boolean): GestureDetector {
        val minDistance = dp(72).toFloat()
        val minVelocity = 900f
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
                if (e1 == null || sheetOpen) return false
                val dx = e2.x - e1.x
                val dy = e2.y - e1.y
                if (abs(dy) < abs(dx) || abs(dy) < minDistance || abs(velocityY) < minVelocity) return false
                if (dy < 0) openSheet() else StatusBarHelper.expandNotifications(this@HomeActivity)
                return true
            }

            override fun onLongPress(e: MotionEvent) {
                if (longPress && !sheetOpen) {
                    showMainMenu(if (b.timeClock.visibility == View.VISIBLE) b.timeClock else b.homeContainer)
                }
            }
        })
        detector.setIsLongpressEnabled(longPress)
        return detector
    }

    // ---------------------------------------------------------------- data

    private fun refreshApps() {
        lifecycleScope.launch {
            val fresh = repo.getAllAppsAsync()
            AppRepository.cached = fresh
            if (!loaded || fresh != allAppsRaw) setApps(fresh)
        }
    }

    private fun setApps(apps: List<AppInfo>) {
        allAppsRaw = apps
        val hidden = prefs.getHidden()
        visibleApps = if (hidden.isEmpty()) apps else apps.filterNot { it.id in hidden }
        loaded = true
        seedFavoritesIfNeeded()
        renderHome()
        renderList()
    }

    /** First run: start with whatever phone/messages/camera/browser/settings apps exist. */
    private fun seedFavoritesIfNeeded() {
        if (prefs.seeded || visibleApps.isEmpty()) return
        val groups = listOf(
            listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer", "com.oneplus.dialer"),
            listOf("com.google.android.apps.messaging", "com.samsung.android.messaging", "com.android.mms"),
            listOf("com.google.android.GoogleCamera", "com.sec.android.app.camera", "com.android.camera", "com.android.camera2"),
            listOf("com.android.chrome", "com.sec.android.app.sbrowser", "org.mozilla.firefox"),
            listOf("com.android.settings")
        )
        for (group in groups) {
            val hit = group.firstNotNullOfOrNull { pkg -> visibleApps.firstOrNull { it.packageName == pkg } }
            if (hit != null) prefs.addFavorite(hit.id)
        }
        prefs.seeded = true
    }

    private fun summaries(): Map<String, NotifSummary> =
        if (ui.notifPreviews) NotificationTracker.summaries() else emptyMap()

    private fun renderHome() {
        val s = summaries()
        val byId = visibleApps.associateBy { it.id }
        val rows = ArrayList<Row>()
        for (id in prefs.getFavorites()) {
            val app = byId[id] ?: continue
            val n = s[app.packageName]
            rows.add(Row.App(app, n?.count ?: 0, n?.text))
        }
        favoritesAdapter.submitList(rows)
        b.emptyHome.visibility = if (loaded && rows.isEmpty()) View.VISIBLE else View.GONE
        b.favoritesList.visibility = if (rows.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun renderList() {
        val s = summaries()
        val q = query.trim()
        val rows = ArrayList<Row>()
        val letters = HashSet<String>()
        if (q.isEmpty()) {
            var last = ""
            for (app in visibleApps) {
                if (app.letter != last) {
                    rows.add(Row.Header(app.letter))
                    letters.add(app.letter)
                    last = app.letter
                }
                rows.add(Row.App(app, s[app.packageName]?.count ?: 0))
            }
        } else {
            val matches = visibleApps.filter { it.label.contains(q, ignoreCase = true) }
            val (starts, rest) = matches.partition { it.label.startsWith(q, ignoreCase = true) }
            for (app in starts + rest) rows.add(Row.App(app, s[app.packageName]?.count ?: 0))
            rows.add(Row.Web(q))
        }
        allAdapter.submitList(rows)
        b.alphabet.setActiveLetters(letters)
        b.alphabet.visibility = if (q.isEmpty()) View.VISIBLE else View.INVISIBLE
    }

    private fun onNotificationsChanged() {
        renderHome()
        if (sheetOpen) renderList()
        refreshMediaCard()
    }

    private fun jumpToLetter(letter: String) {
        val order = listOf("#") + ('A'..'Z').map { it.toString() }
        val present = allAdapter.currentList.filterIsInstance<Row.Header>().map { it.letter }.toSet()
        if (present.isEmpty()) return
        val target = order.dropWhile { it != letter }.firstOrNull { it in present } ?: present.last()
        val pos = allAdapter.positionOfHeader(target)
        if (pos >= 0) (b.allAppsList.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
    }

    // ---------------------------------------------------------------- actions

    private fun launchApp(app: AppInfo) {
        repo.launch(app)
        if (sheetOpen) closeSheet()
    }

    private fun openNotification(app: AppInfo) {
        val pi = NotificationTracker.latestFor(app.packageName)?.notification?.contentIntent
        if (pi == null) {
            launchApp(app)
            return
        }
        try {
            pi.send()
        } catch (e: PendingIntent.CanceledException) {
            launchApp(app)
        }
    }

    private fun webSearch(q: String) {
        safeStart(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(q))))
        if (sheetOpen) closeSheet()
    }

    private fun submitSearch() {
        val q = query.trim()
        if (q.isEmpty()) return
        val first = visibleApps.firstOrNull { it.label.startsWith(q, ignoreCase = true) }
            ?: visibleApps.firstOrNull { it.label.contains(q, ignoreCase = true) }
        if (first != null) launchApp(first) else webSearch(q)
    }

    private fun safeStart(intent: Intent) {
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, R.string.cant_open, Toast.LENGTH_SHORT).show()
        }
    }

    private fun notificationAccessGranted(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(this).contains(packageName)

    private fun showAppMenu(app: AppInfo, anchor: View) {
        val popup = PopupMenu(this, anchor)
        val menu = popup.menu
        val isFav = prefs.isFavorite(app.id)
        menu.add(0, A_FAV, 0, if (isFav) R.string.remove_from_home else R.string.add_to_home)
        if (isFav) {
            menu.add(0, A_UP, 1, R.string.move_up)
            menu.add(0, A_DOWN, 2, R.string.move_down)
        }
        menu.add(0, A_HIDE, 3, R.string.hide_app)
        menu.add(0, A_INFO, 4, R.string.app_info)
        menu.add(0, A_UNINSTALL, 5, R.string.uninstall)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                A_FAV -> { prefs.toggleFavorite(app.id); renderHome() }
                A_UP -> { prefs.moveFavorite(app.id, -1); renderHome() }
                A_DOWN -> { prefs.moveFavorite(app.id, 1); renderHome() }
                A_HIDE -> { prefs.hide(app.id); setApps(allAppsRaw) }
                A_INFO -> repo.openAppInfo(app)
                A_UNINSTALL -> repo.uninstall(app)
            }
            true
        }
        popup.show()
    }

    private fun showMainMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        val menu = popup.menu
        menu.add(0, M_CUSTOMIZE, 0, R.string.menu_customize)
        if (prefs.getHidden().isNotEmpty()) menu.add(0, M_HIDDEN, 1, R.string.menu_hidden)
        menu.add(
            0, M_NOTIF, 2,
            if (notificationAccessGranted()) R.string.menu_notif_manage else R.string.menu_notif_enable
        )
        menu.add(0, M_WALLPAPER, 3, R.string.menu_wallpaper)
        menu.add(0, M_DEFAULT_HOME, 4, R.string.menu_default_home)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                M_CUSTOMIZE -> startActivity(Intent(this, SettingsActivity::class.java))
                M_HIDDEN -> showUnhideDialog()
                M_NOTIF -> safeStart(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                M_WALLPAPER -> safeStart(
                    Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), getString(R.string.menu_wallpaper))
                )
                M_DEFAULT_HOME -> safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
            }
            true
        }
        popup.show()
    }

    private fun showUnhideDialog() {
        val hidden = prefs.getHidden()
        val hiddenApps = allAppsRaw.filter { it.id in hidden }
        if (hiddenApps.isEmpty()) {
            Toast.makeText(this, R.string.no_hidden_apps, Toast.LENGTH_SHORT).show()
            return
        }
        val labels = hiddenApps.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.unhide_title)
            .setItems(labels) { _, which ->
                prefs.unhide(hiddenApps[which].id)
                setApps(allAppsRaw)
            }
            .show()
    }

    // ---------------------------------------------------------------- widgets

    private fun refreshNextEvent() {
        if (!ui.showCalendar) {
            b.nextEventText.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            val event = withContext(Dispatchers.IO) { CalendarHelper.nextEvent(this@HomeActivity) }
            if (event != null) {
                b.nextEventText.text = "${event.whenText} · ${event.title}"
                b.nextEventText.visibility = View.VISIBLE
            } else {
                b.nextEventText.visibility = View.GONE
            }
        }
    }

    private fun refreshMediaCard() {
        if (!ui.showMedia) {
            b.mediaCard.visibility = View.GONE
            return
        }
        lifecycleScope.launch {
            val media = withContext(Dispatchers.Default) { MediaHelper.activeMedia(this@HomeActivity) }
            if (media != null && (media.title.isNotBlank() || media.artist.isNotBlank())) {
                b.mediaCard.visibility = View.VISIBLE
                b.mediaTitle.text = media.title.ifBlank { getString(R.string.app_name) }
                b.mediaArtist.text = media.artist
                b.mediaPlayPause.setImageResource(if (media.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            } else {
                b.mediaCard.visibility = View.GONE
            }
        }
    }

    private fun refreshWallpaperAccent() {
        if (!ui.accentFromWallpaper) return
        lifecycleScope.launch {
            val color = withContext(Dispatchers.Default) { WallpaperTheme.extractAccent(this@HomeActivity, DEFAULT_ACCENT) }
            if (color != wallpaperAccent) {
                wallpaperAccent = color
                applyAppearance()
            }
        }
    }

    // ---------------------------------------------------------------- app list sheet

    private fun openSheet() {
        if (sheetOpen) return
        sheetOpen = true
        b.searchBox.setText("")
        b.allAppsList.scrollToPosition(0)
        b.sheet.animate().cancel()
        b.sheet.visibility = View.VISIBLE
        b.sheet.alpha = 0f
        b.sheet.translationY = dp(40).toFloat()
        b.sheet.animate().alpha(1f).translationY(0f).setDuration(210)
            .setInterpolator(DecelerateInterpolator()).start()
        b.homeContainer.animate().cancel()
        b.homeContainer.animate().alpha(0f).setDuration(180).start()
    }

    private fun closeSheet() {
        if (!sheetOpen) return
        sheetOpen = false
        hideKeyboard()
        b.searchBox.setText("")
        b.sheet.animate().cancel()
        b.sheet.animate().alpha(0f).translationY(dp(40).toFloat()).setDuration(160).withEndAction {
            if (!sheetOpen) b.sheet.visibility = View.INVISIBLE
        }.start()
        b.homeContainer.animate().cancel()
        b.homeContainer.animate().alpha(1f).setDuration(200).start()
    }

    private fun hideKeyboard() {
        val imm = ContextCompat.getSystemService(this, InputMethodManager::class.java)
        imm?.hideSoftInputFromWindow(b.searchBox.windowToken, 0)
    }

    private companion object {
        const val A_FAV = 1
        const val A_UP = 2
        const val A_DOWN = 3
        const val A_HIDE = 4
        const val A_INFO = 5
        const val A_UNINSTALL = 6

        const val M_CUSTOMIZE = 11
        const val M_HIDDEN = 12
        const val M_NOTIF = 13
        const val M_WALLPAPER = 14
        const val M_DEFAULT_HOME = 15
    }
}
