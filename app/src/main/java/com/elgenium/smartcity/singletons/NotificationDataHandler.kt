package com.elgenium.smartcity.singletons

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import java.util.Locale

object NotificationDataHandler {
    private const val PREFS_NAME = "user_settings"
    private const val CITY_NAME_KEY = "city_name"

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    fun init(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }

    fun getUserCityName(context: Context, callback: (String?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    val addresses = geocoder.getFromLocation(it.latitude, it.longitude, 1)

                    if (addresses?.isNotEmpty() == true) {
                        var cityName = addresses[0].locality // Get the city name

                        // Remove the word "City" if it's part of the city name
                        cityName = cityName?.replace(" City", "", ignoreCase = true)

                        Log.d("FCM", "User's city: $cityName")
                        callback(cityName) // Return the city name via callback
                    } else {
                        Log.e("FCM", "No address found")
                        callback(null) // Return null if no address found
                    }
                } ?: run {
                    Log.e("FCM", "Location not found")
                    callback(null) // Return null if location is not found
                }
            }
        } else {
            Log.e("FCM", "Location permission not granted")
            callback(null) // Return null if permission is not granted
        }
    }


    fun checkAndSaveCityName(context: Context, cityName: String) {
        // Check SharedPreferences for the saved city name
        val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedCityName = sharedPreferences.getString(CITY_NAME_KEY, null)

        // If the saved city name is different from the current city name, update it
        if (savedCityName != cityName) {
            // Save the new city name in SharedPreferences
            sharedPreferences.edit().putString(CITY_NAME_KEY, cityName).apply()
            Log.d("FCM", "City name saved to SharedPreferences: $cityName")

            // Save the city name in Firebase
            saveCityNameToFirebase(cityName)
        } else {
            Log.d("FCM", "City name is already up-to-date in SharedPreferences: $savedCityName")
        }
    }

    private fun saveCityNameToFirebase(cityName: String) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val database: DatabaseReference = FirebaseDatabase.getInstance().getReference("Users").child(userId).child("NotificationData")

        // Create a map to hold user data
        val userData = mapOf("cityName" to cityName)

        // Save the city name under the user's ID in Firebase
        database.updateChildren(userData).addOnCompleteListener { task ->
            if (task.isSuccessful) {
                Log.d("FCM", "City name saved successfully to Firebase!")
            } else {
                Log.e("FCM", "Failed to save city name to Firebase: ${task.exception?.message}")
            }
        }
    }

    // Method to check if the current city is different from the saved city in SharedPreferences
    fun isCurrentCityDifferent(context: Context, currentCityName: String): Boolean {
        val sharedPreferences: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedCityName = sharedPreferences.getString(CITY_NAME_KEY, null)
        return savedCityName != currentCityName
    }
}
