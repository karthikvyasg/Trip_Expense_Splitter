package com.example.tripexpensesplitter

import android.app.Activity
import android.os.Build
import android.view.WindowManager

object DisplayUtils {
    fun enableMaxRefreshRate(activity: Activity) {
        // Ensure screenshots and screen capture are never restricted by the window
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = activity.display ?: return
            val supportedModes = display.supportedModes
            val maxMode = supportedModes.maxByOrNull { it.refreshRate }
            if (maxMode != null) {
                val layoutParams = activity.window.attributes
                layoutParams.preferredDisplayModeId = maxMode.modeId
                activity.window.attributes = layoutParams
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            @Suppress("DEPRECATION")
            val display = (activity.getSystemService(Activity.WINDOW_SERVICE) as? WindowManager)?.defaultDisplay ?: return
            @Suppress("DEPRECATION")
            val supportedModes = display.supportedModes
            val maxMode = supportedModes.maxByOrNull { it.refreshRate }
            if (maxMode != null) {
                val layoutParams = activity.window.attributes
                @Suppress("DEPRECATION")
                layoutParams.preferredDisplayModeId = maxMode.modeId
                activity.window.attributes = layoutParams
            }
        }
    }
}