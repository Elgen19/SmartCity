package com.elgenium.smartcity.contextuals

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.BuildConfig
import com.elgenium.smartcity.models.ActivityDetails
import com.elgenium.smartcity.models.LocationBasedPlaceRecommendationItems
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.network_reponses.PlaceDistanceResponse
import com.elgenium.smartcity.speech.TextToSpeechHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.atomic.AtomicInteger

class ActivityPlaceRecommendation(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val textToSpeechHelper = TextToSpeechHelper()

    init {
        textToSpeechHelper.initializeTTS(context)
    }

    fun performTextSearch(
        placesClient: PlacesClient,
        currentPlaceTypes: List<String>,
        activityList: List<ActivityDetails>, // Pass the activity list to determine location
        context: Context,
        callback: (List<LocationBasedPlaceRecommendationItems>) -> Unit
    ) {
        // Get location for recommendation (either from the last activity or user location)
        val locationForRecommendation = getLocationForRecommendation(activityList)

        if (locationForRecommendation != null) {
            // Use the last activity's location
            val currentLatLng = LatLng(locationForRecommendation.latitude, locationForRecommendation.longitude)
            fetchPlaceRecommendations(placesClient, currentPlaceTypes, currentLatLng, callback)
        } else {
            // If no activity is available, use the user's current location
            getCurrentLocation(context) { currentLatLng ->
                currentLatLng?.let {
                    fetchPlaceRecommendations(placesClient, currentPlaceTypes, it, callback)
                } ?: run {
                    // Handle the case where the location is null (show error or fallback)
                    Log.e("ActivityPlaceRecommendation", "Unable to fetch current location.")
                    callback(emptyList()) // Return empty list or handle error
                }
            }
        }
    }

    private fun fetchPlaceRecommendations(
        placesClient: PlacesClient,
        currentPlaceTypes: List<String>,
        currentLatLng: LatLng,
        callback: (List<LocationBasedPlaceRecommendationItems>) -> Unit
    ) {
        val locationBias = CircularBounds.newInstance(currentLatLng, 500.0)
        Log.d("ActivityPlaceRecommendation", "Location bias set to: $locationBias")

        val searchSize = if (currentPlaceTypes.size == 1) 10 else 5

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
        val placeItems = mutableListOf<LocationBasedPlaceRecommendationItems>()
        val searchCount = currentPlaceTypes.size
        var completedSearches = 0

        Log.d("ActivityPlaceRecommendation", "Searching for place types: $currentPlaceTypes")

        currentPlaceTypes.forEach { placeType ->
            val query = "$placeType near me"
            Log.d("ActivityPlaceRecommendation", "Search query: '$query'")

            val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                .setMaxResultCount(searchSize)
                .setLocationBias(locationBias)
                .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                .build()

            placesClient.searchByText(searchByTextRequest)
                .addOnSuccessListener { response ->
                    val places = response.places
                    Log.d("ActivityPlaceRecommendation", "Found ${places.size} places for query: '$query'")

                    val distanceCallbacksCompleted = AtomicInteger(0) // Track distance callbacks
                    places.forEach { place ->
                        Log.d("ActivityPlaceRecommendation", "Place found: ${place.name}, Address: ${place.address}")
                        placesList.add(place)

                        // Use checkPlaceDistance to calculate the distance
                        checkPlaceDistance(currentLatLng, place) { distance ->
                            val item = LocationBasedPlaceRecommendationItems(
                                place.name ?: "Unknown",
                                place.address ?: "Unknown address",
                                place.id ?: "No ID",
                                place.latLng?.toString() ?: "No LatLng",
                                place.rating?.toString() ?: "No ratings",
                                distance
                            )
                            placeItems.add(item)

                            // Check if all distance callbacks are completed
                            if (distanceCallbacksCompleted.incrementAndGet() == places.size) {
                                completedSearches++
                                Log.d("ActivityPlaceRecommendation", "Completed search $completedSearches/$searchCount")
                                if (completedSearches == searchCount) {
                                    Log.d("ActivityPlaceRecommendation", "Returning ${placeItems.size} place recommendations.")
                                    callback(placeItems)
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ActivityPlaceRecommendation", "Error during place search: ${exception.message}")
                    completedSearches++
                    if (completedSearches == searchCount) {
                        Log.d("ActivityPlaceRecommendation", "Returning empty list due to failure.")
                        callback(emptyList())
                    }
                }
        }
    }


    private fun getLocationForRecommendation(activityList: List<ActivityDetails>): Location? {
        // Check if activityList has any activities
        return if (activityList.isNotEmpty()) {
            // Get the location from the last activity in the list
            val lastActivity = activityList.last()
            if (lastActivity.placeLatlng.isNotEmpty()) {
                val latLngParts = lastActivity.placeLatlng.split(",")
                if (latLngParts.size == 2) {
                    val latitude = latLngParts[0].toDoubleOrNull()
                    val longitude = latLngParts[1].toDoubleOrNull()
                    if (latitude != null && longitude != null) {
                        Location("").apply {
                            this.latitude = latitude
                            this.longitude = longitude
                        }
                    } else {
                        null
                    }
                } else {
                    null
                }
            } else {
                null
            }
        } else {
            // If no activity in the list, return null to use the user's current location
            null
        }
    }


    fun performTextSearchWithActivityOrigin(
        placesClient: PlacesClient,
        originLatLng: LatLng, // Activity's LatLng as the origin
        placeTypes: List<String>,
        callback: (List<LocationBasedPlaceRecommendationItems>) -> Unit
    ) {
        Log.d("ActivityPlaceRecommendation", "Using activity origin: $originLatLng")

        val locationBias = CircularBounds.newInstance(originLatLng, 500.0)
        Log.d("ActivityPlaceRecommendation", "Location bias set to: $locationBias")

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
        val placeItems = mutableListOf<LocationBasedPlaceRecommendationItems>()
        val searchCount = placeTypes.size
        var completedSearches = 0

        Log.d("ActivityPlaceRecommendation", "Searching for place types: $placeTypes")

        placeTypes.forEach { placeType ->
            val query = "$placeType near me"
            Log.d("ActivityPlaceRecommendation", "Search query: '$query'")

            val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                .setMaxResultCount(3)
                .setLocationBias(locationBias)
                .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                .build()

            placesClient.searchByText(searchByTextRequest)
                .addOnSuccessListener { response ->
                    val places = response.places
                    Log.d("ActivityPlaceRecommendation", "Found ${places.size} places for query: '$query'")

                    val distanceCallbacksCompleted = AtomicInteger(0) // Track distance callbacks
                    places.forEach { place ->
                        Log.d("ActivityPlaceRecommendation", "Place found: ${place.name}, Address: ${place.address}")
                        placesList.add(place)

                        // Use checkPlaceDistance to calculate the distance
                        checkPlaceDistance(originLatLng, place) { distance ->
                            val item = LocationBasedPlaceRecommendationItems(
                                place.name ?: "Unknown",
                                place.address ?: "Unknown address",
                                place.id ?: "No ID",
                                place.latLng?.toString() ?: "No LatLng",
                                place.rating?.toString() ?: "No ratings",
                                distance
                            )
                            placeItems.add(item)

                            // Check if all distance callbacks are completed
                            if (distanceCallbacksCompleted.incrementAndGet() == places.size) {
                                completedSearches++
                                Log.d("ActivityPlaceRecommendation", "Completed search $completedSearches/$searchCount")
                                if (completedSearches == searchCount) {
                                    Log.d("ActivityPlaceRecommendation", "Returning ${placeItems.size} place recommendations.")
                                    callback(placeItems)
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ActivityPlaceRecommendation", "Error during place search: ${exception.message}")
                    completedSearches++
                    if (completedSearches == searchCount) {
                        Log.d("ActivityPlaceRecommendation", "Returning empty list due to failure.")
                        callback(emptyList())
                    }
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
                        Log.e("ActivityPlaceRecommendation", "Location is null.")
                        callback(null) // Handle case where location is null
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("ActivityPlaceRecommendation", "Failed to get location", exception)
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