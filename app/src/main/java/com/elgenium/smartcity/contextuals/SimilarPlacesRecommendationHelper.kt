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
import com.elgenium.smartcity.models.RecommendedPlace
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.network_reponses.PlaceDistanceResponse
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class SimilarPlacesRecommendationHelper(
    private val placesClient: PlacesClient,  // Assuming you pass in the Places client
    private val fusedLocationClient: FusedLocationProviderClient, // Fused Location client
    private val context: Context // Context for permission requests and logs
) {

    private val placesList = mutableListOf<RecommendedPlace>()

    // Function to get similar places based on the current place types
    fun getFilteredPlacesForRecommendationBasedOnType(
        currentPlaceTypesString: String,
        onTopPlacesReady: (List<RecommendedPlace>) -> Unit // Callback function as parameter
    ) {
        val currentPlaceTypes = cleanUpPlaceTypes(currentPlaceTypesString).firstOrNull() ?: "Point of Interest"


        Log.e("PlaceRecommendationHelper", "Current place types: $currentPlaceTypes")

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

        Log.e("PlaceRecommendationHelper", "Search query for places: $currentPlaceTypes")

        val searchByTextRequest = SearchByTextRequest.builder(currentPlaceTypes, placeFields)
            .setMaxResultCount(5)
            .build()

        getCurrentLocation { currentLocation ->
            placesClient.searchByText(searchByTextRequest)
                .addOnSuccessListener { response ->
                    Log.e("PlaceRecommendationHelper", "Successfully retrieved places. Total places found: ${response.places.size}")

                    val places = response.places
                    placesList.clear()

                    places.forEach { place ->
                        val placeTypes = place.placeTypes?.map { it.toString() } ?: emptyList()
                        val score = calculateScore(place, placeTypes, currentPlaceTypes, currentLocation)

                        Log.e("PlaceRecommendationHelper", "Place: ${place.name}, Score: $score")

                        val placeLatLng = place.latLng
                        if (placeLatLng != null) {
                            checkPlaceDistance(placeLatLng, place) { distance ->
                                val placeModel = RecommendedPlace(
                                    placeId = place.id ?: "",
                                    name = place.name ?: "",
                                    address = place.address ?: "",
                                    placeTypes = placeTypes,
                                    score = score,
                                    rating = place.rating ?: 0.0,
                                    numReviews = place.userRatingsTotal ?: 0,
                                    photoMetadata = place.photoMetadatas?.firstOrNull(),
                                    distanceString = distance
                                )
                                placesList.add(placeModel)

                                // Ensure we call the callback only once after all places are processed
                                if (placesList.size == response.places.size) {
                                    placesList.sortWith(compareByDescending<RecommendedPlace> { it.score }.thenBy { it.distance })

                                    Log.e("PlacesActivity", "place list size: ${placesList.size}")

                                    // Get the top 10 after sorting
                                    val top10 = placesList.take(10)

                                    Log.e("PlacesActivity", "TOP 10 PLACE LIST: $top10")

                                    // Call the callback with the top 10 places
                                    onTopPlacesReady(top10)
                                }
                            }
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("PlaceRecommendationHelper", "Error retrieving places", exception)
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




    // Helper function to clean up the place types string
    private fun cleanUpPlaceTypes(currentPlaceTypesString: String): List<String> {
        return currentPlaceTypesString
            .replace("[", "")
            .replace("]", "")
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    // Helper function to calculate the score based on multiple factors
    private fun calculateScore(
        place: Place,
        placeTypes: List<String>,
        preferredPlaceType: String,
        userLocation: LatLng
    ): Double {
        // Adjust weights with the preferred type, if it matches any of the place types
        val weights = mutableMapOf<String, Double>().apply {
            placeTypes.forEach { type ->
                // Assign base weight and increase if it matches the preferred type
                this[type] = if (type == preferredPlaceType) 1.5 else 0.5
            }
        }

        val rating = place.rating ?: 0.0
        val numReviews = place.userRatingsTotal ?: 0
        val placeLocation = place.latLng
        val distance = placeLocation?.let { calculateDistance(userLocation, it) } ?: 0.0

        // Calculate score using weighted factors
        return (placeTypes.sumOf { type -> weights[type] ?: 0.0 } * 2) +
                (rating * 0.3) +
                (numReviews / 100.0 * 0.1) -
                (distance / 1000.0 * 2)
    }



    // Helper function to calculate the distance between two LatLng points
    private fun calculateDistance(start: LatLng, end: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return results[0].toDouble()
    }

    // Helper function to get the current location of the user
    private fun getCurrentLocation(callback: (LatLng) -> Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    callback(LatLng(it.latitude, it.longitude))
                }
            }.addOnFailureListener { exception ->
                Log.e("PlaceRecommendationHelper", "Failed to get location", exception)
            }
        } else {
            ActivityCompat.requestPermissions(context as Activity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1)
        }
    }





}
