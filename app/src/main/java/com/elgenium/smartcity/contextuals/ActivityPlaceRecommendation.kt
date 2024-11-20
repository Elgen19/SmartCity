package com.elgenium.smartcity.contextuals

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.models.LocationBasedPlaceRecommendationItems
import com.elgenium.smartcity.speech.TextToSpeechHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest

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
        callback: (List<LocationBasedPlaceRecommendationItems>) -> Unit
    ) {
        // Get the current location first
        getCurrentLocation(context) { currentLocation ->
            currentLocation?.let { location ->
                Log.d("ActivityPlaceRecommendation", "Current location: $location")

                val currentLatLng = LatLng(location.latitude, location.longitude)
                val locationBias = CircularBounds.newInstance(currentLatLng, 1000.0)
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
                val searchCount = currentPlaceTypes.size
                var completedSearches = 0

                Log.d("ActivityPlaceRecommendation", "Searching for place types: $currentPlaceTypes")

                currentPlaceTypes.forEach { placeType ->
                    val query = "$placeType near me"
                    Log.d("ActivityPlaceRecommendation", "Search query: '$query'")

                    val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                        .setMaxResultCount(10)
                        .setLocationBias(locationBias)
                        .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                        .build()

                    placesClient.searchByText(searchByTextRequest)
                        .addOnSuccessListener { response ->
                            val places = response.places
                            Log.d("ActivityPlaceRecommendation", "Found ${places.size} places for query: '$query'")
                            places.forEach { place ->
                                Log.d("ActivityPlaceRecommendation", "Place found: ${place.name}, Address: ${place.address}")
                                placesList.add(place)
                            }
                        }
                        .addOnCompleteListener {
                            completedSearches++
                            Log.d("ActivityPlaceRecommendation", "Completed search $completedSearches/$searchCount")
                            if (completedSearches == searchCount) {
                                // Convert places to LocationBasedPlaceRecommendationItems list and update RecyclerView
                                val placeItems = placesList.map {
                                    LocationBasedPlaceRecommendationItems(
                                        it.name ?: "Unknown",
                                        it.address ?: "Unknown address",
                                        it.id ?: "No ID"
                                    )
                                }

                                Log.d("ActivityPlaceRecommendation", "Returning ${placeItems.size} place recommendations.")
                                callback(placeItems)
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
            } ?: run {
                Log.e("ActivityPlaceRecommendation", "Current location is not available.")
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