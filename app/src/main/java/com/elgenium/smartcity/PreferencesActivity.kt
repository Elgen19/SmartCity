package com.elgenium.smartcity

import android.os.Bundle
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityPreferencesBinding
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.LayoutStateManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlin.properties.Delegates

class PreferencesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreferencesBinding
    private lateinit var userRef: DatabaseReference
    private var isNewUser by Delegates.notNull<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize View Binding
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        isNewUser = intent.getBooleanExtra("IS_NEW_USER", false)

        if (!isNewUser) {
            binding.backButton.visibility = View.VISIBLE
        } else {
            val layoutParams = binding.preferencesTitle.layoutParams as LinearLayout.LayoutParams
            layoutParams.marginStart = 0
            binding.preferencesTitle.layoutParams = layoutParams
        }

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        } else {
            userRef = FirebaseDatabase.getInstance().getReference("Users").child(user.uid)

            loadPreferences()
        }

        binding.backButton.setOnClickListener { 
            ActivityNavigationUtils.navigateToActivity(this, SettingsActivity::class.java, true)
        }

        binding.buttonSave.setOnClickListener {
            collectPreferences()?.let { preferences ->
                savePreferences(preferences)
            } ?: run {
                Toast.makeText(this, "Please select at least one option from each section.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadPreferences() {
        userRef.get().addOnSuccessListener { dataSnapshot ->
            val preferredTransport = dataSnapshot.child("preferredTransport").children.map { it.value.toString() }
            val preferredEvents = dataSnapshot.child("preferredEvents").children.map { it.value.toString() }

            // Pre-fill transport checkboxes
            binding.checkboxPublicTransport.isChecked = preferredTransport.contains("Public Transport")
            binding.checkboxCar.isChecked = preferredTransport.contains("Car")
            binding.checkboxWalking.isChecked = preferredTransport.contains("Walking")
            binding.checkboxMotor.isChecked = preferredTransport.contains("Motor")

            // Pre-fill event checkboxes
            binding.checkboxWeatherAdvisories.isChecked = preferredEvents.contains("Weather Advisories")
            binding.checkboxFoodHubs.isChecked = preferredEvents.contains("Popular Food Hubs/Stalls")
            binding.checkboxHealthFitness.isChecked = preferredEvents.contains("Health and Fitness")
            binding.checkboxFestivalsConcerts.isChecked = preferredEvents.contains("Festivals and Concerts")
            binding.checkboxTrafficAlerts.isChecked = preferredEvents.contains("Traffic Alerts")
            binding.checkboxPublicTransits.isChecked = preferredEvents.contains("Public Transits")
        }
    }


    private fun collectPreferences(): Map<String, Any>? {
        val selectedTransport = getSelectedOptions(
            binding.checkboxPublicTransport,
            binding.checkboxCar,
            binding.checkboxWalking,
            binding.checkboxMotor
        )

        val selectedEvents = getSelectedOptions(
            binding.checkboxWeatherAdvisories,
            binding.checkboxFoodHubs,
            binding.checkboxHealthFitness,
            binding.checkboxFestivalsConcerts,
            binding.checkboxTrafficAlerts,
            binding.checkboxPublicTransits
        )

        return if (selectedTransport.isEmpty() || selectedEvents.isEmpty()) {
            null
        } else {
            mapOf(
                "preferredTransport" to selectedTransport,
                "preferredEvents" to selectedEvents
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
                // Mark preferences as set
                userRef.child("preferencesSet").setValue(true)

                if (!isNewUser) {
                    LayoutStateManager.showSuccessLayout(this, "Preferences Updated!", "Your preferences was successfully updated.",  DashboardActivity::class.java)
                } else {
                    ActivityNavigationUtils.navigateToActivity(this, DashboardActivity::class.java, true)
                }

            } else {
                LayoutStateManager.showFailureLayout(this, "Failed to update preferences. Please tru again.", "Return to Settings")
            }
        }
    }

}
