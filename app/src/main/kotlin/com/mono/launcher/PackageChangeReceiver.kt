package com.mono.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Registered dynamically (not in the manifest — implicit package broadcasts
 * are restricted there on modern Android). Fires on install/uninstall/update
 * so the list stays live without a manual refresh.
 */
class PackageChangeReceiver(private val onChanged: () -> Unit) : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        onChanged()
    }
}
