package com.elgenium.smartcity.contextuals

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.view.LayoutInflater
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.BuildConfig
import com.elgenium.smartcity.PlacesActivity
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.BottomSheetContextualRecommendationBinding
import com.elgenium.smartcity.models.RecommendedPlace
import com.elgenium.smartcity.network.GeocodingService
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.network_reponses.GeocodingResponse
import com.elgenium.smartcity.network_reponses.PlaceDistanceResponse
import com.elgenium.smartcity.recyclerview_adapter.RecommendedPlaceAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Calendar

class MealPlaceRecommendationManager(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    // Define mappings of meal times to relevant place types
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
            "meal_delivery"
        ),
        "dinner" to listOf(
           "restaurant",
            "fast_food_restaurant",
            "meal_delivery",
            "meal_takeaway"
        ),
        "late-night" to listOf(
            "bar",
            "fast_food_restaurant",
            "meal_delivery",
            "meal_takeaway"
        )
    )

    // Determine meal time based on the current time of day
    fun getMealTime(): String {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (currentHour) {
            in 5..11 -> "breakfast"
            in 12..13 -> "lunch"
            in 14..17 -> "snack"
            in 18..22 -> "dinner"
            else -> "late-night"
        }.also {
            Log.e("MealPlaceRecommendationManager", "Current meal time is $it")
        }
    }

    // Perform a text search using PlacesClient for recommended meal places
    fun performTextSearch(
        placesClient: PlacesClient,
        currentPlaceTypes: List<String>,
        context: Context,
        recyclerView: RecyclerView,
        titleTextView: TextView,
        supportTextView: TextView,
        isForCarousel: Boolean,
        callback: (List<Place>) -> Unit) {
        // Get the current location first
        getCurrentLocation(context) { currentLocation ->
            // Ensure current location is available
            currentLocation?.let {
                Log.e("MealPlaceRecommendationManager", "Current location: $currentLocation")
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

                getCityName(currentLocation) { cityName ->
                    Log.e("MealPlaceRecommendationManager", "City Name: $cityName")
                    // Now you can use the cityName to modify your search query
                    val query = "$cityName ${currentPlaceTypes.joinToString(" OR ")}"

                    Log.e("MealPlaceRecommendationManager", "Search query for places: $query")

                    val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                        .setMaxResultCount(10)
                        .setLocationBias(locationBias)
                        .setOpenNow(true)
                        .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                        .build()
                    // Perform the search using the PlacesClient
                    placesClient.searchByText(searchByTextRequest)
                        .addOnSuccessListener { response ->
                            val places = response.places
                            val recommendedPlacesList = mutableListOf<RecommendedPlace>()
                            var distancesCalculated = 0

                            places.forEach { place ->
                                checkPlaceDistance(currentLatLng, place) { calculatedDistanceString ->
                                    // Remove any non-numeric characters, such as "km", and convert the remaining string to a double
                                    val distanceNumericString = calculatedDistanceString.replace("[^\\d.]".toRegex(), "") // Removes all non-digit characters except the decimal point

                                    // Parse the numeric string into a double value
                                    val distance = distanceNumericString.toDoubleOrNull() ?: 0.0

                                    // Create RecommendedPlace instance
                                    val recommendedPlace = RecommendedPlace(
                                        placeId = place.id ?: "NO PLACE ID",
                                        name = place.name ?: "Unknown Name",
                                        address = place.address ?: "Unknown Address",
                                        placeTypes = place.placeTypes ?: emptyList(),
                                        rating = place.rating ?: 0.0,
                                        numReviews = place.userRatingsTotal ?: 0,
                                        distance = distance,
                                        distanceString = calculatedDistanceString, // Keep the original distance string
                                        photoMetadata = place.photoMetadatas?.firstOrNull() // Take the first photo if available
                                    )

                                    // Add the recommended place to the list
                                    recommendedPlacesList.add(recommendedPlace)


                                    distancesCalculated++

                                    // Check if all distances have been calculated
                                    if (distancesCalculated == places.size) {
                                        Log.e("MealPlaceRecommendationManager", "places: $recommendedPlacesList")

                                        if (isForCarousel){
                                            setupRecommendationUI(context, placesClient, recommendedPlacesList, recyclerView, titleTextView, supportTextView)
                                        } else {
                                            showMealRecommendationBottomSheet(context, recommendedPlacesList, placesClient) // Show the bottom sheet with all places
                                        }
                                    }

                                }
                            }





                            // Log the number of places retrieved
                            Log.e("MealPlaceRecommendationManager", "Number of places found: ${places.size}")
                            places.forEach { place ->
                                Log.e("MealPlaceRecommendationManager", "Place: ${place.name}, Address: ${place.address}, ID: ${place.id}, LatLng: ${place.latLng}, Place types: ${place.placeTypes}")
                                checkPlaceDistance(currentLatLng, place) { distance ->
                                    // Handle the distance returned
                                    Log.e("MealPlaceRecommendationManager", "Distance to ${place.name}: $distance")
                                }
                            }
                            callback(places) // Return the found places through the callback
                        }
                        .addOnFailureListener { exception ->
                            Log.e("MealPlaceRecommendationManager", "Error during text search: ${exception.message}")
                            callback(emptyList()) // Return an empty list on error
                        }
                }


            } ?: run {
                Log.e("MealPlaceRecommendationManager", "Current location is not available.")
                callback(emptyList()) // Return an empty list if the location is not available
            }
        }
    }

    fun setupRecommendationUI(
        context: Context,
        placesClient: PlacesClient,
        placesList: List<RecommendedPlace>,
        recyclerView: RecyclerView,
        titleTextView: TextView,
        supportTextView: TextView,
    ) {
        // Set the title text
        val (title, text) = getMealRecommendations()
        titleTextView.text = title
        supportTextView.text = text


        val adapter = RecommendedPlaceAdapter(
            placesList,  // Pass the list of places
            true,
            placesClient,  // Pass the PlacesClient
            onPlaceClick = { place ->
                Log.e("MealRecommendation", "Place clicked: ${place.name}")
                val intent = Intent(context, PlacesActivity::class.java)

                intent.putExtra("DASHBOARD_RECOMMENDED_PLACE_ID", place.placeId)
                context.startActivity(intent)
            }
        )
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)


    }


    private fun showMealRecommendationBottomSheet(context: Context, placesList: List<RecommendedPlace>, placesClient: PlacesClient) {
        // Create a new instance of BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(context)

        // Inflate the layout for the bottom sheet
        val bottomSheetView = LayoutInflater.from(context).inflate(
            R.layout.bottom_sheet_contextual_recommendation,
            null
        )

        // Set up view binding for the bottom sheet layout
        val binding = BottomSheetContextualRecommendationBinding.bind(bottomSheetView)

        val (title, recommendation) = getMealRecommendations()
        binding.textViewRecommendationTitle.text = title
        binding.textViewRecommendationDescription.text = recommendation

        // Set up the RecyclerView with the adapter
        val adapter = RecommendedPlaceAdapter(
            placesList,  // Pass the list of places
            false,
            placesClient,  // Pass the PlacesClient
            onPlaceClick = { place ->
                Log.e("MealRecommendation", "Place clicked: ${place.name}")
                val intent = Intent(context, PlacesActivity::class.java)

                intent.putExtra("DASHBOARD_RECOMMENDED_PLACE_ID", place.placeId)
                context.startActivity(intent)
            }
        )
        binding.recyclerViewMealRecommendations.adapter = adapter
        binding.recyclerViewMealRecommendations.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

        // Set up the close button (ImageButton)
        binding.buttonClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }


        bottomSheetDialog.setContentView(bottomSheetView)
        // Show the bottom sheet dialog
        bottomSheetDialog.show()
    }

    // Get current location
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
                        Log.e("MealPlaceRecommendationManager", "Location is null.")
                        callback(null) // Handle case where location is null
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("MealPlaceRecommendationManager", "Failed to get location", exception)
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

    private fun checkPlaceDistance(
        currentLocation: LatLng?, // User's current location
        place: Place, // Place object
        distanceCallback: (String) -> Unit // Callback to return the distance or an error
    ) {
        if (currentLocation != null) {
            // Get the place's location from the Place object
            val placeLatLng = place.latLng

            // Check if placeLatLng is provided
            if (placeLatLng != null) {
                // Build the API request URL parameters.
                val apiKey = BuildConfig.MAPS_API_KEY
                val origin = "${currentLocation.latitude},${currentLocation.longitude}"
                val destination = "${placeLatLng.latitude},${placeLatLng.longitude}"

                // Create a Retrofit instance for API requests.
                val retrofit = Retrofit.Builder()
                    .baseUrl("https://maps.googleapis.com/")
                    .addConverterFactory(GsonConverterFactory.create())
                    .build()

                val api = retrofit.create(PlaceDistanceService::class.java)

                // Enqueue the API request to get directions and distance.
                api.getDirections(origin, destination, apiKey)
                    .enqueue(object : Callback<PlaceDistanceResponse> {
                        override fun onResponse(
                            call: Call<PlaceDistanceResponse>,
                            response: Response<PlaceDistanceResponse>
                        ) {
                            // Handle the response from the API.
                            if (response.isSuccessful && response.body() != null) {
                                val directionsResponse = response.body()
                                if (directionsResponse?.routes?.isNotEmpty() == true) {
                                    // Extract the distance from the response.
                                    val distance = directionsResponse.routes[0].legs[0].distance.text

                                    // Return distance through the callback
                                    distanceCallback(distance)
                                } else {
                                    // Return "Distance not available" if no routes are found.
                                    distanceCallback("Distance not available")
                                }
                            } else {
                                // Return "Error calculating distance" if the API response fails.
                                distanceCallback("Error calculating distance")
                            }
                        }

                        override fun onFailure(call: Call<PlaceDistanceResponse>, t: Throwable) {
                            // Log the error and return "Error calculating distance"
                            Log.e("RetrofitError", "Error fetching directions: ${t.message}")
                            distanceCallback("Error calculating distance")
                        }
                    })
            } else {
                // Return "Place location not available" if place location is null.
                distanceCallback("Place location not available")
            }
        } else {
            // Return "User location not available" if the user's current location is null.
            distanceCallback("User location not available")
        }
    }

    private fun getCityName(currentLocation: LatLng, distanceCallback: (String) -> Unit) {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        val apiKey = BuildConfig.MAPS_API_KEY
        val latLng = "${currentLocation.latitude},${currentLocation.longitude}"

        val geocodingService = retrofit.create(GeocodingService::class.java)

        geocodingService.getCityName(latLng, apiKey).enqueue(object : Callback<GeocodingResponse> {
            override fun onResponse(
                call: Call<GeocodingResponse>,
                response: Response<GeocodingResponse>
            ) {
                if (response.isSuccessful && response.body() != null) {
                    val results = response.body()!!.results
                    if (results.isNotEmpty()) {
                        // Loop through address components to find the city name
                        for (component in results[0].address_components) {
                            if (component.types.contains("locality")) { // Locality indicates city
                                distanceCallback(component.long_name)
                                return
                            }
                        }
                    }
                    distanceCallback("City not found")
                } else {
                    distanceCallback("Error fetching city name")
                }
            }

            override fun onFailure(call: Call<GeocodingResponse>, t: Throwable) {
                Log.e("GeocodingError", "Error fetching city name: ${t.message}")
                distanceCallback("Error fetching city name")
            }
        })
    }

    private fun getMealRecommendations(): Pair<String, String> {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

        val recommendationText = when (currentHour) {
            in 5..11 -> "Explore these breakfast spots."
            in 12..13 -> "Discover these lunch options."
            in 14..17 -> "Check out these snack suggestions."
            in 18..22 -> "Consider these dinner recommendations."
            else -> "Explore these late-night dining choices."
        }

        val title = when (currentHour) {
            in 5..11 -> "Breakfast Recommendations"
            in 12..13 -> "Lunch Recommendations"
            in 14..17 -> "Snack Recommendations"
            in 18..22 -> "Dinner Recommendations"
            else -> "Late-Night Dining Recommendations"
        }

        return Pair(title, recommendationText)
    }




}
