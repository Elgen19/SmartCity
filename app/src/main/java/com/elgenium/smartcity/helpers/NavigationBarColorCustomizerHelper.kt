package com.elgenium.smartcity.helpers

import android.app.Activity
import androidx.core.content.ContextCompat

object NavigationBarColorCustomizerHelper {
    /**
     * Sets the color of the navigation bar.
     *
     * @param activity The activity where the navigation bar color needs to be set.
     * @param colorResId The resource ID of the color to set.
     */
    fun setNavigationBarColor(activity: Activity, colorResId: Int) {
        activity.window.navigationBarColor = ContextCompat.getColor(activity, colorResId)
    }
}