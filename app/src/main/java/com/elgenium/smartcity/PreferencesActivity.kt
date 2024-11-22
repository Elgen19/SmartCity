package com.elgenium.smartcity

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
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

        showQuestion(1)

        // Set button listeners
        binding.backButton.setOnClickListener {
            when {
                binding.question2.isVisible -> showQuestion(1)
                binding.question3.isVisible -> showQuestion(2)
                binding.question4.isVisible -> showQuestion(3)
                binding.question5.isVisible -> showQuestion(4)
                binding.question6.isVisible -> showQuestion(5)
            }
        }

        binding.radioGroupQuestion4.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radio_none) {
                // Skip question 5 if "I don't have any" is selected
                showQuestion(6)
            }
        }


        binding.submitButton.setOnClickListener {
            when {
                // Question 1
                binding.question1.isVisible && isAnyCheckboxChecked(binding.question1) -> showQuestion(2)
                // Question 2
                binding.question2.isVisible && isAnyCheckboxChecked(binding.question2) -> showQuestion(3)
                // Question 3
                binding.question3.isVisible && isAnyCheckboxChecked(binding.question3) -> showQuestion(4)
                // Question 4
                binding.question4.isVisible -> {
                    val checkedId = binding.radioGroupQuestion4.checkedRadioButtonId
                    if (checkedId != -1) { // Ensure one option is selected
                        if (checkedId == R.id.radio_none) {
                            // Skip Question 5 if "I don't have any" is selected
                            showQuestion(6)
                        } else {
                            showQuestion(5)
                        }
                    } else {
                        Toast.makeText(this, "Please select an option for Question 4.", Toast.LENGTH_SHORT).show()
                    }
                }
                // Question 5
                binding.question5.isVisible && isAnyCheckboxChecked(binding.question5) -> showQuestion(6)
                // Question 6
                binding.question6.isVisible && isAnyCheckboxChecked(binding.question6) -> {
                    collectPreferences()?.let { preferences ->
                        savePreferences(preferences)
                    } ?: run {
                        Toast.makeText(this, "Please select at least one option from each section.", Toast.LENGTH_SHORT).show()
                    }
                }
                else -> Toast.makeText(this, "Please select at least one option.", Toast.LENGTH_SHORT).show()
            }
        }


    }

    private fun showQuestion(questionNumber: Int) {
        with(binding) {
            when (questionNumber) {
                1 -> {
                    question1.visibility = View.VISIBLE
                    question2.visibility = View.GONE
                    question3.visibility = View.GONE
                    question4.visibility = View.GONE
                    question5.visibility = View.GONE
                    question6.visibility = View.GONE
                    backButton.visibility = View.GONE
                    submitButton.text = "Next"
                }
                2 -> {
                    question1.visibility = View.GONE
                    question2.visibility = View.VISIBLE
                    question3.visibility = View.GONE
                    question4.visibility = View.GONE
                    question5.visibility = View.GONE
                    question6.visibility = View.GONE
                    backButton.visibility = View.VISIBLE
                    submitButton.text = "Next"
                }
                3 -> {
                    question1.visibility = View.GONE
                    question2.visibility = View.GONE
                    question3.visibility = View.VISIBLE
                    question4.visibility = View.GONE
                    question5.visibility = View.GONE
                    question6.visibility = View.GONE
                    backButton.visibility = View.VISIBLE
                    submitButton.text = "Next"
                }
                4 -> {
                    question1.visibility = View.GONE
                    question2.visibility = View.GONE
                    question3.visibility = View.GONE
                    question4.visibility = View.VISIBLE
                    question5.visibility = View.GONE
                    question6.visibility = View.GONE
                    backButton.visibility = View.VISIBLE
                    submitButton.text = "Next"
                }
                5 -> {
                    question1.visibility = View.GONE
                    question2.visibility = View.GONE
                    question3.visibility = View.GONE
                    question4.visibility = View.GONE

                    // Show question 5 only if the user has a vehicle
                    val hasVehicle = binding.radioCar.isChecked ||
                            binding.radioMotorcycle.isChecked ||
                            binding.radioBoth.isChecked
                    if (hasVehicle) {
                        question5.visibility = View.VISIBLE
                    } else {
                        question5.visibility = View.GONE
                        // Automatically jump to question 6
                        showQuestion(6)
                        return
                    }

                    question6.visibility = View.GONE
                    backButton.visibility = View.VISIBLE
                    submitButton.text = "Next"
                }
                6 -> {
                    question1.visibility = View.GONE
                    question2.visibility = View.GONE
                    question3.visibility = View.GONE
                    question4.visibility = View.GONE
                    question5.visibility = View.GONE
                    question6.visibility = View.VISIBLE
                    backButton.visibility = View.VISIBLE
                    submitButton.text = "Get Started"
                }
            }
        }
    }


    private fun isAnyCheckboxChecked(layout: LinearLayout): Boolean {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child is CheckBox && child.isChecked) {
                return true
            }
        }
        return false
    }

    private fun loadPreferences() {
        userRef.get().addOnSuccessListener { dataSnapshot ->
            // Pre-fill checkboxes with saved preferences for daily activities
            val dailyActivities = dataSnapshot.child("dailyActivities").children.map { it.value.toString() }
            binding.checkboxShopping.isChecked = dailyActivities.contains("Shopping at a Mall")
            binding.checkboxDining.isChecked = dailyActivities.contains("Dining at a Restaurant")
            binding.checkboxCoffee.isChecked = dailyActivities.contains("Enjoying Coffee at a Cafe")
            binding.checkboxDrinks.isChecked = dailyActivities.contains("Grabbing Drinks at a Bar")
            binding.checkboxTourist.isChecked = dailyActivities.contains("Exploring Tourist Attractions")
            binding.checkboxMovie.isChecked = dailyActivities.contains("Watching a Movie")
            binding.checkboxGym.isChecked = dailyActivities.contains("Working Out at the Gym")
            binding.checkboxBeach.isChecked = dailyActivities.contains("Relaxing at the Beach")

            // Pre-fill checkboxes with saved preferences for visit places
            val preferredVisitPlaces = dataSnapshot.child("preferredVisitPlaces").children.map { it.value.toString() }
            binding.checkboxCafesRestaurants.isChecked = preferredVisitPlaces.contains("Cafes and restaurants")
            binding.checkboxParksOutdoorSpaces.isChecked = preferredVisitPlaces.contains("Parks and outdoor spaces")
            binding.checkboxCulturalHistoricalSites.isChecked = preferredVisitPlaces.contains("Cultural and historical sites")
            binding.checkboxShoppingAreas.isChecked = preferredVisitPlaces.contains("Shopping areas and malls")
            binding.checkboxWorkspacesStudyAreas.isChecked = preferredVisitPlaces.contains("Workspaces or study areas")
            binding.checkboxEntertainmentVenues.isChecked = preferredVisitPlaces.contains("Entertainment venues (theaters, clubs)")

            // Pre-fill checkboxes with saved preferences for events
            val preferredEvents = dataSnapshot.child("preferredEvents").children.map { it.value.toString() }
            binding.checkboxConcerts.isChecked = preferredEvents.contains("Concerts & Live Performances")
            binding.checkboxFestivals.isChecked = preferredEvents.contains("Festivals & Celebrations")
            binding.checkboxSales.isChecked = preferredEvents.contains("Sales & Promotions")
            binding.checkboxWorkshops.isChecked = preferredEvents.contains("Workshops & Seminars")
            binding.checkboxCommunity.isChecked = preferredEvents.contains("Community Events")
            binding.checkboxOutdoorEvents.isChecked = preferredEvents.contains("Outdoor & Adventure Events")

            // Pre-fill radio buttons for Question 4 (Vehicle Ownership)
            val vehicleOwnership = dataSnapshot.child("vehicleOwnership").value?.toString()
            when (vehicleOwnership) {
                "Car" -> binding.radioCar.isChecked = true
                "Motorcycle" -> binding.radioMotorcycle.isChecked = true
                "Both" -> binding.radioBoth.isChecked = true
                "None" -> binding.radioNone.isChecked = true
            }

            // Pre-fill checkboxes for Question 5 (Preferred Gas Stations)
            val preferredGasStations = dataSnapshot.child("preferredGasStations").children.map { it.value.toString() }
            binding.checkboxShell.isChecked = preferredGasStations.contains("Shell")
            binding.checkboxPetron.isChecked = preferredGasStations.contains("Petron")
            binding.checkboxCaltex.isChecked = preferredGasStations.contains("Caltex")
            binding.checkboxPhoenix.isChecked = preferredGasStations.contains("Phoenix")
            binding.checkboxSeaoil.isChecked = preferredGasStations.contains("Sea Oil")
            binding.checkboxTotal.isChecked = preferredGasStations.contains("Total")
            binding.checkboxFlyingv.isChecked = preferredGasStations.contains("Flying V")
            binding.checkboxPtt.isChecked = preferredGasStations.contains("PTT")

            // Pre-fill checkboxes for Question 6 (Preferred Meal Places)
            val preferredMealPlaces = dataSnapshot.child("preferredMealPlaces").children.map { it.value.toString() }
            binding.checkboxAmerican.isChecked = preferredMealPlaces.contains("American restaurants")
            binding.checkboxKorean.isChecked = preferredMealPlaces.contains("Korean restaurant")
            binding.checkboxRamen.isChecked = preferredMealPlaces.contains("Ramen restaurant")
            binding.checkboxFastfood.isChecked = preferredMealPlaces.contains("Fastfood restaurant")
            binding.checkboxSteak.isChecked = preferredMealPlaces.contains("Steak house")
            binding.fineDining.isChecked = preferredMealPlaces.contains("Fine dining")
            binding.foodCourt.isChecked = preferredMealPlaces.contains("Food court")
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
        // Collect selected options for Questions 1–3
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

        // Collect Question 4 response
        val vehicleOwnership = when (binding.radioGroupQuestion4.checkedRadioButtonId) {
            R.id.radio_car -> "Car"
            R.id.radio_motorcycle -> "Motorcycle"
            R.id.radio_both -> "Both"
            R.id.radio_none -> "None"
            else -> null
        }

        // Collect Question 5 response (only if applicable)
        val preferredGasStations = if (vehicleOwnership != "None") {
            getSelectedOptions(
                binding.checkboxShell,
                binding.checkboxPetron,
                binding.checkboxCaltex,
                binding.checkboxPhoenix,
                binding.checkboxSeaoil,
                binding.checkboxTotal,
                binding.checkboxFlyingv,
                binding.checkboxPtt
            )
        } else {
            emptyList()
        }

        // Collect Question 6 response
        val preferredMealPlaces = getSelectedOptions(
            binding.checkboxAmerican,
            binding.checkboxKorean,
            binding.checkboxRamen,
            binding.checkboxFastfood,
            binding.checkboxSteak,
            binding.fineDining,
            binding.foodCourt
        )

        // Return null if required fields are missing
        return if (
            selectedActivities.isEmpty() ||
            selectedEvents.isEmpty() ||
            vehicleOwnership == null
        ) {
            null
        } else {
            mapOf(
                "dailyActivities" to selectedActivities,
                "preferredVisitPlaces" to placeToVisit,
                "preferredEvents" to selectedEvents,
                "vehicleOwnership" to vehicleOwnership,
                "preferredGasStations" to preferredGasStations,
                "preferredMealPlaces" to preferredMealPlaces
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


