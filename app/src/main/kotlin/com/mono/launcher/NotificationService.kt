package com.mono.launcher

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class NotifSummary(val count: Int, val text: String)

/**
 * In-memory only: whatever notifications are active right now, so the home screen can
 * show a one-line preview under a favorite. Nothing is written to disk. Needs the
 * one-time "Notification access" grant (Mono links straight to that settings page).
 */
object NotificationTracker {
    private val byKey = LinkedHashMap<String, StatusBarNotification>()
    private val listeners = mutableListOf<() -> Unit>()

    @Synchronized
    fun put(sbn: StatusBarNotification) {
        byKey[sbn.key] = sbn
        fire()
    }

    @Synchronized
    fun remove(key: String) {
        byKey.remove(key)
        fire()
    }

    @Synchronized
    fun clearAll() {
        byKey.clear()
        fire()
    }

    @Synchronized
    fun latestFor(packageName: String): StatusBarNotification? =
        byKey.values.filter { it.packageName == packageName }.maxByOrNull { it.postTime }

    /** One pass over everything: per-app count + text of the newest notification. */
    @Synchronized
    fun summaries(): Map<String, NotifSummary> {
        val counts = HashMap<String, Int>()
        val latest = HashMap<String, StatusBarNotification>()
        for (sbn in byKey.values) {
            val pkg = sbn.packageName
            counts[pkg] = (counts[pkg] ?: 0) + 1
            val cur = latest[pkg]
            if (cur == null || sbn.postTime >= cur.postTime) latest[pkg] = sbn
        }
        val out = HashMap<String, NotifSummary>()
        for ((pkg, sbn) in latest) out[pkg] = NotifSummary(counts[pkg] ?: 1, describe(sbn))
        return out
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun fire() { listeners.toList().forEach { it() } }

    private fun describe(sbn: StatusBarNotification): String {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        return when {
            title.isNotBlank() && text.isNotBlank() -> "$title · $text"
            title.isNotBlank() -> title
            else -> text
        }
    }
}

class NotificationService : NotificationListenerService() {

    override fun onListenerConnected() {
        super.onListenerConnected()
        try {
            activeNotifications?.forEach { handle(it) }
        } catch (e: Exception) {
            // Some OEM skins have nothing available right after connect.
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        handle(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        NotificationTracker.remove(sbn.key)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        NotificationTracker.clearAll()
    }

    private fun handle(sbn: StatusBarNotification) {
        // Media notifications are surfaced through the Now Playing card instead.
        if (sbn.notification.category == Notification.CATEGORY_TRANSPORT) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        NotificationTracker.put(sbn)
    }
}
