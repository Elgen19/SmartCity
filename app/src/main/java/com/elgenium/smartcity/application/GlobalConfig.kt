package com.elgenium.smartcity.application

import android.app.Application
import android.content.Context

class GlobalConfig : Application() {

    override fun onCreate() {
        super.onCreate()

        // Reset the recommendation flag when the app is created
        resetRecommendationsFlag()
    }

    private fun resetRecommendationsFlag() {
        val sharedPreferences = getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
        sharedPreferences.edit().putBoolean("recommendations_shown", false).apply()
    }
}