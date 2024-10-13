package com.elgenium.smartcity

import android.app.ActivityOptions
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivitySettingsBinding
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.BottomNavigationManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var userRef: DatabaseReference


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

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // Bottom navigation setup using singletons
        BottomNavigationManager.setupBottomNavigation(this, binding.bottomNavigation, SettingsActivity::class.java)

        setupFeedbacks()
        setupFAQ()
        setupEditPreferences()
        loadUserSettings()

    }



    override fun onPause() {
        super.onPause()
        saveUserSettings()
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
            "pushNotifications" to binding.turnOnPushNotificationsSwitch.isChecked,
            "trafficUpdates" to binding.enableTrafficUpdateNotificationsSwitch.isChecked,
            "eventsNotifications" to binding.enableEventsNotificationsSwitch.isChecked,
            "mealReminder" to binding.mealReminderSwitch.isChecked,
            "exerciseReminder" to binding.exerciseReminderSwitch.isChecked,
            "newEventReminders" to binding.newEventRemindersSwitch.isChecked
        )

        userRef.child("settings").updateChildren(settings).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("SettingsActivity", "Settings saved")
            } else {
                Toast.makeText(this, "Failed to update settings. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadUserSettings() {
        userRef.child("settings").get().addOnSuccessListener { dataSnapshot ->
            dataSnapshot?.let {
                binding.turnOnPushNotificationsSwitch.isChecked = it.child("pushNotifications").getValue(Boolean::class.java) ?: false
                binding.enableTrafficUpdateNotificationsSwitch.isChecked = it.child("trafficUpdates").getValue(Boolean::class.java) ?: false
                binding.enableEventsNotificationsSwitch.isChecked = it.child("eventsNotifications").getValue(Boolean::class.java) ?: false
                binding.mealReminderSwitch.isChecked = it.child("mealReminder").getValue(Boolean::class.java) ?: false
                binding.exerciseReminderSwitch.isChecked = it.child("exerciseReminder").getValue(Boolean::class.java) ?: false
                binding.newEventRemindersSwitch.isChecked = it.child("newEventReminders").getValue(Boolean::class.java) ?: false
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load settings", Toast.LENGTH_SHORT).show()
        }
    }


}
