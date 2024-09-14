package com.elgenium.smartcity.singletons

import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.DashboardActivity
import com.elgenium.smartcity.EventsActivity
import com.elgenium.smartcity.FavoritesActivity
import com.elgenium.smartcity.PlacesActivity
import com.elgenium.smartcity.R
import com.elgenium.smartcity.SettingsActivity
import com.elgenium.smartcity.singletons.ActivityNavigationUtils.navigateToActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

object BottomNavigationManager {
    fun setupBottomNavigation(
        activity: AppCompatActivity,
        bottomNavigationView: BottomNavigationView,
        currentActivityClass: Class<out AppCompatActivity>
    ) {
        // Set the selected item in the BottomNavigationView based on the current activity
        when (currentActivityClass) {
            DashboardActivity::class.java -> bottomNavigationView.selectedItemId = R.id.navigation_home
            PlacesActivity::class.java -> bottomNavigationView.selectedItemId = R.id.navigation_places
            FavoritesActivity::class.java -> bottomNavigationView.selectedItemId = R.id.navigation_favorites
            SettingsActivity::class.java -> bottomNavigationView.selectedItemId = R.id.navigation_settings
            EventsActivity::class.java -> bottomNavigationView.selectedItemId = R.id.navigation_events
            // Add more cases as needed
        }

        // Set up the BottomNavigationView listener
        bottomNavigationView.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    if (currentActivityClass != DashboardActivity::class.java) {
                        navigateToActivity(activity, DashboardActivity::class.java, true)
                    }
                    true
                }
                R.id.navigation_places -> {
                    if (currentActivityClass != PlacesActivity::class.java) {
                        navigateToActivity(activity, PlacesActivity::class.java, true)
                    }
                    true
                }
                R.id.navigation_favorites -> {
                    if (currentActivityClass != FavoritesActivity::class.java) {
                        navigateToActivity(activity, FavoritesActivity::class.java, true)
                    }
                    true
                }
                R.id.navigation_events -> {
                    if (currentActivityClass != EventsActivity::class.java) {
                        navigateToActivity(activity, EventsActivity::class.java, true)
                    }
                    true
                }
                R.id.navigation_settings -> {
                    if (currentActivityClass != SettingsActivity::class.java) {
                        navigateToActivity(activity, SettingsActivity::class.java, true)
                    }
                    true
                }
                else -> false
            }
        }
    }

}

