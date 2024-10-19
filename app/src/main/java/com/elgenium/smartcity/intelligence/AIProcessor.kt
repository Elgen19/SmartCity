package com.elgenium.smartcity.intelligence

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.BuildConfig
import com.elgenium.smartcity.models.UserQueryParams
import com.elgenium.smartcity.singletons.PlacesNewClientSingleton
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.SearchByTextRequest
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class AIProcessor(context: Context) {
    private val placesClient by lazy { PlacesNewClientSingleton.getPlacesClient(context) }
    private var placesList: List<Place>? = emptyList()
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val contextInClass = context

    companion object {
        private const val INTENT_SEARCHING_FOR_PLACE = "Searching for a place"
        private const val INTENT_GET_DIRECTIONS = "Get directions"
    }



    private val model = GenerativeModel(
        "tunedModels/textsearchclassifier-pqmymlb1jlbp",
        apiKey = BuildConfig.GEMINI_AI_API,
        generationConfig = generationConfig {
            temperature = 1f
            topK = 64
            topP = 0.95f
            maxOutputTokens = 8192
            responseMimeType = "text/plain"
        }
    )

    suspend fun processUserQuery(userQuery: String): String {
        // Start a chat session with an empty history
        val chat = model.startChat(listOf())

        // Send the user query as a message
        val response = chat.sendMessage(userQuery)

        // Extract the first text part of the first candidate
        val result = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.asTextOrNull()

        // Check if the result is null or empty
        if (result.isNullOrEmpty()) {
            Log.e("AIProcessor", "Processed user query: '$userQuery', but received no valid response.")
        } else {
            Log.e("AIProcessor", "Processed user query: '$userQuery', Response: '$result'")
        }

        return result ?: "" // Return an empty string if no result
    }



    fun parseUserQuery(processedUserQuery: String): UserQueryParams {
        val intentRegex = Regex("""Intent:\s*(.+)""")
        val keywordsRegex = Regex("""Keywords:\s*(.+)""")
        val placeTypeRegex = Regex("""placeType:\s*(.+)""")
        val openNowRegex = Regex("""openNow\s*=\s*(.+)""")
        val ratingRegex = Regex("""rating\s*=\s*(.+)""")
        val priceLevelsRegex = Regex("""priceLevels\s*=\s*(.+)""")

        val intent = intentRegex.find(processedUserQuery)?.groups?.get(1)?.value?.trim() ?: ""
        val keywords = keywordsRegex.find(processedUserQuery)?.groups?.get(1)?.value?.trim() ?: ""
        val placeType = placeTypeRegex.find(processedUserQuery)?.groups?.get(1)?.value?.trim() ?: ""
        val openNow = openNowRegex.find(processedUserQuery)?.groups?.get(1)?.value?.trim()?.toBoolean() ?: false
        val rating = ratingRegex.find(processedUserQuery)?.groups?.get(1)?.value?.trim()?.toDoubleOrNull() ?: 0.0
        val priceLevels = priceLevelsRegex.find(processedUserQuery)?.groups?.get(1)?.value?.trim()?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: listOf()

        return UserQueryParams(intent, keywords, placeType, openNow, rating, priceLevels)
    }

     suspend fun intentClassification(userQueryParams: UserQueryParams) {
        when (userQueryParams.intent) {
            INTENT_SEARCHING_FOR_PLACE -> {
                // Call a function to handle searching for a place
                handleSearchPlace(userQueryParams)
            }
            INTENT_GET_DIRECTIONS -> {
                // Call a function to handle getting directions
            }
            else -> {
                // Handle unsupported intents if necessary
                println("Unsupported intent: ${userQueryParams.intent}")
            }
        }
    }


    private suspend fun handleSearchPlace(userQueryParams: UserQueryParams): List<Place> {
        val openNow = userQueryParams.openNow
        val priceLevels = userQueryParams.priceLevels
        val ratings = userQueryParams.rating

        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.TYPES,
            Place.Field.LAT_LNG,
            Place.Field.RATING,
            Place.Field.USER_RATINGS_TOTAL
        )

        // Get current location using coroutine
        val currentLatLng = getCurrentLocationCoroutine(contextInClass)

        return currentLatLng?.let { it ->
            val locationBias = CircularBounds.newInstance(it, 1000.0) // 1000 meters radius

            val searchByTextRequest = SearchByTextRequest.builder(userQueryParams.keywords, placeFields)
                .setMaxResultCount(5)
                .setOpenNow(openNow)
                .setLocationBias(locationBias)
                .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                .apply {
                    if (priceLevels.isNotEmpty() && priceLevels.any { it > 0 }) {
                        setPriceLevels(priceLevels)
                    }
                    if (ratings != 0.0) {
                        minRating = ratings
                    }
                }
                .setPlaceFields(placeFields)
                .build()

            Log.e("AIProcessor", "SEARCH TEXT IS: ${userQueryParams.keywords}")

            return try {
                // Perform the search and wait for the result
                val response = placesClient.searchByText(searchByTextRequest).await()
                placesList = response.places // Set the placesList here
                placesList?.forEach { place ->
                    Log.e("AIProcessor", "Place Name: ${place.name ?: "Unknown Name"}")
                    Log.e("AIProcessor", "Place Address: ${place.address ?: "Unknown Address"}")
                    Log.e("AIProcessor", "Place Types: ${place.placeTypes?.joinToString() ?: "No Types"}")
                    Log.e("AIProcessor", "Place Rating: ${place.rating ?: "No Rating"}")
                    Log.e("AIProcessor", "Total User Ratings: ${place.userRatingsTotal ?: "No User Ratings"}")
                }
                placesList ?: emptyList()
            } catch (exception: Exception) {
                Log.e("AIProcessor", "Error retrieving places", exception)
                emptyList()
            }
        } ?: run {
            Log.e("AIProcessor", "Location not available")
            emptyList() // Return empty list if location is null
        }
    }


    fun extractPlaceInfo(): List<Map<String, Any>> {
        Log.e("AIProcessor", "Extracting place info, total places: ${placesList?.size}")
        return placesList?.mapNotNull { place ->
            place.latLng?.let { latLng ->
                mapOf(
                    "name" to (place.name ?: "Unknown Name"),
                    "address" to (place.address ?: "Unknown Address"),
                    "latLng" to latLng,
                    "placeId" to (place.id ?: "NO PLACE ID")
                )
            }
        } ?: emptyList()
    }

    fun hasPlaceIdAndIsValidPlace(): Boolean {
        // Check if the placesList is not null and contains at least one place with a valid placeId
        val hasValidPlaceId = placesList?.any { place ->
            !place.id.isNullOrEmpty()  // Ensure placeId is not null or empty
        } ?: false  // Return false if placesList is null

        // Log the result
        Log.e("AIProcessor", "Has valid place ID: $hasValidPlaceId")

        return hasValidPlaceId
    }


    private suspend fun getCurrentLocationCoroutine(context: Context): LatLng? {
        return suspendCoroutine { continuation ->
            getCurrentLocation(context) { latLng ->
                if (latLng != null) {
                    continuation.resume(latLng) // Return the location
                } else {
                    continuation.resume(null) // Return null if location is unavailable
                }
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
