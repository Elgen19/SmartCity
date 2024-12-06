package com.elgenium.smartcity.intelligence

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import com.elgenium.smartcity.databinding.BottomSheetFuelInfoEntryBinding
import com.elgenium.smartcity.databinding.BottomSheetFuelSetupPromptBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import java.text.DecimalFormat

class FuelSetupManager(private val activity: Activity, private val travelMode: String) {

    private lateinit var bindingPrompt: BottomSheetFuelSetupPromptBinding
    private lateinit var bindingEntry: BottomSheetFuelInfoEntryBinding

    private val PREFS_NAME = "fuel_setup_preferences"
    private val PREF_KEY_HAS_SHOWN = "has_shown_fuel_prompt"

    private val sharedPreferences: SharedPreferences =
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // Show the fuel setup prompt
    fun showFuelSetupPrompt(onPreferencesSaved: () -> Unit) {
        // Check if the bottom sheet has already been shown
        if (sharedPreferences.getBoolean(PREF_KEY_HAS_SHOWN, false)) {
            return // Don't show again if it's already been shown
        }

        // Initialize the BottomSheetDialog for setup prompt
        val bottomSheetDialog = BottomSheetDialog(activity)
        bindingPrompt = BottomSheetFuelSetupPromptBinding.inflate(LayoutInflater.from(activity))
        bottomSheetDialog.setContentView(bindingPrompt.root)

        // Set up button listeners
        bindingPrompt.btnSetupFuelInfo.setOnClickListener {
            onSetupFuelInfoClicked(bottomSheetDialog, onPreferencesSaved)
        }

        bindingPrompt.btnUsePresets.setOnClickListener {
            onUsePresetsClicked(bottomSheetDialog, onPreferencesSaved)
        }

        bindingPrompt.btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    // Handle "Setup Fuel Info" button click
    private fun onSetupFuelInfoClicked(bottomSheetDialog: BottomSheetDialog, onPreferencesSaved: () -> Unit) {
        Toast.makeText(activity, "Setting up fuel information", Toast.LENGTH_SHORT).show()
        bottomSheetDialog.dismiss() // Close the current sheet

        // Now show the fuel info entry sheet
        showFuelInfoEntry(onPreferencesSaved)
    }

    // Handle "Use Presets" button click
    private fun onUsePresetsClicked(bottomSheetDialog: BottomSheetDialog, onPreferencesSaved: () -> Unit) {
        val vehicleType = when (travelMode) {
            "Car" -> "Car"
            "TWO_WHEELER" -> "Motorcycle"
            else -> "Car"
        }

        applyPresets(vehicleType) {
            // Trigger callback after presets are applied
            onPreferencesSaved()
        }
        setFuelSetupPromptShown() // Mark as shown
        bottomSheetDialog.dismiss() // Close the prompt
    }

    // Apply preset values based on vehicle type
    private fun applyPresets(vehicleType: String, onPreferencesSaved: () -> Unit) {
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

        Toast.makeText(activity, "$vehicleType preset applied: Tank Capacity = $tankCapacity L, Fuel Efficiency = $fuelEfficiency km/L", Toast.LENGTH_LONG).show()
        savePresetValuesToFirebase(vehicleType, tankCapacity, fuelEfficiency, onPreferencesSaved)
    }

    // Save preset values to Firebase
    private fun savePresetValuesToFirebase(vehicleType: String, tankCapacity: Double, fuelEfficiency: Double, onPreferencesSaved: () -> Unit) {
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
                    // Trigger callback after the data is saved
                    onPreferencesSaved()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(activity, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(activity, "User not logged in.", Toast.LENGTH_SHORT).show()
        }
    }

    // Show the fuel info entry bottom sheet
    private fun showFuelInfoEntry(onPreferencesSaved: () -> Unit) {
        val entryDialog = BottomSheetDialog(activity)
        bindingEntry = BottomSheetFuelInfoEntryBinding.inflate(LayoutInflater.from(activity))
        entryDialog.setContentView(bindingEntry.root)

        // Set up the vehicle types for the spinner
        val vehicleTypes = listOf("Car", "Motorcycle")
        bindingEntry.vehicleTypeSpinner.adapter = object : ArrayAdapter<String>(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            vehicleTypes
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).setTextColor(Color.BLACK)
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).setTextColor(Color.BLACK)
                return view
            }
        }

        // Setup save button
        bindingEntry.saveVehicleInfoBtn.setOnClickListener {
            saveVehicleInfo(entryDialog, onPreferencesSaved)
        }

        // Setup cancel button
        bindingEntry.btnCancel.setOnClickListener {
            entryDialog.dismiss()
        }

        entryDialog.show()
    }

    // Save the entered vehicle information to Firebase
    private fun saveVehicleInfo(entryDialog: BottomSheetDialog, onPreferencesSaved: () -> Unit) {
        val vehicleType = bindingEntry.vehicleTypeSpinner.selectedItem.toString()
        val tankCapacity = bindingEntry.tankCapacityInput.text.toString().trim()
        val fuelEfficiency = bindingEntry.fuelEfficiencyInput.text.toString().trim()

        if (tankCapacity.isEmpty()) {
            Toast.makeText(activity, "Please enter tank capacity", Toast.LENGTH_SHORT).show()
            return
        }

        if (fuelEfficiency.isEmpty()) {
            Toast.makeText(activity, "Please enter fuel efficiency", Toast.LENGTH_SHORT).show()
            return
        }

        val vehicleInfo = mapOf(
            "vehicleType" to vehicleType,
            "tankCapacity" to tankCapacity.toDouble(),
            "fuelEfficiency" to fuelEfficiency.toDouble()
        )

        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("VehicleInfo")
                .setValue(vehicleInfo)
                .addOnSuccessListener {
                    setFuelSetupPromptShown()
                    Toast.makeText(activity, "Vehicle information saved successfully!", Toast.LENGTH_SHORT).show()
                    entryDialog.dismiss()
                    // Trigger the callback after the data is saved
                    onPreferencesSaved()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(activity, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(activity, "User not logged in.", Toast.LENGTH_SHORT).show()
        }
    }

    // Mark that the fuel setup prompt has been shown
    private fun setFuelSetupPromptShown() {
        val editor = sharedPreferences.edit()
        editor.putBoolean(PREF_KEY_HAS_SHOWN, true)
        editor.apply() // Save asynchronously
    }

    // Check if fuel preferences have been set
    fun hasSetFuelPreferences(): Boolean {
        return sharedPreferences.getBoolean(PREF_KEY_HAS_SHOWN, false)
    }

    fun fetchVehicleDataAndCalculateFuel(
        context: Context,
        totalRouteDistance: Double,
        onCalculationCompleted: (remainingRange: Double,
                                 isRefuelRequired: Boolean,
                                 requiredFuel: Double,
                                 vehicleTypeUse: String,
                                 fuelEfficiencyOfVehicle: String,
                                 vehicleTankCapacity: String,
                                 isThresholdReached: Boolean,
                                 refuelingThresholdVolume: Double,
                                 fuelLevel: String) -> Unit // Callback with more details
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            // Fetching the vehicle data from Firebase
            FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("VehicleInfo")
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        // Retrieve vehicle information (fuel efficiency, tank capacity, etc.)
                        val vehicleType = snapshot.child("vehicleType").getValue(String::class.java) ?: "Car"
                        val tankCapacity = snapshot.child("tankCapacity").getValue(Double::class.java) ?: 50.0
                        val fuelEfficiency = snapshot.child("fuelEfficiency").getValue(Double::class.java) ?: 15.0
                        val fuelLevel  = snapshot.child("fuelLevel").getValue(Double::class.java) ?: tankCapacity
                        val refuelingThreshold  = snapshot.child("refuelingThreshold").getValue(Int::class.java) ?: 0

                        // Step 1: Calculate the refueling threshold volume in liters
                        val refuelingThresholdVolume = (refuelingThreshold.toDouble() / 100) * tankCapacity

                        // Step 2: Calculate the required fuel for the total route distance
                        val requiredFuel = totalRouteDistance / fuelEfficiency

                        // Step 3: Calculate the remaining range (km)
                        val remainingRange = fuelLevel * fuelEfficiency

                        // Step 4: Determine if refueling is required
                        val isRefuelRequired = requiredFuel > fuelLevel

                        val isRefuelingThresholdReached = fuelLevel <= refuelingThresholdVolume

                        // Step 5: Return results through the callback
                        onCalculationCompleted(
                            remainingRange,
                            isRefuelRequired,
                            requiredFuel,
                            vehicleType,
                            fuelEfficiency.toString(),
                            tankCapacity.toString(),
                            isRefuelingThresholdReached,
                            refuelingThresholdVolume,
                            fuelLevel.toString()
                        )

                    } else {
                        // If no vehicle info is found in Firebase
                        Toast.makeText(context, "No vehicle information found. Please set up your vehicle info first.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    // Handle errors, such as no internet connection or Firebase failure
                    Toast.makeText(context, "Error fetching vehicle data: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Handle case where the user is not logged in
            Toast.makeText(context, "User is not logged in. Please log in to retrieve vehicle data.", Toast.LENGTH_SHORT).show()
        }
    }


    fun updateFuelLevelViaDistanceCovered(
        context: Context,
        traveledDistanceInMeters: Double,
        onUpdateCompleted: (Boolean, String) -> Unit // Callback to notify whether the update was successful
    ) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            // Fetch the vehicle data from Firebase
            FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("VehicleInfo")
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        // Retrieve vehicle information (fuel efficiency and current fuel level)
                        val tankCapacity = snapshot.child("tankCapacity").getValue(Double::class.java) ?: 50.0
                        val fuelEfficiency = snapshot.child("fuelEfficiency").getValue(Double::class.java) ?: 15.0
                        var fuelLevel = snapshot.child("fuelLevel").getValue(Double::class.java) ?: tankCapacity

                        Log.i("FUEL_SETUP", "FUEL SETUP: $fuelLevel")
                        Log.i("FUEL_SETUP", "TRAVEL DISTANCE: $traveledDistanceInMeters")

                        // Step 1: Calculate the fuel consumed based on the traveled distance
                        val traveledDistanceInKm = traveledDistanceInMeters / 1000.0 // Convert to kilometers
                        val fuelConsumed = traveledDistanceInKm / fuelEfficiency

                        // Step 2: Update the fuel level by subtracting the consumed fuel
                        fuelLevel -= fuelConsumed

                        // Step 3: Ensure the fuel level doesn't go below zero
                        if (fuelLevel < 0) {
                            fuelLevel = 0.0
                        }

                        // Round to two decimal places using DecimalFormat
                        val decimalFormat = DecimalFormat("#.##")
                        fuelLevel = decimalFormat.format(fuelLevel).toDouble()

                        Log.i("FUEL_SETUP", "FUEL LEFT: $fuelLevel")

                        // Step 4: Update the fuelLevel in Firebase
                        FirebaseDatabase.getInstance().getReference("Users")
                            .child(userId)
                            .child("VehicleInfo")
                            .child("fuelLevel")
                            .setValue(fuelLevel)
                            .addOnSuccessListener {
                                // Callback with success
                                onUpdateCompleted(true, "$fuelLevel liters")
                            }
                            .addOnFailureListener { exception ->
                                // Callback with failure message
                                onUpdateCompleted(false, "Failed to update fuel level: ${exception.message}")
                            }

                    } else {
                        // Vehicle info not found in Firebase
                        onUpdateCompleted(false, "No vehicle found")
                    }
                }
                .addOnFailureListener { exception ->
                    // Handle errors, such as no internet connection or Firebase failure
                    onUpdateCompleted(false, "Error fetching vehicle data: ${exception.message}")
                }
        } else {
            // Handle case where the user is not logged in
            onUpdateCompleted(false, "User is not logged in. Please log in to update fuel data.")
        }
    }


    fun updateFuelLevelAndRefuelingThreshold(context: Context, newFuelLevel: Double, refuelingPercentage: Int) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid

        if (userId != null) {
            // Fetch the vehicle data from Firebase
            FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("VehicleInfo")
                .get()
                .addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        // Step 1: Update the fuelLevel and refuelingPercentage in Firebase
                        val userVehicleRef = FirebaseDatabase.getInstance().getReference("Users")
                            .child(userId)
                            .child("VehicleInfo")

                        // Update both fuelLevel and refuelingPercentage
                        userVehicleRef.child("fuelLevel").setValue(newFuelLevel)
                            .addOnSuccessListener {
                                // Handle success for fuel level update
                                Toast.makeText(context, "Fuel level updated successfully.", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { exception ->
                                // Handle failure for fuel level update
                                Toast.makeText(context, "Failed to update fuel level: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }

                        userVehicleRef.child("refuelingThreshold").setValue(refuelingPercentage)
                            .addOnSuccessListener {
                                // Handle success for refueling threshold update
                                Toast.makeText(context, "Refueling threshold updated successfully.", Toast.LENGTH_SHORT).show()
                            }
                            .addOnFailureListener { exception ->
                                // Handle failure for refueling threshold update
                                Toast.makeText(context, "Failed to update refueling threshold: ${exception.message}", Toast.LENGTH_SHORT).show()
                            }

                    } else {
                        // Vehicle info not found in Firebase
                        Toast.makeText(context, "No vehicle information found.", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    // Handle errors, such as no internet connection or Firebase failure
                    Toast.makeText(context, "Error fetching vehicle data: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            // Handle case where the user is not logged in
            Toast.makeText(context, "User is not logged in. Please log in to update fuel data.", Toast.LENGTH_SHORT).show()
        }
    }






}
