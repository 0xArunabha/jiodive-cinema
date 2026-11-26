package com.jiodive.cinema.ui.components

import android.app.Activity
import android.os.Build

/**
 * Unlocks the panel's native peak refresh rate (90Hz / 120Hz / 144Hz) so scrolling,
 * sheets, and animations never throttle down to 60Hz.
 */
object DisplayRefreshHelper {
    @Suppress("DEPRECATION")
    fun enablePeakRefreshRate(activity: Activity) {
        runCatching {
            val activeDisplay = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                activity.display
            } else {
                activity.windowManager.defaultDisplay
            } ?: return@runCatching

            val cur = activeDisplay.mode
            val peakMode = activeDisplay.supportedModes
                .filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }
                .maxByOrNull { it.refreshRate }
                ?: cur

            val attrs = activity.window.attributes
            if (attrs.preferredDisplayModeId != peakMode.modeId || attrs.preferredRefreshRate != peakMode.refreshRate) {
                attrs.preferredRefreshRate = peakMode.refreshRate
                attrs.preferredDisplayModeId = peakMode.modeId
                activity.window.attributes = attrs
            }
        }
    }
}
