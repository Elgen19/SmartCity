package com.elgenium.smartcity.intelligence

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.text.Html
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.BuildConfig
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.DialogActivitySuggestionsBinding
import com.elgenium.smartcity.models.UserQueryParams
import com.elgenium.smartcity.routes_network_request.LatLngMatrix
import com.elgenium.smartcity.routes_network_request.LocationMatrix
import com.elgenium.smartcity.routes_network_request.RouteMatrixDestination
import com.elgenium.smartcity.routes_network_request.RouteMatrixOrigin
import com.elgenium.smartcity.routes_network_request.RouteMatrixRequest
import com.elgenium.smartcity.routes_network_request.WaypointMatrix
import com.elgenium.smartcity.singletons.PlacesNewClientSingleton
import com.elgenium.smartcity.singletons.RoutesMatrixClientSingleton
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.asTextOrNull
import com.google.ai.client.generativeai.type.generationConfig
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.maps.android.PolyUtil
import com.google.maps.android.SphericalUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

class AIProcessor(private val context: Context) {
    private val placesClient by lazy { PlacesNewClientSingleton.getPlacesClient(context) }
    private var placesList: MutableList<Place>? = mutableListOf()
    private var filteredList: MutableList<Place>? = mutableListOf()
    private val offRoutePlaces = mutableListOf<Place>()
    private var onPlaceSelected: ((String) -> Unit)? = null
    private var userQueries: String? = null


    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    companion object {
        private const val INTENT_SEARCHING_FOR_PLACE = "Searching for a place"
        private const val INTENT_GET_DIRECTIONS = "Get directions"
    }

    fun clearPreviousData() {
        placesList?.clear()  // Clear the places list
        filteredList?.clear()  // Clear the filtered list
        offRoutePlaces.clear()
        Log.e("AIProcessor", "Cleared places and filtered lists")
    }


