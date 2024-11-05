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
import com.google.firebase.database.FirebaseDatabase
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PopularPlaceRecommendationManager(context: Context, userIdConstructor: String) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val userId = userIdConstructor

    private fun mapUserPreferencesToPlaceTypes(preferences: List<String>): List<String> {
        val mappedTypes = mutableListOf<String>()

        preferences.forEach { preference ->
            when (preference) {
                "Cafes and restaurants" -> mappedTypes.addAll(listOf(
                    "bar", "restaurant", "cafe", "coffee_shop", "bakery", "fast_food_restaurant"
                ))
                "Parks and outdoor spaces" -> mappedTypes.addAll(listOf(
                    "park",  "tourist_attraction", "amusement_park"
                ))
                "Cultural and historical sites" -> mappedTypes.addAll(listOf(
                    "museum", "art_gallery", "historical_landmark"
                ))
                "Shopping areas and malls" -> mappedTypes.addAll(listOf(
                    "shopping_mall", "super_market", "market",
                ))
                "Workspaces or study areas" -> mappedTypes.addAll(listOf(
                    "library", "coworking_space", "coffee_shop"
                ))
                "Entertainment venues (theaters, clubs)" -> mappedTypes.addAll(listOf(
                    "movie_theater", "night_club", "amusement_center", "casino"
                ))
            }
        }

        return mappedTypes.distinct() // Ensure unique place types
    }



    private fun calculatePopularityScore(place: Place): Double {
        val ratingWeight = 0.7 // Weight for the rating
        val reviewWeight = 0.3 // Weight for the number of reviews

        // Normalize the rating (e.g., out of 5) and reviews (scale to a maximum of 1000 for example)
        val normalizedRating = place.rating ?: 0.0 // If rating is null, treat as 0
        val normalizedReviews = minOf(place.userRatingsTotal ?: 0, 1000) / 1000.0 // Cap reviews at 1000 for normalization

        return (normalizedRating * ratingWeight) + (normalizedReviews * reviewWeight)
    }

    private fun rankPlacesByPopularity(places: List<Place>): List<Place> {
        // Use a set to track place IDs to avoid duplicates
        val uniquePlaces = mutableSetOf<String>()
        val filteredAndUniquePlaces = places.filter { place ->
            val placeId = place.id ?: "NO PLACE ID" // Assuming each Place object has a unique `id`

            // Check if the place has non-null ratings and user ratings total
            val hasValidRating = place.rating != null
            val hasValidReviews = place.userRatingsTotal != null

            // Only include the place if it hasn't been added before
            if (!uniquePlaces.contains(placeId) && hasValidRating && hasValidReviews) {
                uniquePlaces.add(placeId) // Add the place ID to the set
                true // Include this place
            } else {
                Log.e("PopularPlaceRecommendationManager", "DUPLICATE FOUND: NAME: ${place.name}, RATING: ${place.rating}, NUMBER OF REVIEWS: ${place.userRatingsTotal}")
                false // Exclude this place as it's a duplicate

            }
        }

        // Sort the filtered, unique places by their popularity score
        return filteredAndUniquePlaces.sortedByDescending { calculatePopularityScore(it) }
    }



    fun performTextSearch(
        placesClient: PlacesClient,
        userPreferences: List<String>, // Pass user preferences here
        context: Context,
        recyclerView: RecyclerView,
        titleTextView: TextView,
        supportText: TextView,
        isForCarousel: Boolean,
        callback: (List<Place>) -> Unit
    ) {
        val currentPlaceTypes = mapUserPreferencesToPlaceTypes(userPreferences) // Map preferences to place types

        // Get the current location first
        getCurrentLocation(context) { currentLocation ->
            // Ensure current location is available
            currentLocation?.let {
                Log.e("PopularPlaceRecommendationManager", "Current location: $currentLocation")
                val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                val locationBias = CircularBounds.newInstance(currentLatLng, 1000.0) // 1000 meters radius

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
                    Log.e("PopularPlaceRecommendationManager", "City Name: $cityName")
                    val allFilteredPlaces = mutableListOf<Place>()
                    var completedQueries = 0

                    currentPlaceTypes.forEach { placeType ->
                        val query = "$cityName $placeType"
                        Log.e("PopularPlaceRecommendationManager", "Search query for places: $query")

                        val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                            .setMaxResultCount(1)
                            .setLocationBias(locationBias)
                            .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                            .build()

                        // Perform the search using the PlacesClient
                        placesClient.searchByText(searchByTextRequest)
                            .addOnSuccessListener { response ->
                                val places = response.places

                                // Log the number of places retrieved
                                Log.e("PopularPlaceRecommendationManager", "Number of places found: ${places.size}")
                                places.forEach { place ->
                                    Log.e("PopularPlaceRecommendationManager", "Place: ${place.name}, Address: ${place.address}, ID: ${place.id}, Number of Reviews: ${place.userRatingsTotal}, Place rating: ${place.rating}")
                                }

                                // Rank places by popularity using the new scoring method
                                val rankedPlaces = rankPlacesByPopularity(places)

                                // Select top 5 from the ranked places
                                val top5Places = rankedPlaces.take(3)
                                allFilteredPlaces.addAll(top5Places)

                                // Check if all queries are completed
                                completedQueries++
                                if (completedQueries == currentPlaceTypes.size) {
                                    // All queries completed, return the combined places
                                    callback(allFilteredPlaces)

                                    val recommendedPlacesList = mutableListOf<RecommendedPlace>()
                                    var distancesCalculated = 0
                                    allFilteredPlaces.shuffle()
                                    val distinctList = allFilteredPlaces.distinctBy { it.name }

                                    distinctList.forEach { filters ->
                                        Log.e("PopularPlaceRecommendationManager", "NAME: ${filters.name}, RATING: ${filters.rating}, NUMBER OF REVIEWS: ${filters.userRatingsTotal}")
                                        checkPlaceDistance(currentLatLng, filters) { calculatedDistanceString ->
                                            // Remove any non-numeric characters, such as "km", and convert the remaining string to a double
                                            val distanceNumericString = calculatedDistanceString.replace("[^\\d.]".toRegex(), "") // Removes all non-digit characters except the decimal point

                                            // Parse the numeric string into a double value
                                            val distance = distanceNumericString.toDoubleOrNull() ?: 0.0

                                            // Create RecommendedPlace instance
                                            val recommendedPlace = RecommendedPlace(
                                                placeId = filters.id ?: "NO PLACE ID",
                                                name = filters.name ?: "Unknown Name",
                                                address = filters.address ?: "Unknown Address",
                                                placeTypes = filters.placeTypes ?: emptyList(),
                                                rating = filters.rating ?: 0.0,
                                                numReviews = filters.userRatingsTotal ?: 0,
                                                distance = distance,
                                                distanceString = calculatedDistanceString, // Keep the original distance string
                                                photoMetadata = filters.photoMetadatas?.firstOrNull() // Take the first photo if available
                                            )

                                            // Add the recommended place to the list
                                            recommendedPlacesList.add(recommendedPlace)


                                            distancesCalculated++

                                            // Check if all distances have been calculated
                                            if (distancesCalculated == places.size) {
                                                Log.e("MealPlaceRecommendationManager", "places: $recommendedPlacesList")

                                                if (isForCarousel){
                                                    setupRecommendationUI(context, placesClient, recommendedPlacesList, recyclerView, titleTextView, supportText)
                                                } else {
                                                    showMealRecommendationBottomSheet(context, recommendedPlacesList, placesClient) // Show the bottom sheet with all places
                                                }
                                            }

                                        }
                                    }

                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("PopularPlaceRecommendationManager", "Error during text search: ${exception.message}")

                                // Even on failure, we check if all queries are completed
                                completedQueries++
                                if (completedQueries == currentPlaceTypes.size) {
                                    callback(allFilteredPlaces)
                                }
                            }
                    }
                }
            } ?: run {
                Log.e("PopularPlaceRecommendationManager", "Current location is not available.")
                callback(emptyList()) // Return an empty list if the location is not available
            }
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

        binding.textViewRecommendationTitle.text = "Top Rated Places Just for You"
        binding.textViewRecommendationDescription.text = "Enjoy your time at these highly recommended venues, each renowned for their quality and customer satisfaction."

        // Set up the RecyclerView with the adapter
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

    private fun setupRecommendationUI(
        context: Context,
        placesClient: PlacesClient,
        placesList: List<RecommendedPlace>,
        recyclerView: RecyclerView,
        titleTextView: TextView,
        supportText: TextView
    ) {
        // Set the title text
        titleTextView.text = "Top Rated Places Just for You"
        supportText.text = "Visit these esteemed locations known for their outstanding ratings."

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
                Log.e("PopularPlaceRecommendationManager", "Error fetching city name: ${t.message}")
                distanceCallback("Error fetching city name")
            }
        })
    }


    fun fetchPreferredVisitPlaces(onComplete: (List<String>) -> Unit) {
        val preferredPlaces = mutableListOf<String>()
        val databaseReference = FirebaseDatabase.getInstance().getReference("Users").child(userId).child("preferredVisitPlaces")

        databaseReference.get().addOnSuccessListener { dataSnapshot ->
            dataSnapshot.children.forEach { child ->
                preferredPlaces.add(child.value.toString())
            }
            onComplete(preferredPlaces)
        }.addOnFailureListener {
            // Handle error
            onComplete(emptyList())
        }
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
                        Log.e("PopularPlaceRecommendationManager", "Location is null.")
                        callback(null) // Handle case where location is null
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("PopularPlaceRecommendationManager", "Failed to get location", exception)
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