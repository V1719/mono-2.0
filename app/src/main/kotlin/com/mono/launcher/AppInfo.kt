package com.mono.launcher

import java.text.Normalizer

/** One launchable activity, as reported by PackageManager. */
data class AppInfo(
    val packageName: String,
    val activityName: String,
    val label: String
) {
    val id: String get() = "$packageName/$activityName"

    /** Section letter: A-Z (accents stripped) or "#" for everything else. */
    val letter: String = letterFor(label)
}

private fun letterFor(label: String): String {
    val first = label.trimStart().firstOrNull() ?: return "#"
    val base = Normalizer.normalize(first.toString(), Normalizer.Form.NFD).firstOrNull()?.uppercaseChar()
        ?: return "#"
    return if (base in 'A'..'Z') base.toString() else "#"
}
