package com.elgenium.smartcity

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivitySettingsBinding
import com.elgenium.smartcity.shared_preferences_keys.SettingsKeys
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.BottomNavigationManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var userRef: DatabaseReference
    private lateinit var sharedPreferences: SharedPreferences
    private var selectedMapTheme = "Light"
    private var isFewerLandmarks = false
    private var isFewerLabels = false
    private var isTrafficOverlayEnabled = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }
        userRef = FirebaseDatabase.getInstance().getReference("Users").child(user.uid)

        // Initialize SharedPreferences
        sharedPreferences = getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // Bottom navigation setup using singletons
        BottomNavigationManager.setupBottomNavigation(this, binding.bottomNavigation, SettingsActivity::class.java)

        setupFeedbacks()
        setupFAQ()
        setupEditPreferences()
        loadUserSettings()
        setupMapOptions()
    }

    private fun logSharedPreferences() {
        val sharedPreferences = getSharedPreferences("user_settings", MODE_PRIVATE)
        val allEntries = sharedPreferences.all
        for ((key, value) in allEntries) {
            Log.e("Preferences", "$key: $value")
        }
    }

    override fun onPause() {
        super.onPause()
        saveUserSettings()
        // Save to SharedPreferences
        with(sharedPreferences.edit()) {
            putBoolean(SettingsKeys.KEY_CONTEXT_RECOMMENDER, binding.contextRecommenderSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_EVENT_RECOMMENDER, binding.eventRecommenderSwitch.isChecked)
            putString(SettingsKeys.KEY_MAP_THEME, selectedMapTheme)
            putBoolean(SettingsKeys.KEY_MAP_LANDMARKS, isFewerLandmarks)
            putBoolean(SettingsKeys.KEY_MAP_LABELS, isFewerLabels)
            putBoolean(SettingsKeys.KEY_MAP_OVERLAY, isTrafficOverlayEnabled)
            putBoolean(SettingsKeys.KEY_STARTER_SCREEN, binding.setDashboardAsStarterSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_WEATHER, binding.weatherNotificationSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_CYCLONE, binding.cycloneAlertSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_TRAFFIC, binding.trafficUpdateSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_MEAL, binding.mealNotificationSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_ACTIVITY_RECOMMENDATION, binding.activityPlaceRecommenderSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_SIMILAR_PLACE, binding.similarPlaceRecommenderSwitch.isChecked)

            apply()
        }
        logSharedPreferences()

    }

    private fun setupFeedbacks() {
        binding.feedbackCard.setOnClickListener {
            ActivityNavigationUtils.navigateToActivity(this, FeedbackActivity::class.java, false)
        }
    }

    private fun setupFAQ() {
        binding.FAQCard.setOnClickListener {
            ActivityNavigationUtils.navigateToActivity(this, FAQActivity::class.java, false)
        }
    }

    private fun setupEditPreferences() {
        binding.editPreferencesCard.setOnClickListener {
            val intent = Intent(this, PreferencesActivity::class.java)
            intent.putExtra("IS_NEW_USER", false)
            val options = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
        }
    }

    private fun saveUserSettings() {
        val settings = mapOf(
            "context_recommender" to binding.contextRecommenderSwitch.isChecked,
            "event_recommender" to binding.eventRecommenderSwitch.isChecked,
            "map_theme" to selectedMapTheme,
            "map_landmarks" to isFewerLandmarks,
            "map_labels" to isFewerLabels,
            "map_overlay" to isTrafficOverlayEnabled,
            "start_screen" to binding.setDashboardAsStarterSwitch.isChecked,
            "weather_notifications" to binding.weatherNotificationSwitch.isChecked,
            "meal_notifications" to binding.weatherNotificationSwitch.isChecked,
            "cyclone_alert" to binding.cycloneAlertSwitch.isChecked,
            "traffic_alert" to binding.trafficUpdateSwitch.isChecked,
            "key_activity" to binding.activityPlaceRecommenderSwitch.isChecked,
            "similar_place" to binding.similarPlaceRecommenderSwitch.isChecked,




            )

        userRef.child("settings").updateChildren(settings).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.e("SettingsActivity", "Settings saved")
            } else {
                Toast.makeText(this, "Failed to update settings. Please try again.", Toast.LENGTH_SHORT).show()
            }

        }
    }

    private fun loadUserSettings() {
        // Load from SharedPreferences
        binding.contextRecommenderSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_CONTEXT_RECOMMENDER, false)
        binding.eventRecommenderSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_EVENT_RECOMMENDER, false)
        binding.setDashboardAsStarterSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_STARTER_SCREEN, false)
        selectedMapTheme = sharedPreferences.getString(SettingsKeys.KEY_MAP_THEME, "Aubergine").toString()
        isFewerLandmarks = sharedPreferences.getBoolean(SettingsKeys.KEY_MAP_LANDMARKS, false)
        isFewerLabels = sharedPreferences.getBoolean(SettingsKeys.KEY_MAP_LABELS, false)
        isTrafficOverlayEnabled = sharedPreferences.getBoolean(SettingsKeys.KEY_MAP_OVERLAY, true)
        binding.weatherNotificationSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_WEATHER, true)
        binding.mealNotificationSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_MEAL, true)
        binding.cycloneAlertSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_CYCLONE, true)
        binding.trafficUpdateSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_TRAFFIC, true)
        binding.activityPlaceRecommenderSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_ACTIVITY_RECOMMENDATION, false)
        binding.similarPlaceRecommenderSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_SIMILAR_PLACE, false)



    }

    private fun setupMapOptions() {
        when (selectedMapTheme) {
            "Standard" -> {
                binding.radioStandard.isChecked = true
                binding.imgMapPreview.setImageResource(R.drawable.light)

            }
            "Retro" -> {
                binding.radioRetro.isChecked = true
                binding.imgMapPreview.setImageResource(R.drawable.retro)

            }
            "Aubergine" -> {
                binding.radioAubergine.isChecked = true
                binding.imgMapPreview.setImageResource(R.drawable.aubergine)

            }
        }

        binding.adjustLabelsSwitch.isChecked = isFewerLabels
        binding.adjustLandmarkSwitch.isChecked = isFewerLandmarks
        binding.adjustTrafficOverlaySwitch.isChecked = isTrafficOverlayEnabled



        // Handle interactions, such as radio button selection
        val radioGroup = binding.radioGroupMapThemes
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radioStandard -> {
                    binding.imgMapPreview.setImageResource(R.drawable.light)
                    selectedMapTheme = "Standard"
                }
                R.id.radioRetro -> {
                    binding.imgMapPreview.setImageResource(R.drawable.retro)
                    selectedMapTheme = "Retro"
                }
                R.id.radioAubergine -> {
                    binding.imgMapPreview.setImageResource(R.drawable.aubergine)
                    selectedMapTheme = "Aubergine"
                }
            }
        }

        binding.adjustLabelsSwitch.setOnCheckedChangeListener { _, isChecked ->
            isFewerLabels = isChecked
        }

        binding.adjustLandmarkSwitch.setOnCheckedChangeListener { _, isChecked ->
            isFewerLandmarks = isChecked
        }

        binding.adjustTrafficOverlaySwitch.setOnCheckedChangeListener { _, isChecked ->
            isTrafficOverlayEnabled = isChecked
        }


    }




}
