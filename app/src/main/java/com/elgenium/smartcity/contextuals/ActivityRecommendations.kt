package com.elgenium.smartcity.contextuals

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import android.view.LayoutInflater
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.BuildConfig
import com.elgenium.smartcity.PlacesActivity
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.BottomSheetContextualRecommendationBinding
import com.elgenium.smartcity.models.RecommendedPlace
import com.elgenium.smartcity.network.OpenWeatherAPIService
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.network_reponses.PlaceDistanceResponse
import com.elgenium.smartcity.network_reponses.WeatherResponse
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

class ActivityRecommendations(context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private var weatherCondition = ""

    private fun createWeatherApiService(): OpenWeatherAPIService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(OpenWeatherAPIService::class.java)
    }

    fun getTimeOfDay(): String {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY) // Get current hour in 24-hour format

        return when (hour) {
            in 5..11 -> "morning"    // 5 AM to 11 AM
            in 12..17 -> "afternoon"  // 12 PM to 5 PM
            in 18..22 -> "evening"    // 6 PM to 10 PM
            else -> "night"                // 9 PM to 4 AM
        }
    }

    private fun mapWeatherAndTimeOfTheDayToActivities(weatherCondition: String, timeOfDay: String): List<String> {

        return when (weatherCondition) {
            // Sunny or clear conditions
            "Clear" -> {
                when (timeOfDay) {
                    "morning" -> listOf("go for a morning jog", "enjoy a coffee at a cafe", "visit a park", "do a workout at the gym", "explore the outdoors", "relax at a resort", "grab a quick bite at a fast-food joint")
                    "afternoon" -> listOf("have lunch at a cafe", "shopping spree at a mall", "hike near the mountains", "visit an amusement park", "sightseeing at tourist attractions", "relax at the beach", "explore tourist spots", "enjoy a meal at a restaurant")
                    "evening" -> listOf("dine at a restaurant", "grab drinks at a bar", "outdoor dining experience", "have a barbecue dinner", "shop at a mall", "pick up takeout for the evening", "enjoy a relaxed meal at a fast-food joint")
                    else -> emptyList()
                }
            }

            // Cloudy conditions
            "Clouds" -> {
                when (timeOfDay) {
                    "morning" -> listOf("enjoy a peaceful walk in the park", "visit a tourist attraction", "grab coffee at a cafe", "have a relaxing breakfast", "work out at a fitness center", "shop at a local market")
                    "afternoon" -> listOf("shop at a shopping mall", "enjoy a meal near the mountains", "explore a museum", "take a dip in the swimming pool", "have fun at an amusement park", "visit tourist spots", "relax at the beach", "dine at a pizza restaurant", "unwind at a spa")
                    "evening" -> listOf("order takeout", "enjoy a food park visit", "have dinner near the mountains", "barbecue party", "dine at a fast-food restaurant", "socialize at a bar", "have a meal at a restobar")
                    else -> emptyList()
                }
            }

            // Mist or foggy conditions
            "Atmosphere" -> {
                listOf("visit a cozy coffee shop", "enjoy a bowl of soup", "explore a shopping mall", "work out at the gym", "dine at a food park", "indulge in street food", "dine outdoors", "relax at a mountain cafe", "enjoy a warm meal near the mountains")
            }

            // Light rain conditions to heavy rain
            "Drizzle", "Rain", "Thunderstorm" -> {
                listOf("order a meal for delivery", "grab takeout", "enjoy a hot meal at a cafe", "stay warm with a soup", "indulge in popular comfort foods")
            }

            // Default case for other weather codes
            else -> listOf("dine at a restaurant", "visit a cozy cafe", "shop at a mall")
        }
    }

    private fun mapActivitiesToPlaceQueries(activities: List<String>): List<String> {
        return activities.flatMap { activity ->
            when {
                activity.contains("coffee", ignoreCase = true) -> listOf("coffee_shop", "cafe")
                activity.contains("jog", ignoreCase = true) || activity.contains("workout", ignoreCase = true) -> listOf("gym", "fitness_center", "outdoor_exercise")
                activity.contains("park", ignoreCase = true) -> listOf("park", "nature_park", "botanical_garden")
                activity.contains("shop", ignoreCase = true) || activity.contains("mall", ignoreCase = true) -> listOf("shopping_mall", "retail_store", "outlet")
                activity.contains("restaurant", ignoreCase = true) || activity.contains("dine", ignoreCase = true) -> listOf("restaurant", "fast_food", "dining", "cafe")
                activity.contains("bar", ignoreCase = true) || activity.contains("drinks", ignoreCase = true) -> listOf("bar", "pub", "night_club")
                activity.contains("amusement park", ignoreCase = true) -> listOf("amusement_park", "theme_park")
                activity.contains("mountain", ignoreCase = true) -> listOf("mountain_view", "mountain_hike", "resort_near_mountains")
                activity.contains("beach", ignoreCase = true) -> listOf("beach", "beachfront", "seaside_resort")
                activity.contains("museum", ignoreCase = true) -> listOf("museum", "art_gallery", "history_museum")
                activity.contains("spa", ignoreCase = true) -> listOf("spa", "wellness_center")
                activity.contains("takeout", ignoreCase = true) || activity.contains("delivery", ignoreCase = true) -> listOf("meal_delivery", "takeout")
                activity.contains("soup", ignoreCase = true) -> listOf("soup_place", "restaurant")
                activity.contains("food park", ignoreCase = true) -> listOf("food_park", "street_food")
                activity.contains("pizza", ignoreCase = true) -> listOf("pizza_restaurant")
                activity.contains("tourist", ignoreCase = true) -> listOf("tourist_attraction", "tourist_spots", "tourist_information")
                activity.contains("barbecue", ignoreCase = true) -> listOf("barbecue_restaurant", "grill", "barbecue_catering")
                activity.contains("comfort food", ignoreCase = true) -> listOf("comfort_food_restaurant")
                activity.contains("food stall", ignoreCase = true) -> listOf("street_food", "food_stall")
                else -> emptyList()
            }
        }
    }

    private fun mapWeatherAndTimeOfTheDayToQueries(weatherCondition: String, timeOfDay: String): List<String> {
        // First, get the activity suggestions based on the weather and time of day
        val activities = mapWeatherAndTimeOfTheDayToActivities(weatherCondition, timeOfDay)

        // Then, map those activities to the corresponding place types or queries
        return mapActivitiesToPlaceQueries(activities)
    }

    fun performTextSearch(
        placesClient: PlacesClient,
        context: Context,
        callback: (List<RecommendedPlace>) -> Unit
    ) {
        val currentPlaceQueries = mapWeatherAndTimeOfTheDayToQueries(weatherCondition, getTimeOfDay())

        Log.e("ActivityRecommendations", "Starting performTextSearch. Current place queries: $currentPlaceQueries")

        if (currentPlaceQueries.isEmpty()) {
            Log.e("ActivityRecommendations", "No place queries found. Returning empty list.")
            callback(emptyList())
            return
        }

        getCurrentLocation(context) { currentLocation ->
            currentLocation?.let {
                Log.e("ActivityRecommendations", "Current location: $currentLocation")
                val currentLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
                val locationBias = CircularBounds.newInstance(currentLatLng, 500.0)

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

                val placesList = mutableListOf<RecommendedPlace>()
                val searchCount = currentPlaceQueries.size
                var completedSearches = 0
                var distanceCalculationCount = 0 // Track distance calculations completion

                currentPlaceQueries.forEach { placeQuery ->
                    Log.e("ActivityRecommendations", "Searching for place: $placeQuery")

                    val searchByTextRequest = SearchByTextRequest.builder(placeQuery, placeFields)
                        .setMaxResultCount(1)
                        .setLocationBias(locationBias)
                        .setOpenNow(true)
                        .setMinRating(4.0)
                        .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                        .build()

                    placesClient.searchByText(searchByTextRequest)
                        .addOnSuccessListener { response ->
                            val places = response.places
                            Log.e("ActivityRecommendations", "Search success for query: $placeQuery. Found ${places.size} places.")

                            places.forEach { place ->
                                val recommendedPlace = RecommendedPlace(
                                    placeId = place.id ?: "",
                                    name = place.name ?: "",
                                    address = place.address ?: "",
                                    rating = place.rating ?: 0.0,
                                    numReviews = place.userRatingsTotal ?: 0,
                                    photoMetadata = place.photoMetadatas?.getOrNull(0)
                                )

                                checkPlaceDistance(currentLocation, place) { distance ->
                                    recommendedPlace.distanceString = distance
                                    recommendedPlace.distance = parseDistanceToMeters(distance)

                                    synchronized(placesList) {
                                        placesList.add(recommendedPlace)
                                        Log.e("ActivityRecommendations", "Added place: ${recommendedPlace.name} to the list with distance: ${recommendedPlace.distance}")
                                    }

                                    // Track the distance calculations
                                    distanceCalculationCount++
                                    if (distanceCalculationCount == searchCount) {
                                        // Return once all distances are calculated
                                        callback(placesList.distinctBy { it.name }.shuffled())
                                    }
                                }
                            }
                        }
                        .addOnCompleteListener {
                            completedSearches++
                            Log.e("ActivityRecommendations", "Completed search for query: $placeQuery. Completed searches: $completedSearches/$searchCount.")
                            if (completedSearches == searchCount && distanceCalculationCount == searchCount) {
                                callback(placesList.distinctBy { it.name }.shuffled())
                            }
                        }
                        .addOnFailureListener { exception ->
                            completedSearches++
                            Log.e("ActivityRecommendations", "Search failed for query: $placeQuery. Error: ${exception.localizedMessage}")
                            if (completedSearches == searchCount && distanceCalculationCount == searchCount) {
                                callback(placesList.distinctBy { it.name }.shuffled())
                            }
                        }
                }

                if (searchCount == 0) {
                    Log.e("ActivityRecommendations", "No place queries to search. Returning empty list.")
                    callback(placesList)
                }

            } ?: run {
                Log.e("ActivityRecommendations", "Current location is not available.")
                callback(emptyList())
            }
        }
    }


    private fun parseDistanceToMeters(distance: String): Double {
        return when {
            distance.contains("km") -> {
                val km = distance.replace("km", "").trim().toDouble()
                km * 1000 // Convert to meters
            }
            distance.contains("m") -> {
                distance.replace("m", "").trim().toDouble()
            }
            else -> 0.0 // Default to 0 if the format is unexpected
        }
    }




    fun fetchWeather(
        context: Context
    ) {
        getCurrentLocation(context) { latLng ->
            latLng?.let {
                val weatherApiService = createWeatherApiService()
                val lat = it.latitude
                val lang = it.longitude
                val apiKey = BuildConfig.OPEN_WEATHER_API

                val call = weatherApiService.getCurrentWeatherData(lat, lang, apiKey = apiKey)

                call.enqueue(object : Callback<WeatherResponse> {
                    override fun onResponse(
                        call: Call<WeatherResponse>,
                        response: Response<WeatherResponse>
                    ) {
                        if (response.isSuccessful) {
                            val weatherResponse = response.body()
                            if (weatherResponse != null) {
                                Log.e("ActivityRecommendations", "RESPONSE: $weatherResponse")

                                weatherCondition = weatherResponse.weather.firstOrNull()?.main.toString()
                                Log.e("ActivityRecommendations", "Weather condition: $weatherCondition")
                                Log.e("ActivityRecommendations", "TIME OF THE DAY: ${getTimeOfDay()}")

                            } else {
                                Log.e("ActivityRecommendations", "Weather response is null.")
                            }
                        } else {
                            Log.e("ActivityRecommendations", "API Error: ${response.code()} - ${response.message()}")
                        }
                    }

                    override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                        Log.e("ActivityRecommendations", "Network Error: ${t.localizedMessage}")
                    }
                })
            } ?: run {
                Log.e("ActivityRecommendations", "Failed to get current location.")
            }
        }
    }


    fun showRecommendationBottomsheet(context: Context, placesList: List<RecommendedPlace>, placesClient: PlacesClient) {
        Log.e("ActivityRecommendations", "Starting to show recommendation bottom sheet with ${placesList.size} places.")

        // Create a new instance of BottomSheetDialog
        val bottomSheetDialog = BottomSheetDialog(context)
        Log.e("ActivityRecommendations", "BottomSheetDialog instance created.")

        // Inflate the layout for the bottom sheet
        val bottomSheetView = LayoutInflater.from(context).inflate(
            R.layout.bottom_sheet_contextual_recommendation,
            null
        )
        Log.e("ActivityRecommendations", "Bottom sheet layout inflated.")

        // Set up view binding for the bottom sheet layout
        val binding = BottomSheetContextualRecommendationBinding.bind(bottomSheetView)
        Log.e("ActivityRecommendations", "View binding initialized for bottom sheet.")

        val (title, description) = mapWeatherAndTimeOfDayToTitleAndDescription(weatherCondition, getTimeOfDay())

        // Set the title and description in the bottom sheet
        binding.textViewRecommendationTitle.text = title
        binding.textViewRecommendationDescription.text = description

        // Set up the RecyclerView with the adapter
        Log.e("ActivityRecommendations", "Setting up RecyclerView with the places list.")
        val adapter = RecommendedPlaceAdapter(
            placesList,  // Pass the list of places
            false,
            placesClient,  // Pass the PlacesClient
            onPlaceClick = { place ->
                Log.e("ActivityRecommendations", "Place clicked: ${place.name}")
                val intent = Intent(context, PlacesActivity::class.java)

                intent.putExtra("DASHBOARD_RECOMMENDED_PLACE_ID", place.placeId)
                context.startActivity(intent)
                Log.e("ActivityRecommendations", "Navigating to PlacesActivity with placeId: ${place.placeId}")
            }
        )
        binding.recyclerViewMealRecommendations.adapter = adapter
        binding.recyclerViewMealRecommendations.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
        Log.e("ActivityRecommendations", "RecyclerView adapter set and layout manager configured.")

        // Set up the close button (ImageButton)
        binding.buttonClose.setOnClickListener {
            Log.e("ActivityRecommendations", "Close button clicked. Dismissing bottom sheet.")
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.setContentView(bottomSheetView)
        Log.e("ActivityRecommendations", "Bottom sheet content set.")

        // Show the bottom sheet dialog
        bottomSheetDialog.show()
        Log.e("ActivityRecommendations", "Bottom sheet dialog shown.")
    }








    private fun mapWeatherAndTimeOfDayToTitleAndDescription(weatherCondition: String, timeOfDay: String): Pair<String, String> {
        return when (weatherCondition) {
            // Sunny or clear conditions
            "Clear" -> {
                when (timeOfDay) {
                    "morning" -> "Kickstart Your Day" to "Go for a morning jog, enjoy a coffee at a cafe, visit a park, do a workout at the gym, explore the outdoors, relax at a resort, or grab a quick bite at a fast-food joint."
                    "afternoon" -> "Explore and Relax" to "Go on a shopping spree at a mall, hike near the mountains, visit an amusement park, sightsee at tourist attractions, relax at the beach, explore tourist spots, or enjoy a meal at a restaurant."
                    "evening" -> "Dine and Unwind" to "Grab drinks at a bar, enjoy an outdoor dining experience, have a barbecue dinner, shop at a mall, pick up takeout, or enjoy a relaxed meal at a fast-food joint."
                    else -> "Enjoy Activities" to "Activities may vary based on time of day"
                }
            }

            // Cloudy conditions
            "Clouds" -> {
                when (timeOfDay) {
                    "morning" -> "Relax and Refresh" to "Enjoy a peaceful walk in the park, visit a tourist attraction, grab coffee at a cafe, have a relaxing breakfast, work out at a fitness center, or shop at a local market."
                    "afternoon" -> "Discover and Unwind" to "Shop at a mall, enjoy a meal near the mountains, explore a museum, take a dip in the pool, have fun at an amusement park, visit tourist spots, relax at the beach, dine at a pizza restaurant, or unwind at a spa."
                    "evening" -> "Evening Outing" to "Order takeout, visit a food park, have dinner near the mountains, host a barbecue, dine at a fast-food restaurant, socialize at a bar, or have a meal at a restobar."
                    else -> "Enjoy Activities" to "Activities may vary based on time of day"
                }
            }

            // Mist or foggy conditions
            "Atmosphere" -> "Cozy Escapades" to "Visit a cozy coffee shop, enjoy a bowl of soup, explore a shopping mall, work out at the gym, dine at a food park, indulge in street food, or relax at a mountain cafe."

            // Rainy conditions
            "Drizzle", "Rain", "Thunderstorm" -> "Comfort Food and Cozy Spots" to "Order a meal for delivery, grab takeout, enjoy a hot meal at a cafe, stay warm with soup, or indulge in popular comfort foods."

            // Default case
            else -> "Leisurely Outings" to "Dine at a restaurant, visit a cozy cafe, or shop at a mall."
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
                        Log.e("WeatherBasedPlaceRecommendation", "Location: $latLng")

                        callback(latLng) // Return the location through the callback
                    } ?: run {
                        Log.e("WeatherBasedPlaceRecommendation", "Location is null.")
                        callback(null) // Handle case where location is null
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("WeatherBasedPlaceRecommendation", "Failed to get location", exception)
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