    fun setOnPlaceSelectedCallback(callback: (String) -> Unit) {
        onPlaceSelected = callback
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
        userQueries = userQuery

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

    suspend fun intentClassification(userQueryParams: UserQueryParams, allLatLngs: List<LatLng>) {
        when (userQueryParams.intent) {
            INTENT_SEARCHING_FOR_PLACE -> {
                // Call a function to handle searching for a place
                handleSearchPlace(userQueryParams, allLatLngs)
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

    suspend fun performSearch(userQuery: String, allLatLngs: List<LatLng>): List<Place> {
        clearPreviousData()

        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.ADDRESS,
            Place.Field.TYPES,
            Place.Field.LAT_LNG,
            Place.Field.RATING,
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.PHOTO_METADATAS
        )

        val sampledLatLngs = allLatLngs.filterIndexed { index, _ -> index % 5 == 0 }
        val toleranceMeters = 100.0 // Adjust as per your requirement

        sampledLatLngs.forEachIndexed { index, latLng ->
            val locationBias = CircularBounds.newInstance(latLng, 1000.0)

            val searchByTextRequest = SearchByTextRequest.builder(userQuery, placeFields)
                .setMaxResultCount(3)
                .setLocationBias(locationBias)
                .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                .setPlaceFields(placeFields)
                .build()

            try {
                val response = placesClient.searchByText(searchByTextRequest).await()

                response.places.forEach { place ->
                    val placeLatLng = place.latLng ?: return@forEach
                    val isOnRoute = PolyUtil.isLocationOnPath(
                        placeLatLng,
                        allLatLngs,
                        true, // Consider the path as geodesic
                        toleranceMeters
                    )

                    if (isOnRoute) {
                        placesList?.add(place)
                        Log.i("AIProcessor", "Added: ${place.name}, ${place.rating}")
                    } else {
                        offRoutePlaces.add(place)
                        Log.e("AIProcessor", "Filtered out off-route place: ${place.name}")
                    }
                }

                Log.i("AIProcessor", "There are ${placesList?.size} on the route")
                Log.i("AIProcessor", "There are ${offRoutePlaces.size} places off route")

            } catch (exception: Exception) {
                Log.e("AIProcessor", "Error retrieving places at route point #$index: $latLng", exception)
            }
        }

        filteredList = placesList?.distinctBy { it.id }?.toMutableList()

        Log.e("AIProcessor", "Filtered places: ${filteredList?.size ?: 0}")

        // If the list of places is empty or null, recommend off-route places
        if (filteredList.isNullOrEmpty()) {
            // Call the recommendOffRoutePlace function to get a place recommendation
            recommendOffRoutePlace(context, allLatLngs) { recommendedPlace, distance ->
                if (recommendedPlace != null) {
                    // Show the dialog with the recommended place and its details
                    showNearbyOffRoutePlaceSuggestion(
                        context,
                        recommendedPlace,  // Pass the recommended off-route place
                        distance  // Pass the distance (in meters)
                    )

                } else {
                    // No off-route places to recommend
                    Log.d("AIProcessor", "No off-route places available.")
                }
            }
        }

        return filteredList ?: emptyList()
    }

    suspend fun handleSearchPlace(userQueryParams: UserQueryParams, allLatLngs: List<LatLng>): List<Place> {
        clearPreviousData()

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
            Place.Field.USER_RATINGS_TOTAL,
            Place.Field.PHOTO_METADATAS
        )

        val sampledLatLngs = allLatLngs.filterIndexed { index, _ -> index % 5 == 0 }
        val toleranceMeters = 100.0 // Adjust as per your requirement

        sampledLatLngs.forEachIndexed { index, latLng ->
            val locationBias = CircularBounds.newInstance(latLng, 1000.0)

            val searchByTextRequest = SearchByTextRequest.builder(userQueryParams.keywords, placeFields)
                .setMaxResultCount(3)
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

            try {
                val response = placesClient.searchByText(searchByTextRequest).await()

                response.places.forEach { place ->
                    val placeLatLng = place.latLng ?: return@forEach
                    val isOnRoute = PolyUtil.isLocationOnPath(
                        placeLatLng,
                        allLatLngs,
                        true, // Consider the path as geodesic
                        toleranceMeters
                    )

                    if (isOnRoute) {
                        placesList?.add(place)
                    } else {
                        offRoutePlaces.add(place)
                        Log.e("AIProcessor", "Filtered out off-route place: ${place.name}")
                    }
                }

                Log.i("AIProcessor", "There are ${placesList?.size} on the route")
                Log.i("AIProcessor", "There are ${offRoutePlaces.size} places off route")

            } catch (exception: Exception) {
                Log.e("AIProcessor", "Error retrieving places at route point #$index: $latLng", exception)
            }
        }

        filteredList = placesList?.distinctBy { it.id }?.toMutableList()

        Log.e("AIProcessor", "Filtered places: ${filteredList?.size ?: 0}")

        // If the list of places is empty or null, recommend off-route places
        if (filteredList.isNullOrEmpty()) {
            // Call the recommendOffRoutePlace function to get a place recommendation
            recommendOffRoutePlace(context, allLatLngs) { recommendedPlace, distance ->
                if (recommendedPlace != null) {
                    // Show the dialog with the recommended place and its details
                    showNearbyOffRoutePlaceSuggestion(
                        context,
                        recommendedPlace,  // Pass the recommended off-route place
                        distance  // Pass the distance (in meters)
                    )

                } else {
                    // No off-route places to recommend
                    Log.d("AIProcessor", "No off-route places available.")
                }
            }
        }

        return filteredList ?: emptyList()
    }

    fun extractPlaceInfo(): List<Map<String, Any>> {
        Log.e("AIProcessor", "Extracting place info, total places: ${filteredList?.size}")
        return filteredList?.mapNotNull { place ->
            place.latLng?.let { latLng ->
                mapOf(
                    "name" to (place.name ?: "Unknown Name"),
                    "address" to (place.address ?: "Unknown Address"),
                    "latLng" to latLng,
                    "placeId" to (place.id ?: "NO PLACE ID"),
                    "photos" to place.photoMetadatas,
                    "rating" to place.rating
                )
            }
        } ?: emptyList()
    }

    fun hasPlaceIdAndIsValidPlace(): Boolean {
        // Check if the placesList is not null and contains at least one place with a valid placeId
        val hasValidPlaceId = filteredList?.any { place ->
            !place.id.isNullOrEmpty()  // Ensure placeId is not null or empty
        } ?: false  // Return false if placesList is null

        // Log the result
        Log.e("AIProcessor", "Has valid place ID: $hasValidPlaceId")

        return hasValidPlaceId
    }

    private fun showNearbyOffRoutePlaceSuggestion(
        context: Context,
        recommendedPlace: Place?,
        distance: Float?
    ) {
        // Inflate the layout using ViewBinding
        val binding = DialogActivitySuggestionsBinding.inflate(LayoutInflater.from(context))

        // Create the AlertDialog builder
        val builder = AlertDialog.Builder(context)
            .setView(binding.root) // Set the root of the view binding layout
            .setCancelable(false)   // Set whether the dialog is cancelable or not

        // Initialize the dialog
        val dialog = builder.create()

        // Handle the Dismiss button click
        binding.btnDismiss.setOnClickListener {
            dialog.dismiss()
        }

        binding.errorAnimation.setAnimation(R.raw.thinking)
        binding.btnAction.visibility = View.VISIBLE
        binding.btnAction.text = "Yes"
        binding.btnDismiss.text = "No"

        // Handle the Action button click (callback trigger happens here)
        binding.btnAction.setOnClickListener {
            recommendedPlace?.id?.let { placeId ->
                Log.d("Dialog Debug", "Setting selected place ID: $placeId")

                // Trigger the callback to send the place ID back
                onPlaceSelected?.invoke(placeId)  // This will invoke the callback
            } ?: Log.e("Dialog Debug", "Failed to set selected place ID. Recommended place ID is null.")
            dialog.dismiss()
        }

        // Prepare the dialog message with HTML formatting
        val placeName = recommendedPlace?.name ?: "Unknown place"
        val placeAddress = recommendedPlace?.address ?: "No address available"
        val placeDistance = distance?.let { "%.2f meters away from your location".format(it) } ?: "Distance not available"

        val htmlMessage = """
    Unfortunately, we cannot find $userQueries within the plotted route. The closest place is off-route and may require you to deviate the route.<br><br>
    <b>Place Name:</b> $placeName<br><br>
    <b>Address:</b> $placeAddress<br><br>
    <b>Distance:</b> $placeDistance<br><br>   
    <b>Travel time:</b> $placeDistance<br><br>
    Do you want to select this place?
    """

        // Set the HTML message in the dialog's TextView
        binding.dialogMessage.text = Html.fromHtml(htmlMessage, Html.FROM_HTML_MODE_COMPACT)
        binding.dialogTitle.text = "No known place within the route"

        // Show the dialog
        dialog.show()
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

//    private fun recommendOffRoutePlace(
//        context: Context,
//        onRecommendation: (Place?, Float?) -> Unit // Add Float for distance
//    ) {
//        getCurrentLocation(context) { currentLocation ->
//            if (currentLocation == null) {
//                Log.e("RouteRecommendation", "Failed to get current location.")
//                onRecommendation(null, null) // Pass null for both place and distance
//                return@getCurrentLocation
//            }
//
//            CoroutineScope(Dispatchers.IO).launch {
//                try {
//                    val apiKey = BuildConfig.MAPS_API_KEY
//                    val origin = RouteMatrixOrigin(
//                        waypoint = WaypointMatrix(
//                            location = LocationMatrix(
//                                latLng = LatLngMatrix(currentLocation.latitude, currentLocation.longitude)
//                            )
//                        )
//                    )
//
//                    val destinations = offRoutePlaces.map { place ->
//                        RouteMatrixDestination(
//                            waypoint = WaypointMatrix(
//                                location = LocationMatrix(
//                                    latLng = LatLngMatrix(place.latLng!!.latitude, place.latLng!!.longitude)
//                                )
//                            )
//                        )
//                    }
//
//                    val routeMatrixRequest = RouteMatrixRequest(
//                        origins = listOf(origin),
//                        destinations = destinations,
//                        travelMode = "DRIVE",
//                        routingPreference = "TRAFFIC_AWARE"
//                    )
//
//                    Log.d("RouteRecommendation", "Sending RouteMatrixRequest with ${destinations.size} destinations.")
//
//                    val routeMatrixResponse = RoutesMatrixClientSingleton.instance
//                        .computeRouteMatrix(apiKey, request = routeMatrixRequest)
//
//                    Log.d("RouteRecommendation", "RouteMatrixResponse received with ${routeMatrixResponse.size} elements.")
//
//                    // Log all places with their distances
//                    routeMatrixResponse.forEachIndexed { _, response ->
//                        val place = offRoutePlaces[response.destinationIndex]
//                        Log.d(
//                            "RouteRecommendation",
//                            "Place: ${place.name}, ${place.address} Distance: ${response.distanceMeters} meters"
//                        )
//                    }
//
//                    // Find the place with the shortest distance
//                    val closestPlace = routeMatrixResponse
//                        .withIndex()
//                        .minByOrNull { it.value.distanceMeters }?.let { closest ->
//                            val place = offRoutePlaces[closest.value.destinationIndex]
//                            Log.d("RouteRecommendation", "Closest place: ${place.name} ${place.address}, Distance: ${closest.value.distanceMeters} meters")
//                            place to closest.value.distanceMeters // Return both place and distance
//                        }
//
//                    // Recommend the closest off-route place to the user
//                    withContext(Dispatchers.Main) {
//                        closestPlace?.let {
//                            val (place, distance) = it
//                            onRecommendation(place, distance.toFloat())
//                        } ?: onRecommendation(null, null)
//                    }
//                } catch (e: Exception) {
//                    Log.e("RouteRecommendation", "Error processing Route Matrix request: ${e.message}", e)
//                    withContext(Dispatchers.Main) {
//                        onRecommendation(null, null)
//                    }
//                }
//            }
//        }
//    }

    private fun recommendOffRoutePlace(
        context: Context,
        routePolyline: List<LatLng>, // Polyline representing the route
        onRecommendation: (Place?, Float?) -> Unit
    ) {
        getCurrentLocation(context) { currentLocation ->
            if (currentLocation == null) {
                Log.e("RouteRecommendation", "Failed to get current location.")
                onRecommendation(null, null)
                return@getCurrentLocation
            }

            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val apiKey = BuildConfig.MAPS_API_KEY

                    // Prepare the origin for the Route Matrix API
                    val origin = RouteMatrixOrigin(
                        waypoint = WaypointMatrix(
                            location = LocationMatrix(
                                latLng = LatLngMatrix(currentLocation.latitude, currentLocation.longitude)
                            )
                        )
                    )

                    // Prepare destinations for the Route Matrix API
                    val destinations = offRoutePlaces.map { place ->
                        RouteMatrixDestination(
                            waypoint = WaypointMatrix(
                                location = LocationMatrix(
                                    latLng = LatLngMatrix(place.latLng!!.latitude, place.latLng!!.longitude)
                                )
                            )
                        )
                    }

                    // Step 1: Compute distances using the Route Matrix API
                    val routeMatrixRequest = RouteMatrixRequest(
                        origins = listOf(origin),
                        destinations = destinations,
                        travelMode = "DRIVE",
                        routingPreference = "TRAFFIC_AWARE"
                    )

                    Log.d("RouteRecommendation", "Sending RouteMatrixRequest with ${destinations.size} destinations.")

                    val routeMatrixResponse = RoutesMatrixClientSingleton.instance
                        .computeRouteMatrix(apiKey, request = routeMatrixRequest)

                    Log.d("RouteRecommendation", "RouteMatrixResponse received with ${routeMatrixResponse.size} elements.")

                    val userLocationDistances = routeMatrixResponse.mapIndexed { index, response ->
                        val place = offRoutePlaces[index]
                        Pair(place, response.distanceMeters.toFloat())
                    }

                    // Step 2: Check if places are near the route
                    val placesOnRoute = userLocationDistances.mapNotNull { (place, userDistance) ->
                        val placeLatLng = LatLng(place.latLng!!.latitude, place.latLng!!.longitude)
                        val isNearRoute = PolyUtil.isLocationOnPath(
                            placeLatLng,
                            routePolyline,
                            true, // geodesic
                            500.0 // tolerance in meters
                        )
                        if (isNearRoute) {
                            val routeProximity = routePolyline.minOf { routePoint ->
                                SphericalUtil.computeDistanceBetween(placeLatLng, routePoint)
                            }
                            Triple(place, userDistance, routeProximity)
                        } else null
                    }

                    if (placesOnRoute.isEmpty()) {
                        Log.d("RouteRecommendation", "No places are near the route.")
                        withContext(Dispatchers.Main) {
                            onRecommendation(null, null)
                        }
                        return@launch
                    }

                    // Step 3: Find the optimal place
                    val recommendedPlace = placesOnRoute.minByOrNull { (_, userDistance, routeProximity) ->
                        userDistance + routeProximity // Combine criteria
                    }

                    // Step 4: Provide the recommendation
                    withContext(Dispatchers.Main) {
                        recommendedPlace?.let { (place, userDistance, _) ->
                            Log.d(
                                "RouteRecommendation",
                                "Recommended place: ${place.name}, Distance: $userDistance meters"
                            )
                            onRecommendation(place, userDistance)
                        } ?: onRecommendation(null, null)
                    }
                } catch (e: Exception) {
                    Log.e("RouteRecommendation", "Error processing recommendation: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        onRecommendation(null, null)
                    }
                }
            }
        }
    }






}
