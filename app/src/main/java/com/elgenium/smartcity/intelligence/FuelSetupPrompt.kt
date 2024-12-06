package com.elgenium.smartcity.intelligence

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import android.view.LayoutInflater
import android.widget.Toast
import com.elgenium.smartcity.databinding.BottomSheetFuelSetupPromptBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class FuelSetupPrompt(private val activity: Activity,
                      private val travelMode: String) {

    private lateinit var binding: BottomSheetFuelSetupPromptBinding

    // SharedPreferences key and name
    private val PREFS_NAME = "fuel_setup_preferences"
    private val PREF_KEY_HAS_SHOWN = "has_shown_fuel_prompt"

    // SharedPreferences instance
    private val sharedPreferences: SharedPreferences =
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Method to show the fuel setup prompt
    fun showFuelSetupPrompt() {
        // Check if the bottom sheet has already been shown
        if (sharedPreferences.getBoolean(PREF_KEY_HAS_SHOWN, false)) {
            // If it has been shown, don't display the bottom sheet again
            return
        }

        // Initialize the BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(activity)
        binding = BottomSheetFuelSetupPromptBinding.inflate(LayoutInflater.from(activity))
        bottomSheetDialog.setContentView(binding.root)


        // Setup button to configure fuel info
        binding.btnSetupFuelInfo.setOnClickListener {
            onSetupFuelInfoClicked(bottomSheetDialog)
        }

        // Button to use preset values
        binding.btnUsePresets.setOnClickListener {
            onUsePresetsClicked(bottomSheetDialog)
        }

        // Close button (ImageButton) listener
        binding.btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        // Show the BottomSheet
        bottomSheetDialog.show()
    }

    // Handle the "Setup Fuel Info" button click
    private fun onSetupFuelInfoClicked(bottomSheetDialog: BottomSheetDialog) {
        // You can perform any actions or validation here before showing the setup screen
        Toast.makeText(activity, "Setting up fuel information", Toast.LENGTH_SHORT).show()

        // Dismiss the current BottomSheet
        bottomSheetDialog.dismiss()

        // Invoke the FuelManagement class to show the fuel info entry BottomSheet
        val fuelManagement = FuelManagement(activity, travelMode)
        fuelManagement.show {}
    }


    // Handle the "Use Presets" button click
    private fun onUsePresetsClicked(bottomSheetDialog: BottomSheetDialog) {
        val vehicleType = when (travelMode) {
            "Car" -> "Car"
            "TWO_WHEELER" -> "Motorcycle"
            else -> "Car"
        }

        // Apply preset values based on the vehicle type
        applyPresets(vehicleType)

        // Mark the flag as true so the bottom sheet doesn't show again
        setFuelSetupPromptShown()

        // Dismiss the BottomSheet
        bottomSheetDialog.dismiss()
    }

    // Apply presets for the vehicle type
    private fun applyPresets(vehicleType: String) {
        var tankCapacity = 0.0
        var fuelEfficiency = 0.0

        when (vehicleType) {
            "Car" -> {
                tankCapacity = 50.0 // Default tank capacity for Car
                fuelEfficiency = 15.0 // Default fuel efficiency for Car
            }
            "Motorcycle" -> {
                tankCapacity = 15.0 // Default tank capacity for Motorcycle
                fuelEfficiency = 30.0 // Default fuel efficiency for Motorcycle
            }
            else -> {
                Toast.makeText(activity, "Unknown vehicle type", Toast.LENGTH_SHORT).show()
            }
        }

        // Display a message with the applied presets
        Toast.makeText(activity, "$vehicleType preset applied: Tank Capacity = $tankCapacity L, Fuel Efficiency = $fuelEfficiency km/L", Toast.LENGTH_LONG).show()

        // Save the preset values to Firebase
        savePresetValuesToFirebase(vehicleType, tankCapacity, fuelEfficiency)
    }

    // Save the preset values to Firebase
    private fun savePresetValuesToFirebase(vehicleType: String, tankCapacity: Double, fuelEfficiency: Double) {
        val vehicleInfo = mapOf(
            "vehicleType" to vehicleType,
            "tankCapacity" to tankCapacity,
            "fuelEfficiency" to fuelEfficiency
        )

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("VehicleInfo")
                .setValue(vehicleInfo)
                .addOnSuccessListener {
                    Toast.makeText(activity, "Vehicle information saved successfully!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(activity, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(activity, "User not logged in.", Toast.LENGTH_SHORT).show()
        }
    }

    // Method to set the flag in SharedPreferences after interaction
    private fun setFuelSetupPromptShown() {
        val editor = sharedPreferences.edit()
        editor.putBoolean(PREF_KEY_HAS_SHOWN, true)
        editor.apply()  // Save the change asynchronously
    }

    fun hasSetFuelPreferences(): Boolean {
        Log.d("FUEL_STOPS1", "SHARED PREFERENCES: ${
            sharedPreferences.getBoolean(
                PREF_KEY_HAS_SHOWN,
                false
            )
        }")
        return sharedPreferences.getBoolean(PREF_KEY_HAS_SHOWN, false)

    }
}
