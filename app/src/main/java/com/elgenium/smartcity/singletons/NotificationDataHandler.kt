package com.elgenium.smartcity.singletons

import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.BuildConfig
import com.elgenium.smartcity.network.GeocodingService
import com.elgenium.smartcity.network_reponses.AddressComponent
import com.elgenium.smartcity.network_reponses.GeocodingResponse
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object NotificationDataHandler {
    private const val PREFS_NAME = "user_settings"
    private const val CITY_NAME_KEY = "city_name"

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    fun init(context: Context) {
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    }

    private val geocodingService: GeocodingService by lazy {
        Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeocodingService::class.java)
    }

    fun getUserCityName(context: Context, callback: (String?) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                location?.let {
                    val latLng = "${it.latitude},${it.longitude}"

                    // Use GeocodingService to call the reverse geocoding API
                    geocodingService.getCityName(latLng, BuildConfig.MAPS_API_KEY).enqueue(object :
                        Callback<GeocodingResponse> {
                        override fun onResponse(call: Call<GeocodingResponse>, response: Response<GeocodingResponse>) {
                            if (response.isSuccessful && response.body() != null) {
                                val results = response.body()?.results
                                if (!results.isNullOrEmpty()) {
                                    val cityName = extractCityName(results[0].address_components)
                                    Log.d("FCM", "User's city: $cityName")
                                    callback(cityName) // Return the city name via callback
                                } else {
                                    Log.e("FCM", "No address found")
                                    callback(null) // Return null if no address found
                                }
                            } else {
                                Log.e("FCM", "Error in response: ${response.message()}")
                                callback(null) // Return null if there was an error
                            }
                        }

                        override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {
                            Log.e("FCM", "Error fetching city name: ${t.message}")
                            callback(null) // Return null if there was an error
                        }
                    })
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

    private fun extractCityName(addressComponents: List<AddressComponent>): String? {
        // Iterate through address components to find the city name
        for (component in addressComponents) {
            if (component.types.contains("locality") || component.types.contains("administrative_area_level_1")) {
                return component.long_name // Return the first matching name
            }
        }
        return null // Return null if no city name is found
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
