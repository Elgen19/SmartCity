package com.elgenium.smartcity.sharedpreferences

import android.content.Context
import android.content.SharedPreferences

object PreferencesManager {
    private const val PREFS_NAME = "app_prefs"
    private const val KEY_ONBOARDING_COMPLETED = "onboarding_completed"

    private fun getPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isOnboardingCompleted(context: Context): Boolean {
        return getPreferences(context).getBoolean(KEY_ONBOARDING_COMPLETED, false)
    }

    fun setOnboardingCompleted(context: Context, completed: Boolean) {
        getPreferences(context).edit().putBoolean(KEY_ONBOARDING_COMPLETED, completed).apply()
    }
}
