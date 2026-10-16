package com.mono.launcher

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import androidx.palette.graphics.Palette
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Wallpaper-adaptive accent. First choice is the colour set Android itself extracts from
 * the wallpaper (no permission needed); Palette on the wallpaper bitmap is the fallback.
 */
object WallpaperTheme {
    fun extractAccent(context: Context, fallback: Int): Int {
        val wm = WallpaperManager.getInstance(context)
        try {
            val colors = wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM)
            if (colors != null) {
                val candidates = listOfNotNull(
                    colors.primaryColor.toArgb(),
                    colors.secondaryColor?.toArgb(),
                    colors.tertiaryColor?.toArgb()
                )
                val best = candidates.maxByOrNull { saturation(it) }
                if (best != null) return best
            }
        } catch (e: Exception) {
            // fall through to Palette
        }
        return try {
            val bitmap = (wm.drawable as? BitmapDrawable)?.bitmap
            if (bitmap != null) {
                val palette = Palette.from(bitmap).generate()
                palette.vibrantSwatch?.rgb ?: palette.dominantSwatch?.rgb ?: fallback
            } else fallback
        } catch (e: Exception) {
            fallback
        }
    }

    private fun saturation(color: Int): Float {
        val hsv = FloatArray(3)
        Color.colorToHSV(color, hsv)
        return hsv[1]
    }
}

/** Reads the next upcoming event off the device calendar, if permission was granted. */
object CalendarHelper {
    data class NextEvent(val title: String, val whenText: String)

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun nextEvent(context: Context): NextEvent? {
        if (!hasPermission(context)) return null

        val now = System.currentTimeMillis()
        val builder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(builder, now)
        ContentUris.appendId(builder, now + 7L * 24 * 60 * 60 * 1000)
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN)

        return try {
            context.contentResolver
                .query(builder.build(), projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val title = c.getString(0) ?: return null
                        val begin = c.getLong(1)
                        NextEvent(title, SimpleDateFormat("EEE, HH:mm", Locale.getDefault()).format(Date(begin)))
                    } else null
                }
        } catch (e: Exception) {
            null
        }
    }
}

/** Whatever is playing right now. Needs the notification-access grant (MediaSessionManager requires it). */
object MediaHelper {
    data class ActiveMedia(
        val title: String,
        val artist: String,
        val isPlaying: Boolean,
        val controller: MediaController
    )

    fun activeMedia(context: Context): ActiveMedia? = try {
        val msm = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val component = ComponentName(context, NotificationService::class.java)
        val sessions = msm.getActiveSessions(component)
        val controller = sessions.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: sessions.firstOrNull()
        controller?.metadata?.let { md ->
            ActiveMedia(
                title = md.getString(MediaMetadata.METADATA_KEY_TITLE).orEmpty(),
                artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST).orEmpty(),
                isPlaying = controller.playbackState?.state == PlaybackState.STATE_PLAYING,
                controller = controller
            )
        }
    } catch (e: SecurityException) {
        null
    } catch (e: Exception) {
        null
    }
}

/**
 * Expands the system notification shade for swipe-down. Calls a hidden StatusBarManager
 * method (the same trick most launchers use). Not a stable public API, so it fails
 * silently instead of crashing if a future Android release blocks it.
 */
object StatusBarHelper {
    fun expandNotifications(context: Context): Boolean = try {
        val service = context.getSystemService("statusbar")
        val cls = Class.forName("android.app.StatusBarManager")
        cls.getMethod("expandNotificationsPanel").invoke(service)
        true
    } catch (e: Exception) {
        false
    }
}
