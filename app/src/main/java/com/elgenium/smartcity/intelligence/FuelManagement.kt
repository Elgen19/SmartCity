package com.elgenium.smartcity.intelligence

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import com.elgenium.smartcity.databinding.BottomSheetFuelInfoEntryBinding
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

class FuelManagement(private val activity: Activity, private val travelMode: String) {

    private lateinit var binding: BottomSheetFuelInfoEntryBinding

    private val PREFS_NAME = "fuel_setup_preferences"
    private val PREF_KEY_HAS_SHOWN = "has_shown_fuel_prompt"

    // SharedPreferences instance
    private val sharedPreferences: SharedPreferences =
        activity.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun show(onPreferencesSaved: () -> Unit) {
        // Initialize the BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(activity)
        binding = BottomSheetFuelInfoEntryBinding.inflate(LayoutInflater.from(activity))
        bottomSheetDialog.setContentView(binding.root)

        val vehicleTypes = listOf("Car", "Motorcycle")

        binding.vehicleTypeSpinner.adapter = object : ArrayAdapter<String>(
            activity,
            android.R.layout.simple_spinner_dropdown_item,
            vehicleTypes
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent)
                (view as TextView).setTextColor(Color.BLACK) // Ensure black text
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent)
                (view as TextView).setTextColor(Color.BLACK) // Ensure black dropdown text
                return view
            }
        }

        // Setup save button
        binding.saveVehicleInfoBtn.setOnClickListener {
            saveVehicleInfo(bottomSheetDialog, onPreferencesSaved)
        }

        // Setup cancel button
        binding.btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        // Show the BottomSheet
        bottomSheetDialog.show()
    }

    private fun saveVehicleInfo(bottomSheetDialog: BottomSheetDialog, onPreferencesSaved: () -> Unit) {
        val vehicleType = binding.vehicleTypeSpinner.selectedItem.toString()
        val tankCapacity = binding.tankCapacityInput.text.toString().trim()
        val fuelEfficiency = binding.fuelEfficiencyInput.text.toString().trim()

        // Validate the tank capacity input
        if (tankCapacity.isEmpty()) {
            Toast.makeText(activity, "Please enter tank capacity", Toast.LENGTH_SHORT).show()
            return // Exit the method early if validation fails
        }

        // Validate the fuel efficiency input
        if (fuelEfficiency.isEmpty()) {
            Toast.makeText(activity, "Please enter fuel efficiency", Toast.LENGTH_SHORT).show()
            return // Exit the method early if validation fails
        }

        val vehicleInfo = mapOf(
            "vehicleType" to vehicleType,
            "tankCapacity" to tankCapacity.toDouble(),
            "fuelEfficiency" to fuelEfficiency.toDouble()
        )

        // Save to Firebase Realtime Database
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            FirebaseDatabase.getInstance().getReference("Users")
                .child(userId)
                .child("VehicleInfo")
                .setValue(vehicleInfo)
                .addOnSuccessListener {
                    // Vehicle information saved successfully
                    Toast.makeText(activity, "Vehicle information saved successfully!", Toast.LENGTH_SHORT).show()

                    setFuelSetupPromptShown()

                    // Trigger the callback after saving preferences
                    onPreferencesSaved()

                    // Dismiss the BottomSheet after saving the info
                    bottomSheetDialog.dismiss()
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(activity, "Error: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
        } else {
            Toast.makeText(activity, "User not logged in.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setFuelSetupPromptShown() {
        val editor = sharedPreferences.edit()
        editor.putBoolean(PREF_KEY_HAS_SHOWN, true)
        editor.apply()  // Save the change asynchronously
    }

    fun hasSetFuelPreferences(): Boolean {
        return sharedPreferences.getBoolean(PREF_KEY_HAS_SHOWN, false)
    }
}

