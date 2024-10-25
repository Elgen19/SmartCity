package com.elgenium.smartcity

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
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
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        isNewUser = intent.getBooleanExtra("IS_NEW_USER", true)

        // Configure back button visibility
        binding.backButton.visibility = if (!isNewUser) View.VISIBLE else View.GONE

        // Adjust layout parameters for the title if needed
        Log.e("PreferencesActivity", "IS NEW USER before if: $isNewUser")

        if (isNewUser) {
            Log.e("PreferencesActivity", "IS NEW USER inside if: $isNewUser")
            val layoutParams = binding.preferencesTitle.layoutParams as LinearLayout.LayoutParams
            layoutParams.marginStart = 0
            binding.preferencesTitle.layoutParams = layoutParams
            setDefaultPreferences()
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        } else {
            userRef = FirebaseDatabase.getInstance().getReference("Users").child(user.uid)
            loadPreferences()
        }

        // Set click listener for back button
        binding.backButton.setOnClickListener {
            ActivityNavigationUtils.navigateToActivity(this, SettingsActivity::class.java, true)
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
            val preferredPlaces = dataSnapshot.child("preferredPlaces").children.map { it.value.toString() }
            binding.checkboxDiscoverPlaces.isChecked = preferredPlaces.contains("Discover new places (Restaurants, Cafes, Parks, etc.)")
            binding.checkboxFindEvents.isChecked = preferredPlaces.contains("Find events happening around me (Concerts, Festivals, etc.)")
            binding.checkboxExploreThingsToDo.isChecked = preferredPlaces.contains("Explore things to do in a new location (for tourists or travelers)")
            binding.checkboxKeepUpWithLocalHappenings.isChecked = preferredPlaces.contains("Keep up with local happenings and activities")

            val dailyActivities = dataSnapshot.child("dailyActivities").children.map { it.value.toString() }
            binding.checkboxWork.isChecked = dailyActivities.contains("Work")
            binding.checkboxSchool.isChecked = dailyActivities.contains("School")
            binding.checkboxSocializing.isChecked = dailyActivities.contains("Socializing")
            binding.checkboxOutdoorActivities.isChecked = dailyActivities.contains("Outdoor activities")
            binding.checkboxHobbies.isChecked = dailyActivities.contains("Hobbies (e.g., sports, arts)")

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

            val preferredEventSize = dataSnapshot.child("preferredEventSize").children.map { it.value.toString() }
            binding.checkboxLargeEvents.isChecked = preferredEventSize.contains("Large events (e.g., concerts, festivals)")
            binding.checkboxSmallerEvents.isChecked = preferredEventSize.contains("Smaller, intimate gatherings")
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

    }


    private fun collectPreferences(): Map<String, Any>? {
        // Collect selected options for preferences
        val selectedPlaces = getSelectedOptions(
            binding.checkboxDiscoverPlaces,
            binding.checkboxFindEvents,
            binding.checkboxExploreThingsToDo,
            binding.checkboxKeepUpWithLocalHappenings,
        )

        val placeToVisit = getSelectedOptions(
            binding.checkboxCafesRestaurants,
            binding.checkboxParksOutdoorSpaces,
            binding.checkboxCulturalHistoricalSites,
            binding.checkboxShoppingAreas,
            binding.checkboxWorkspacesStudyAreas,
            binding.checkboxEntertainmentVenues
        )

        val selectedActivities = getSelectedOptions(
            binding.checkboxWork,
            binding.checkboxSchool,
            binding.checkboxSocializing,
            binding.checkboxOutdoorActivities,
            binding.checkboxHobbies
        )

        val selectedEvents = getSelectedOptions(
            binding.checkboxConcerts,
            binding.checkboxFestivals,
            binding.checkboxSales,
            binding.checkboxWorkshops,
            binding.checkboxCommunity,
            binding.checkboxOutdoorEvents,
        )

        val eventSize = getSelectedOptions(
            binding.checkboxLargeEvents,
            binding.checkboxSmallerEvents
        )

        return if (selectedPlaces.isEmpty() || selectedActivities.isEmpty() || selectedEvents.isEmpty()) {
            null
        } else {
            mapOf(
                "preferredPlaces" to selectedPlaces,
                "dailyActivities" to selectedActivities,
                "preferredVisitPlaces" to placeToVisit,
                "preferredEvents" to selectedEvents,
                "preferredEventSize" to eventSize,
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
