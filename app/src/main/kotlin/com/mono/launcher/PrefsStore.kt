package com.mono.launcher

import android.content.Context

/**
 * Favorites (shown on home) and hidden apps. Dependency-free: a delimited string in
 * SharedPreferences. Everything stays on the phone.
 */
class PrefsStore(context: Context) {
    private val prefs = context.getSharedPreferences("mono_prefs", Context.MODE_PRIVATE)

    fun getFavorites(): List<String> =
        prefs.getString(KEY_FAVORITES, "").orEmpty().split(SEP).filter { it.isNotBlank() }

    private fun setFavorites(ids: List<String>) {
        prefs.edit().putString(KEY_FAVORITES, ids.joinToString(SEP)).apply()
    }

    fun isFavorite(id: String): Boolean = getFavorites().contains(id)

    fun addFavorite(id: String) {
        val current = getFavorites()
        if (!current.contains(id)) setFavorites(current + id)
    }

    fun toggleFavorite(id: String) {
        val current = getFavorites().toMutableList()
        if (current.contains(id)) current.remove(id) else current.add(id)
        setFavorites(current)
    }

    fun moveFavorite(id: String, delta: Int) {
        val list = getFavorites().toMutableList()
        val from = list.indexOf(id)
        if (from < 0) return
        val to = (from + delta).coerceIn(0, list.lastIndex)
        if (from == to) return
        list.removeAt(from)
        list.add(to, id)
        setFavorites(list)
    }

    fun getHidden(): Set<String> =
        prefs.getString(KEY_HIDDEN, "").orEmpty().split(SEP).filter { it.isNotBlank() }.toSet()

    private fun setHidden(ids: Set<String>) {
        prefs.edit().putString(KEY_HIDDEN, ids.joinToString(SEP)).apply()
    }

    fun hide(id: String) {
        setHidden(getHidden() + id)
        setFavorites(getFavorites().filter { it != id })
    }

    fun unhide(id: String) {
        setHidden(getHidden() - id)
    }

    var seeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(value) { prefs.edit().putBoolean(KEY_SEEDED, value).apply() }

    fun dismissedNotifHint(): Boolean = prefs.getBoolean(KEY_DISMISSED_HINT, false)
    fun setDismissedNotifHint() { prefs.edit().putBoolean(KEY_DISMISSED_HINT, true).apply() }

    companion object {
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_HIDDEN = "hidden"
        private const val KEY_SEEDED = "seeded"
        private const val KEY_DISMISSED_HINT = "dismissed_notif_hint"
        private const val SEP = "|~|"
    }
}
