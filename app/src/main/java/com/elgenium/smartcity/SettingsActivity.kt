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
            putBoolean(SettingsKeys.KEY_PUSH_NOTIFICATIONS, binding.turnOnPushNotificationsSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_TRAFFIC_UPDATES, binding.enableTrafficUpdateNotificationsSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_EVENTS_NOTIFICATIONS, binding.enableEventsNotificationsSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_CONTEXT_RECOMMENDER, binding.contextRecommenderSwitch.isChecked)
            putBoolean(SettingsKeys.KEY_EVENT_RECOMMENDER, binding.eventRecommenderSwitch.isChecked)
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
            "push_notifications" to binding.turnOnPushNotificationsSwitch.isChecked,
            "traffic_updates" to binding.enableTrafficUpdateNotificationsSwitch.isChecked,
            "events_notifications" to binding.enableEventsNotificationsSwitch.isChecked,
            "context_recommender" to binding.contextRecommenderSwitch.isChecked,
            "event_recommender" to binding.eventRecommenderSwitch.isChecked,

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
        binding.turnOnPushNotificationsSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_PUSH_NOTIFICATIONS, false)
        binding.enableTrafficUpdateNotificationsSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_TRAFFIC_UPDATES, false)
        binding.enableEventsNotificationsSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_EVENTS_NOTIFICATIONS, false)
        binding.contextRecommenderSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_CONTEXT_RECOMMENDER, false)
        binding.eventRecommenderSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_EVENT_RECOMMENDER, false)

    }


}
