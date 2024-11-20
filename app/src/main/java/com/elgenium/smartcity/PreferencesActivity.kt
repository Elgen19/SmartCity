package com.elgenium.smartcity

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityPreferencesBinding
import com.elgenium.smartcity.shared_preferences_keys.SettingsKeys
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.LayoutStateManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class PreferencesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreferencesBinding
    private lateinit var userRef: DatabaseReference
    private var isNewUser = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize View Binding
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set the color of the navigation bar
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)

        isNewUser = intent.getBooleanExtra("IS_NEW_USER", true)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        } else {
            userRef = FirebaseDatabase.getInstance().getReference("Users").child(user.uid)
            loadPreferences()
        }

        // Set click listener for save button
        binding.submitButton.setOnClickListener {
            collectPreferences()?.let { preferences ->
                savePreferences(preferences)
            } ?: run {
                Toast.makeText(this, "Please select at least one option from each section.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPreferences() {
        userRef.get().addOnSuccessListener { dataSnapshot ->
            // Pre-fill checkboxes with saved preferences

            val dailyActivities = dataSnapshot.child("dailyActivities").children.map { it.value.toString() }
            binding.checkboxShopping.isChecked = dailyActivities.contains("Shopping at a Mall")
            binding.checkboxDining.isChecked = dailyActivities.contains("Dining at a Restaurant")
            binding.checkboxCoffee.isChecked = dailyActivities.contains("Enjoying Coffee at a Cafe")
            binding.checkboxDrinks.isChecked = dailyActivities.contains("Grabbing Drinks at a Bar")
            binding.checkboxTourist.isChecked = dailyActivities.contains("Exploring Tourist Attractions")
            binding.checkboxMovie.isChecked = dailyActivities.contains("Watching a Movie")
            binding.checkboxGym.isChecked = dailyActivities.contains("Working Out at the Gym")
            binding.checkboxBeach.isChecked = dailyActivities.contains("Relaxing at the Beach")


            val preferredVisitPlaces = dataSnapshot.child("preferredVisitPlaces").children.map { it.value.toString() }
            binding.checkboxCafesRestaurants.isChecked = preferredVisitPlaces.contains("Cafes and restaurants")
            binding.checkboxParksOutdoorSpaces.isChecked = preferredVisitPlaces.contains("Parks and outdoor spaces")
            binding.checkboxCulturalHistoricalSites.isChecked = preferredVisitPlaces.contains("Cultural and historical sites")
            binding.checkboxShoppingAreas.isChecked = preferredVisitPlaces.contains("Shopping areas and malls")
            binding.checkboxWorkspacesStudyAreas.isChecked = preferredVisitPlaces.contains("Workspaces or study areas")
            binding.checkboxEntertainmentVenues.isChecked = preferredVisitPlaces.contains("Entertainment venues (theaters, clubs)")

            val preferredEvents = dataSnapshot.child("preferredEvents").children.map { it.value.toString() }
            // Step 3: Check the mapped categories and update the checkboxes
            binding.checkboxConcerts.isChecked = preferredEvents.contains("Concerts & Live Performances")
            binding.checkboxFestivals.isChecked = preferredEvents.contains("Festivals & Celebrations")
            binding.checkboxSales.isChecked = preferredEvents.contains("Sales & Promotions")
            binding.checkboxWorkshops.isChecked = preferredEvents.contains("Workshops & Seminars")
            binding.checkboxCommunity.isChecked = preferredEvents.contains("Community Events")
            binding.checkboxOutdoorEvents.isChecked = preferredEvents.contains("Outdoor & Adventure Events")

        }
    }

    private fun setDefaultPreferences() {
        val sharedPreferences = getSharedPreferences("user_settings", MODE_PRIVATE)

        // Set default preferences
        with(sharedPreferences.edit()) {
            putBoolean(SettingsKeys.KEY_CONTEXT_RECOMMENDER, true)
            putBoolean(SettingsKeys.KEY_EVENT_RECOMMENDER, true)
            putBoolean(SettingsKeys.KEY_STARTER_SCREEN, false)
            putString(SettingsKeys.KEY_MAP_THEME, "Standard")
            putBoolean(SettingsKeys.KEY_MAP_LANDMARKS, false)
            putBoolean(SettingsKeys.KEY_MAP_LABELS, false)
            putBoolean(SettingsKeys.KEY_MAP_OVERLAY, true)
            putBoolean(SettingsKeys.KEY_WEATHER, false)
            putBoolean(SettingsKeys.KEY_MEAL, false)
            putBoolean(SettingsKeys.KEY_CYCLONE, false)
            putBoolean(SettingsKeys.KEY_TRAFFIC, false)
            putBoolean(SettingsKeys.KEY_ACTIVITY_RECOMMENDATION, false)
            putBoolean(SettingsKeys.KEY_SIMILAR_PLACE, false)



            apply()
        }

        // Log the values of SharedPreferences
        logSharedPreferences(sharedPreferences)
    }

    // Function to log SharedPreferences values
    private fun logSharedPreferences(sharedPreferences: SharedPreferences) {
        val contextRecommender = sharedPreferences.getBoolean("context_recommender", false)
        val eventRecommender = sharedPreferences.getBoolean("event_recommender", false)
        val startScreen = sharedPreferences.getBoolean("start_screen", false)
        val mapTheme = sharedPreferences.getString("map_theme", "Standard")
        val mapLandmarks = sharedPreferences.getBoolean("map_landmarks", false)
        val mapLabels = sharedPreferences.getBoolean("map_labels", false)
        val mapOverlay = sharedPreferences.getBoolean("map_overlay", false)
        val weatherNotifications = sharedPreferences.getBoolean("weather_notifications", false)
        val mealNotifications = sharedPreferences.getBoolean("meal_notifications", false)
        val cyclones = sharedPreferences.getBoolean("cyclone_alert", false)
        val traffic = sharedPreferences.getBoolean("traffic_alert", false)
        val keyActivity = sharedPreferences.getBoolean("key_activity", false)
        val similarPlace = sharedPreferences.getBoolean("similar_place", false)



        Log.e("PreferencesActivity", "context_recommender: $contextRecommender")
        Log.e("PreferencesActivity", "event_recommender: $eventRecommender")
        Log.e("PreferencesActivity", "start_screen: $startScreen")
        Log.e("PreferencesActivity", "map_theme: $mapTheme")
        Log.e("PreferencesActivity", "map_landmarks: $mapLandmarks")
        Log.e("PreferencesActivity", "map_labels: $mapLabels")
        Log.e("PreferencesActivity", "map_overlay: $mapOverlay")
        Log.e("PreferencesActivity", "weather_notifications: $weatherNotifications")
        Log.e("PreferencesActivity", "meal_notifications: $mealNotifications")
        Log.e("PreferencesActivity", "cyclones: $cyclones")
        Log.e("PreferencesActivity", "traffic: $traffic")
        Log.e("PreferencesActivity", "keyActivity: $keyActivity")
        Log.e("PreferencesActivity", "similarPlace: $similarPlace")

    }


    private fun collectPreferences(): Map<String, Any>? {
        // Collect selected options for preferences
        val placeToVisit = getSelectedOptions(
            binding.checkboxCafesRestaurants,
            binding.checkboxParksOutdoorSpaces,
            binding.checkboxCulturalHistoricalSites,
            binding.checkboxShoppingAreas,
            binding.checkboxWorkspacesStudyAreas,
            binding.checkboxEntertainmentVenues
        )

        val selectedActivities = getSelectedOptions(
            binding.checkboxShopping,
            binding.checkboxDining,
            binding.checkboxCoffee,
            binding.checkboxDrinks,
            binding.checkboxTourist,
            binding.checkboxMovie,
            binding.checkboxGym,
            binding.checkboxBeach

        )

        val selectedEvents = getSelectedOptions(
            binding.checkboxConcerts,
            binding.checkboxFestivals,
            binding.checkboxSales,
            binding.checkboxWorkshops,
            binding.checkboxCommunity,
            binding.checkboxOutdoorEvents,
        )


        return if (selectedActivities.isEmpty() || selectedEvents.isEmpty() ) {
            null
        } else {
            mapOf(
                "dailyActivities" to selectedActivities,
                "preferredVisitPlaces" to placeToVisit,
                "preferredEvents" to selectedEvents,
            )
        }
    }

    private fun getSelectedOptions(vararg checkBoxes: CheckBox): List<String> {
        return checkBoxes.filter { it.isChecked }
            .map { it.text.toString() }
    }

    private fun savePreferences(preferences: Map<String, Any>) {
        // Update the user's preferences in Firebase
        userRef.updateChildren(preferences).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                userRef.child("preferencesSet").setValue(true)

                if (!isNewUser) {
                    LayoutStateManager.showSuccessLayout(this, "Preferences Updated!", "Your preferences were successfully updated.", DashboardActivity::class.java)
                } else {
                    ActivityNavigationUtils.navigateToActivity(this, PlacesActivity::class.java, true)
                }
            } else {
                LayoutStateManager.showFailureLayout(this, "Failed to update preferences. Please try again.", "Return to Settings")
            }
        }
    }
}
