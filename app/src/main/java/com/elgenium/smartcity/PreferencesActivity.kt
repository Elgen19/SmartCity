package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import android.widget.CheckBox
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.elgenium.smartcity.databinding.ActivityPreferencesBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

class PreferencesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPreferencesBinding
    private lateinit var userRef: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize View Binding
        binding = ActivityPreferencesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        } else {
            userRef = FirebaseDatabase.getInstance().getReference("Users").child(user.uid)
        }

        binding.buttonSave.setOnClickListener {
            collectPreferences()?.let { preferences ->
                savePreferences(preferences)
            } ?: run {
                Toast.makeText(this, "Please select at least one option from each section.", Toast.LENGTH_SHORT).show()
            }
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
        userRef.updateChildren(preferences).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                // Mark preferences as set
                userRef.child("preferencesSet").setValue(true)

                Toast.makeText(this, "Preferences saved successfully", Toast.LENGTH_SHORT).show()
                val intent = Intent(this, DashboardActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
            } else {
                Toast.makeText(this, "Failed to save preferences. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

}
