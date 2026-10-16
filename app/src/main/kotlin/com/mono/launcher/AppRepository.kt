package com.mono.launcher

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * All app discovery and launching goes through here. Every app comes live from
 * PackageManager; labels are cached so re-scans (every time you return home) are cheap.
 */
class AppRepository(private val context: Context) {

    /** Runs the scan off the main thread so nothing janks while the list refreshes. */
    suspend fun getAllAppsAsync(): List<AppInfo> = withContext(Dispatchers.Default) { getAllApps() }

    @Suppress("DEPRECATION")
    fun getAllApps(): List<AppInfo> {
        val pm = context.packageManager
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val own = context.packageName
        val found = pm.queryIntentActivities(query, 0)

        val apps = ArrayList<AppInfo>(found.size)
        val seen = HashSet<String>()
        for (ri in found) {
            val ai = ri.activityInfo ?: continue
            if (ai.packageName == own) continue
            val id = ai.packageName + "/" + ai.name
            if (!seen.add(id)) continue
            val label = labelCache.getOrPut(id) {
                ri.loadLabel(pm).toString().ifBlank { ai.packageName }
            }
            apps.add(AppInfo(ai.packageName, ai.name, label))
        }

        // Group by letter ("#" first), then locale-aware alphabetical inside each letter.
        val collator = Collator.getInstance(Locale.getDefault()).apply { strength = Collator.PRIMARY }
        apps.sortWith(compareBy<AppInfo> { it.letter }.thenComparator { a, b -> collator.compare(a.label, b.label) })
        return apps
    }

    fun launch(app: AppInfo) {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            component = ComponentName(app.packageName, app.activityName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            // The app may have been removed since the list was built.
        }
    }

    fun openAppInfo(app: AppInfo) {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", app.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }

    fun uninstall(app: AppInfo) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = Uri.fromParts("package", app.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
        }
    }

    companion object {
        /** Last scan result, so a recreated home screen can paint instantly. */
        @Volatile
        var cached: List<AppInfo> = emptyList()

        private val labelCache = ConcurrentHashMap<String, String>()

        fun clearLabelCache() {
            labelCache.clear()
        }
    }
}
