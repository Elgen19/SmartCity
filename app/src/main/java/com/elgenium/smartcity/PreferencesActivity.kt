package com.elgenium.smartcity

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.text.HtmlCompat
import androidx.core.view.isVisible
import com.elgenium.smartcity.databinding.ActivityPreferencesBinding
import com.elgenium.smartcity.databinding.DialogActivitySuggestionsBinding
import com.elgenium.smartcity.databinding.DialogAddScheduleBinding
import com.elgenium.smartcity.network_reponses.GeocodingResponse
import com.elgenium.smartcity.shared_preferences_keys.SettingsKeys
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.GeocodingServiceSingleton
import com.elgenium.smartcity.singletons.LayoutStateManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.GenericTypeIndicator
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class PreferencesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreferencesBinding
    private lateinit var userRef: DatabaseReference
    private var isNewUser = true
    private val getAddressResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val placeName = result.data?.getStringExtra("PLACE_NAME")
            val address = result.data?.getStringExtra("PLACE_ADDRESS")
            val sender = result.data?.getStringExtra("SENDER")

            when (sender) {
                "FROM_PREFERENCES_HOME" -> binding.editTextAddress.setText("$placeName, $address")
                "FROM_PREFERENCES_STUDY" -> binding.editTextSchoolAddress.setText("$placeName, $address")
                else -> binding.editTextWorkAddress.setText("$placeName, $address")
            }
        }
    }

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
                binding.question7.isVisible -> showQuestion(6)
                binding.question8.isVisible -> showQuestion(7)
            }
        }

        binding.editTextAddress.setOnClickListener {
            // Launch the SearchActivity to pick an address
            val intent = Intent(this, SearchActivity::class.java)
            intent.putExtra("FROM_PREFERENCES_HOME", true)
            getAddressResult.launch(intent)
        }

        binding.editTextSchoolAddress.setOnClickListener {
            // Launch the SearchActivity to pick an address
            val intent = Intent(this, SearchActivity::class.java)
            intent.putExtra("FROM_PREFERENCES_STUDY", true)
            getAddressResult.launch(intent)
        }

        binding.editTextWorkAddress.setOnClickListener {
            // Launch the SearchActivity to pick an address
            val intent = Intent(this, SearchActivity::class.java)
            intent.putExtra("FROM_PREFERENCES_WORK", true)
            getAddressResult.launch(intent)
        }

        binding.radioGroupWorkOrStudy.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioWork -> {
                    // Show work section, hide others
                    binding.workLayout.visibility = View.VISIBLE
                    binding.studyLayout.visibility = View.GONE
                }
                R.id.radioStudy -> {
                    // Show study section, hide others
                    binding.workLayout.visibility = View.GONE
                    binding.studyLayout.visibility = View.VISIBLE
                }
                R.id.radioBoth -> {
                    // Show both work and study sections
                    binding.workLayout.visibility = View.VISIBLE
                    binding.studyLayout.visibility = View.VISIBLE
                }
            }
        }



        binding.radioGroupQuestion4.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.radio_none) {
                // Skip question 5 if "I don't have any" is selected
                showQuestion(6)
            }
        }

        binding.buttonWorkAtHome.setOnClickListener {
            val homeAddress = binding.editTextAddress.text.toString()
            binding.editTextWorkAddress.setText(homeAddress)
        }

        binding.buttonAddWorkSchedule.setOnClickListener {
            showAddScheduleDialog { schedule ->
                if (schedule != null) {
                    binding.textViewWorkScheduleSummary.text = schedule
                }
            }
        }

        binding.buttonAddSchoolSchedule.setOnClickListener {
            showAddScheduleDialog { schedule ->
                if (schedule != null) {
                    binding.textViewSchoolScheduleSummary.text = schedule
                }
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
                binding.question6.isVisible && isAnyCheckboxChecked(binding.question6) -> showQuestion(7)

                // Question 7
                binding.question7.isVisible && isQuestion7Valid() -> showQuestion(8)

                // Question 8
                binding.question8.isVisible && isQuestion8Valid() -> {
                    collectPreferences()?.let { preferences ->
                        savePreferences(preferences)
                    } ?: run {
                        Toast.makeText(this, "Please select at least one option from each section.", Toast.LENGTH_SHORT).show()
                    }
                }


                else -> Toast.makeText(this, "Please select at least one option.", Toast.LENGTH_SHORT).show()
            }
        }

        binding.imageButtonLocation.setOnClickListener {
            getUserLocation()
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
                    question7.visibility = View.GONE
                    question8.visibility = View.GONE
                    scrollLayout.visibility = View.GONE
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
                    question7.visibility = View.GONE
                    question8.visibility = View.GONE
                    backButton.visibility = View.VISIBLE
                    scrollLayout.visibility = View.GONE
                    submitButton.text = "Next"
                }
                3 -> {
                    question1.visibility = View.GONE
                    question2.visibility = View.GONE
                    question3.visibility = View.VISIBLE
                    question4.visibility = View.GONE
                    question5.visibility = View.GONE
                    question6.visibility = View.GONE
                    question7.visibility = View.GONE
                    question8.visibility = View.GONE
                    scrollLayout.visibility = View.GONE
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
                    question7.visibility = View.GONE
                    question8.visibility = View.GONE
                    scrollLayout.visibility = View.GONE
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
                    scrollLayout.visibility = View.GONE
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
                    question7.visibility = View.GONE
                    question8.visibility = View.GONE
                    backButton.visibility = View.VISIBLE
                    scrollLayout.visibility = View.GONE
                    submitButton.text = "Next"
                }

                7 -> {
                    question1.visibility = View.GONE
                    question2.visibility = View.GONE
                    question3.visibility = View.GONE
                    question4.visibility = View.GONE
                    question5.visibility = View.GONE
                    question6.visibility = View.GONE
                    question7.visibility = View.VISIBLE
                    question8.visibility = View.GONE
                    scrollLayout.visibility = View.GONE
                    backButton.visibility = View.VISIBLE
                    submitButton.text = "Next"
                }

                8 -> {
                    question1.visibility = View.GONE
                    question2.visibility = View.GONE
                    question3.visibility = View.GONE
                    question4.visibility = View.GONE
                    question5.visibility = View.GONE
                    question6.visibility = View.GONE
                    question7.visibility = View.GONE
                    question8.visibility = View.VISIBLE
                    scrollLayout.visibility = View.VISIBLE
                    backButton.visibility = View.VISIBLE
                    submitButton.text = "Get Started"
                }
            }
        }
    }

    private fun isQuestion7Valid(): Boolean {
        val inputText = binding.editTextAddress.text.toString().trim()
        return if (inputText.isEmpty()) {
            Toast.makeText(this, "Please provide input for Question 7.", Toast.LENGTH_SHORT).show()
            false
        } else {
            true
        }
    }

    private fun isQuestion8Valid(): Boolean {
        val selectedOptionId = binding.radioGroupWorkOrStudy.checkedRadioButtonId

        // Ensure a RadioButton is selected
        if (selectedOptionId == -1) {
            Toast.makeText(this, "Please select whether you are working, studying, or both.", Toast.LENGTH_SHORT).show()
            return false
        }

        var isValid = true

        // Check based on the selected option
        when (selectedOptionId) {
            R.id.radioWork -> {
                // Validate work address and schedule
                if (binding.editTextWorkAddress.text.toString().trim().isEmpty()) {
                    Toast.makeText(this, "Please provide your work address.", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
                if (binding.textViewWorkScheduleSummary.text.toString().trim().isEmpty()) {
                    Toast.makeText(this, "Please add your work schedule.", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
            }
            R.id.radioStudy -> {
                // Validate school address and schedule
                if (binding.editTextSchoolAddress.text.toString().trim().isEmpty()) {
                    Toast.makeText(this, "Please provide your school address.", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
                if (binding.textViewSchoolScheduleSummary.text.toString().trim().isEmpty()) {
                    Toast.makeText(this, "Please add your school schedule.", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
            }
            R.id.radioBoth -> {
                // Validate both work and school details
                if (binding.editTextWorkAddress.text.toString().trim().isEmpty()) {
                    Toast.makeText(this, "Please provide your work address.", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
                if (binding.textViewWorkScheduleSummary.text.toString().trim().isEmpty()) {
                    Toast.makeText(this, "Please add your work schedule.", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
                if (binding.editTextSchoolAddress.text.toString().trim().isEmpty()) {
                    Toast.makeText(this, "Please provide your school address.", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
                if (binding.textViewSchoolScheduleSummary.text.toString().trim().isEmpty()) {
                    Toast.makeText(this, "Please add your school schedule.", Toast.LENGTH_SHORT).show()
                    isValid = false
                }
            }
        }

        return isValid
    }

    private fun showAddScheduleDialog(onScheduleAdded: (String?) -> Unit) {
        // Inflate the dialog layout using its binding class
        val dialogBinding = DialogAddScheduleBinding.inflate(layoutInflater)

        // Create the dialog using the dialogBinding root view without a title
        val dialog = AlertDialog.Builder(this)
            .setView(dialogBinding.root)
            .create()

        // Handle Add Schedule button click
        dialogBinding.buttonConfirmSchedule.setOnClickListener {
            val schedule = dialogBinding.editTextSchedule.text.toString()
            if (validateScheduleInput(schedule)) {
                Toast.makeText(this, "Schedule Added: $schedule", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                onScheduleAdded(schedule) // Return the schedule through the callback
            } else {
                // Show error if the schedule input is invalid
                showSuggestionsDialog(
                    "Invalid Time Format",
                    "Please enter your schedule in the correct format, e.g., <b>Mon-Fri (10 PM - 11 AM)</b>. Ensure the days and time range are properly specified."
                )
            }
        }

        // Handle Cancel button click
        dialogBinding.buttonCancelSchedule.setOnClickListener {
            dialog.dismiss()
            onScheduleAdded(null) // Return null if the user cancels
        }

        dialog.show()
    }

    private fun validateScheduleInput(schedule: String): Boolean {
        val regex = Regex("^[A-Za-z]+\\s*-\\s*[A-Za-z]+.*\\(.*\\d{1,2}\\s*[apmAPM]+.*-.*\\d{1,2}\\s*[apmAPM]+.*\\)$")
        return regex.containsMatchIn(schedule)
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

            // **Pre-fill Question 7 (Where do you live?)**
            val address = dataSnapshot.child("address").value?.toString()
            if (!address.isNullOrEmpty()) {
                binding.editTextAddress.setText(address)
            }

            // **Pre-fill Question 8 (Work/Study schedule)**
            val workOrStudyData = dataSnapshot.child("workOrStudy").getValue(object : GenericTypeIndicator<Map<String, String>>() {})

            workOrStudyData?.let {
                val type = it["type"]
                when (type) {
                    "Work" -> {
                        binding.radioWork.isChecked = true
                        binding.editTextWorkAddress.setText(it["workAddress"])
                        binding.textViewWorkScheduleSummary.text = it["workSchedule"]
                    }
                    "Study" -> {
                        binding.radioStudy.isChecked = true
                        binding.editTextSchoolAddress.setText(it["schoolAddress"])
                        binding.textViewSchoolScheduleSummary.text = it["schoolSchedule"]
                    }
                    "Both" -> {
                        binding.radioBoth.isChecked = true
                        binding.editTextWorkAddress.setText(it["workAddress"])
                        binding.textViewWorkScheduleSummary.text = it["workSchedule"]
                        binding.editTextSchoolAddress.setText(it["schoolAddress"])
                        binding.textViewSchoolScheduleSummary.text = it["schoolSchedule"]
                    }
                }
            }
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

        // Collect Question 7 response
        val address = binding.editTextAddress.text.toString().trim()

        // Collect Question 8 response
        val workOrStudy = when (binding.radioGroupWorkOrStudy.checkedRadioButtonId) {
            R.id.radioWork -> {
                mapOf(
                    "type" to "Work",
                    "workAddress" to binding.editTextWorkAddress.text.toString().trim(),
                    "workSchedule" to binding.textViewWorkScheduleSummary.text.toString().trim()
                )
            }
            R.id.radioStudy -> {
                mapOf(
                    "type" to "Study",
                    "schoolAddress" to binding.editTextSchoolAddress.text.toString().trim(),
                    "schoolSchedule" to binding.textViewSchoolScheduleSummary.text.toString().trim()
                )
            }
            R.id.radioBoth -> {
                mapOf(
                    "type" to "Both",
                    "workAddress" to binding.editTextWorkAddress.text.toString().trim(),
                    "workSchedule" to binding.textViewWorkScheduleSummary.text.toString().trim(),
                    "schoolAddress" to binding.editTextSchoolAddress.text.toString().trim(),
                    "schoolSchedule" to binding.textViewSchoolScheduleSummary.text.toString().trim()
                )
            }
            else -> null
        }

        // Return null if required fields are missing
        return if (
            selectedActivities.isEmpty() ||
            selectedEvents.isEmpty() ||
            vehicleOwnership == null ||
            address.isEmpty() || // Ensure address is provided for Question 7
            workOrStudy == null  // Ensure Question 8 is answered
        ) {
            null
        } else {
            mapOf(
                "dailyActivities" to selectedActivities,
                "preferredVisitPlaces" to placeToVisit,
                "preferredEvents" to selectedEvents,
                "vehicleOwnership" to vehicleOwnership,
                "preferredGasStations" to preferredGasStations,
                "preferredMealPlaces" to preferredMealPlaces,
                "address" to address, // Question 7
                "workOrStudy" to workOrStudy // Question 8
            )
        }
    }


    private fun getUserLocation() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    val latitude = location.latitude
                    val longitude = location.longitude

                    getStreetNameFromCoordinates(latitude, longitude) { streetName ->
                        runOnUiThread {
                            binding.editTextAddress.setText(streetName ?: "Unknown address")
                        }
                    }
                } else {
                    Toast.makeText(this, "Unable to retrieve location.", Toast.LENGTH_SHORT).show()
                }
            }.addOnFailureListener {
                Toast.makeText(this, "Failed to get location: ${it.message}", Toast.LENGTH_SHORT).show()
            }

        } else {
            // Request permissions
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1
            )
        }
    }


    private fun getStreetNameFromCoordinates(latitude: Double, longitude: Double, callback: (String?) -> Unit) {
        val latLng = "$latitude,$longitude"
        val geocodingService = GeocodingServiceSingleton.geocodingService

        // Asynchronous call using enqueue
        geocodingService.getPlace(latLng, BuildConfig.MAPS_API_KEY).enqueue(object :
            Callback<GeocodingResponse> {
            override fun onResponse(call: Call<GeocodingResponse>, response: Response<GeocodingResponse>) {
                if (response.isSuccessful) {
                    val url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=$latLng&key=${BuildConfig.MAPS_API_KEY}"
                    Log.d("GeocodingRequest", url)

                    val body = response.body()
                    Log.d("GeocodingResponse", "Response Body: $body")

                    if (body?.status == "OK" && !body.results.isNullOrEmpty()) {
                        val addressComponents = body.results[0].address_components
                        val streetNumber = addressComponents.find { "street_number" in it.types }?.long_name
                        val route = addressComponents.find { "route" in it.types }?.long_name
                        val streetName = if (streetNumber != null && route != null) {
                            "$streetNumber $route"
                        } else {
                            route ?: "Unknown road"
                        }
                        callback(streetName)
                    } else {
                        Log.e("GeocodingResponse", "No results or status not OK. Status: ${body?.status}")
                        callback("No address found")
                    }
                } else {
                    Log.e("GeocodingResponse", "Response not successful: ${response.errorBody()?.string()}")
                    callback("No address found")
                }
            }

            override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {
                Log.e("DashboardActivity", "Geocoding failed due to an exception: ${t.message}", t)
                callback("Geocoder failed")

                val url = "https://maps.googleapis.com/maps/api/geocode/json?latlng=$latLng&key=${BuildConfig.MAPS_API_KEY}"
                Log.d("GeocodingRequest", url)

            }
        })
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

    private fun showSuggestionsDialog(
        title: String,
        message: String,
    ) {
        val binding = DialogActivitySuggestionsBinding.inflate(layoutInflater)
        val dialog = android.app.AlertDialog.Builder(this).setView(binding.root).create()

        // Set dialog title and message
        binding.dialogTitle.text = title
        binding.dialogMessage.text = HtmlCompat.fromHtml(message, HtmlCompat.FROM_HTML_MODE_LEGACY)

        // Handle "Dismiss" button (finalize selection)
        binding.btnDismiss.setOnClickListener {
            dialog.dismiss()
        }




        dialog.show()
    }
}


