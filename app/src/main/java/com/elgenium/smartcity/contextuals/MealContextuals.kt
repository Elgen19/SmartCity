package com.elgenium.smartcity.contextuals

import android.Manifest
import android.animation.ValueAnimator
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.BottomSheetPlaceOpennowBinding
import com.elgenium.smartcity.speech.TextToSpeechHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MealContextuals(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences("MealTimePrefs", Context.MODE_PRIVATE)

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val textToSpeechHelper = TextToSpeechHelper()

    init {
        textToSpeechHelper.initializeTTS(context)
    }

    val mealTimePlaceMappings = mapOf(
        "breakfast" to listOf(
            "bakery",
            "breakfast_restaurant",
            "brunch_restaurant",
            "cafe",
            "coffee_shop"
        ),
        "lunch" to listOf(
            "cafe",
            "coffee_shop",
            "convenience store",
            "fast_food_restaurant",
            "restaurant",
            "meal_takeaway"
        ),
        "snack" to listOf(
            "bakery",
            "cafe",
            "coffee_shop",
            "ice_cream_shop",
            "meal_takeaway",
            "convenience store",
            "meal_delivery"
        ),
        "dinner" to listOf(
            "american restaurant",
            "korean restaurant",
            "outdoor dining",
            "restaurant",
            "barbecue",
            "fast food",
            "meal_delivery",
        ),
        "late-night" to listOf(
            "bar",
            "fast_food_restaurant",
            "meal_delivery",
            "restobar"
        )
    )

    fun showPlaceDialogIfNeeded(onProceedClicked: () -> Unit) {
        val currentMealTime = getMealTime()
        val currentDate = getCurrentDate()

        val lastShownMealTime = sharedPreferences.getString("lastShownMealTime", null)
        val lastShownDate = sharedPreferences.getString("lastShownDate", null)

        Log.d("MealContextuals", "Checking if dialog should be shown for meal time: $currentMealTime on $currentDate")

        if (lastShownMealTime != currentMealTime || lastShownDate != currentDate) {
            Log.d("MealContextuals", "Conditions met to show dialog for $currentMealTime")

            val dialog = showPlaceDialog(onProceedClicked)

            // Automatically dismiss after 5 seconds
            Handler(Looper.getMainLooper()).postDelayed({
                if (dialog.isShowing) {
                    dialog.dismiss()
                    Log.d("MealContextuals", "Dialog dismissed automatically after 5 seconds.")
                }
            }, 5000)



            with(sharedPreferences.edit()) {
                putString("lastShownMealTime", currentMealTime)
                putString("lastShownDate", currentDate)
                apply()
            }

            Log.d("MealContextuals", "Dialog display status updated in SharedPreferences")
        } else {
            Log.d("MealContextuals", "Dialog already shown for this meal time today; not displaying again.")
        }
    }

    private fun showPlaceDialog(onProceedClicked: () -> Unit): AlertDialog {
        val binding = BottomSheetPlaceOpennowBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .setCancelable(true)
            .create()

        val mealTime = getMealTime()

        val progressAnimator = ValueAnimator.ofInt(0, 100)
        progressAnimator.duration = 5000 // 5 seconds
        progressAnimator.addUpdateListener { animator ->
            binding.progressBar.progress = animator.animatedValue as Int
        }
        progressAnimator.start()

        binding.lottieAnimation.setAnimation(R.raw.food)
        binding.textViewTitle.text = "Have you already taken your $mealTime"
        binding.textViewBody.text = "Take a look at these places you may like for your $mealTime"
        textToSpeechHelper.speakResponse("Have you already taken your $mealTime. Take a look at these places you may like for your $mealTime.")

        Log.d("MealContextuals", "Displaying place dialog with binding.")

        binding.buttonCancel.setOnClickListener {
            Log.d("MealContextuals", "Cancel button clicked.")
            dialog.dismiss()
        }

        binding.buttonProceed.setOnClickListener {
            Log.d("MealContextuals", "Proceed button clicked.")
            textToSpeechHelper.speakResponse("Preparing to plot meal places.")
            dialog.dismiss()
            onProceedClicked()  // Execute the callback function
        }

        dialog.show()
        return dialog
    }


    fun getMealTime(): String {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (currentHour) {
            in 5..11 -> "breakfast"
            in 12..13 -> "lunch"
            in 14..17 -> "snack"
            in 18..22 -> "dinner"
            else -> "late-night"
        }.also {
            Log.d("MealContextuals", "Current meal time determined as $it")
        }
    }

    private fun getCurrentDate(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val currentDate = dateFormat.format(Date())
        Log.d("MealContextuals", "Current date is $currentDate")
        return currentDate
    }

    fun performTextSearch(
        placesClient: PlacesClient,
        currentPlaceTypes: List<String>,
        context: Context,
        callback: (List<Place>) -> Unit
    ) {
        // Get the current location first
        getCurrentLocation(context) { currentLocation ->
            // Ensure current location is available
            currentLocation?.let {
                Log.e("MealContextuals", "Current location: $currentLocation")
                val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                val locationBias = CircularBounds.newInstance(currentLatLng, 500.0) // 500 meters radius

                val placeFields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.ADDRESS,
                    Place.Field.TYPES,
                    Place.Field.RATING,
                    Place.Field.USER_RATINGS_TOTAL,
                    Place.Field.LAT_LNG,
                    Place.Field.PHOTO_METADATAS
                )

                val placesList = mutableListOf<Place>()
                val searchCount = currentPlaceTypes.size
                var completedSearches = 0

                Log.e("MealContextuals", "PLACE TYPE COUNT: $searchCount")

                currentPlaceTypes.forEach { placeType ->
                    val query = "$placeType near me"
                    Log.e("MealContextuals", "Search query for places: $query")

                    val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                        .setMaxResultCount(1)
                        .setLocationBias(locationBias)
                        .setOpenNow(true)
                        .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                        .build()

                    // Perform the search using the PlacesClient
                    placesClient.searchByText(searchByTextRequest)
                        .addOnSuccessListener { response ->
                            val places = response.places
                            Log.e("MealContextuals", "NUMBER OF PLACES: ${places.size}")
                            places.forEach { place ->
                                Log.e("MealContextuals", "NAME OF PLACE: ${place.name}")
                                placesList.add(place)
                            }
                        }
                        .addOnCompleteListener {
                            completedSearches++
                            if (completedSearches == searchCount) {
                                // All searches are complete, invoke the callback with the results
                                callback(placesList)
                            }
                        }
                        .addOnFailureListener { exception ->
                            Log.e("MealContextuals", "Error during place search: ${exception.message}")
                            completedSearches++
                            if (completedSearches == searchCount) {
                                callback(placesList)
                            }
                        }
                }

                // If there are no place types to search, call the callback immediately
                if (searchCount == 0) {
                    callback(placesList)
                }

            } ?: run {
                Log.e("MealContextuals", "Current location is not available.")
                callback(emptyList()) // Return an empty list if the location is not available
            }
        }
    }


    private fun getCurrentLocation(context: Context, callback: (LatLng?) -> Unit) {
        // Check if the location permission is granted
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is granted, get the location
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        // Convert Location to LatLng
                        val latLng = LatLng(it.latitude, it.longitude)
                        callback(latLng) // Return the location through the callback
                    } ?: run {
                        Log.e("MealContextuals", "Location is null.")
                        callback(null) // Handle case where location is null
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("MealContextuals", "Failed to get location", exception)
                    callback(null) // Return null on failure
                }
        } else {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                context as Activity, // Ensure that context is an Activity
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

}
