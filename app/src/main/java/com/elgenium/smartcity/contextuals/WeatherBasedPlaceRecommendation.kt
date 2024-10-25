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
import com.elgenium.smartcity.network.OpenWeatherAPIService
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.network_reponses.GeocodingResponse
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

class WeatherBasedPlaceRecommendation(
    context: Context
) {
    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private var weatherCondition = ""
    private var currentPlaceTypes: List<String> = emptyList()

    private fun createWeatherApiService(): OpenWeatherAPIService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(OpenWeatherAPIService::class.java)
    }

     fun isRecommendationAvailable(): Boolean{
        if (currentPlaceTypes.isNotEmpty()){
            Log.e("WeatherBasedPlaceRecommendation", "PLACE TYPES SIZE AT IF: ${currentPlaceTypes.size}")
            return true
        }

        Log.e("WeatherBasedPlaceRecommendation", "PLACE TYPES SIZE AT NOT IF: ${currentPlaceTypes.size}")
        return false
    }

    // Function to get recommendations based on weather conditions
    private fun mapWeatherToPlaceTypes(weatherCondition: String, timeOfDay: String): List<String> {

        return when (weatherCondition) {
            // Sunny or clear conditions
            "Clear" -> {
                when (timeOfDay) {
                    "morning" -> listOf("coffee_shop", "bakery", "gym", "park", "fast_food_restaurant", "resort_hotel", "convenience_store")
                    "afternoon" -> listOf("cafe", "shopping_mall", "cafe near the mountain",  "amusement_park", "tourist_attraction", "beach", "tourist_spots", "restaurant")
                    "evening" -> listOf("restaurant", "bar", "restaurants near the mountain", "shopping_mall", "outdoor_dining", "food_market", "takeout", "fast_food_restaurant", "barbecue_restaurant")
                    else -> emptyList()
                }
            }

            //  cloudy
            "Clouds" -> {
                when (timeOfDay) {
                    "morning" -> listOf(
                        "park",
                        "tourist_attraction",
                        "coffee_shop",
                        "cafe near the mountain",
                        "bakery",
                        "fitness_center",
                        "market",
                    )
                    "afternoon" -> listOf("shopping_malls", "cafe near the mountain",  "restaurants near the mountain", "tourist_spots", "museum", "swimming_pool", "amusement_park", "tourist_attraction", "beach", "pizza_restaurant", "spa" )
                    "evening" -> listOf("takeout", "food park", "cafe near the mountain", "restaurants near the mountain", "barbecue_restaurant", "american_restaurant", "fast_food_restaurant", "bars", "restobar", "shopping_mall")
                    else -> emptyList()
                }
            }


            // Mist or foggy conditions
            "Atmosphere" -> {
                listOf("coffee_shop", "soup", "shopping_mall", "gym", "food park", "food stall", "outdoor dining", "cafe near the mountain", "restaurants near the mountain")
            }

            // Light rain conditions to heavy rain
           "Drizzle", "Rain", "Thunderstorm" -> {
                listOf("meal_delivery", "meal_takeaway", "cafe", "take out", "soup", "popular foods")
            }


            // Default case for other weather codes
            else -> listOf("restaurants", "cafe", "shopping_malls")
        }
    }

    private fun generateRecommendationText(weatherCondition: String, timeOfDay: String): Pair<String, String> {
        return when (weatherCondition) {
            // Sunny and Clear conditions
            "Clear" -> when (timeOfDay) {
                "morning" -> "Start Your Sunny Day Right" to "Grab a coffee or enjoy a hearty breakfast at these cool and cozy spots."
                "afternoon" -> "Beat the Heat with Cool Spots" to "Relax at these air-conditioned venues or treat yourself to a refreshing meal."
                "evening" -> "Wind Down on a Clear Evening" to "Enjoy a delightful dinner or dessert at these highly rated indoor places."
                else -> "Enjoy the Clear Skies" to "Make the most of the weather with these great places to visit."
            }

            // Partly cloudy, Cloudy, Overcast
            "Clouds" -> when (timeOfDay) {
                "morning" -> "A Perfect Morning with Clouds" to "Take a morning stroll or explore these scenic attractions."
                "afternoon" -> "Cloudy Afternoon Adventures" to "Enjoy the outdoors with these fun-filled activities and attractions."
                "evening" -> "Evening Fun with Light Clouds" to "Unwind and enjoy the cool breeze at these recommended spots."
                else -> "Cloudy but Beautiful" to "Discover top destinations to make the most of the day."
            }

            // Mist, Fog, Freezing Fog
            "Atmosphere" -> "Indoor Escapes for Low Visibility" to "Stay warm and explore these indoor attractions while the fog clears."

            // Light drizzle, Patchy light rain, Light rain, Patchy rain possible
            "Rain, Drizzle, Thunderstorm" -> "Stay Dry and Cozy" to "Find warmth indoors! Order your favorite meals delivered to your door and enjoy a cozy indoor retreat without the hassle of going out."

            else -> {
                "Recommended Places for You" to "Explore these places, selected for their quality and positive reviews."
            }
        }
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

    // Function to fetch weather and provide recommendations
    fun fetchWeatherAndRecommend(
        context: Context,
        onRecommendationsReady: (List<String>?) -> Unit
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
                                Log.e("WeatherBasedPlaceRecommendation", "RESPONSE: $weatherResponse")

                                weatherCondition = weatherResponse.weather.firstOrNull()?.main.toString()
                                Log.e("WeatherBasedPlaceRecommendation", "Weather condition: $weatherCondition")
                                Log.e("WeatherBasedPlaceRecommendation", "TIME OF THE DAY: ${getTimeOfDay()}")

                                val recommendations = mapWeatherToPlaceTypes(weatherCondition, getTimeOfDay())

                                onRecommendationsReady(recommendations)
                            } else {
                                Log.e("WeatherBasedPlaceRecommendation", "Weather response is null.")
                                onRecommendationsReady(null)
                            }
                        } else {
                            Log.e("WeatherBasedPlaceRecommendation", "API Error: ${response.code()} - ${response.message()}")
                            onRecommendationsReady(null)
                        }
                    }

                    override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                        Log.e("WeatherBasedPlaceRecommendation", "Network Error: ${t.localizedMessage}")
                        onRecommendationsReady(null)
                    }
                })
            } ?: run {
                Log.e("WeatherBasedPlaceRecommendation", "Failed to get current location.")
                onRecommendationsReady(null)
            }
        }
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
        val (title, description) = generateRecommendationText(weatherCondition, getTimeOfDay())
        titleTextView.text = title
        supportText.text = description

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


    fun performTextSearch(
        placesClient: PlacesClient,
        context: Context,
        placeRecommendations: List<String>, // Add weather condition as a parameter
        recyclerView: RecyclerView,
        titleTextView: TextView,
        supportText: TextView,
        isForCarousel: Boolean,
        callback: (List<Place>) -> Unit
    ) {
        // Map preferences to place types
        currentPlaceTypes = placeRecommendations
        Log.e("WeatherBasedPlaceRecommendation", "PLACE TYPES SIZE: ${currentPlaceTypes.size}")
        isRecommendationAvailable()

        // Get the current location first
        getCurrentLocation(context) { currentLocation ->
            // Ensure current location is available
            currentLocation?.let {
                Log.e("WeatherBasedPlaceRecommendation", "Current location: $currentLocation")
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
                    Log.e("WeatherBasedPlaceRecommendation", "City Name: $cityName")
                    val allFilteredPlaces = mutableListOf<Place>()
                    var completedQueries = 0

                    currentPlaceTypes.forEach { placeType ->
                        val query = "$cityName $placeType"
                        Log.e("WeatherBasedPlaceRecommendation", "Search query for places: $query")

                        val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                            .setMaxResultCount(1)
                            .setOpenNow(true)
                            .setLocationBias(locationBias)
                            .setRankPreference(SearchByTextRequest.RankPreference.DISTANCE)
                            .build()

                        // Perform the search using the PlacesClient
                        placesClient.searchByText(searchByTextRequest)
                            .addOnSuccessListener { response ->
                                val places = response.places

                                // Log the number of places retrieved
                                Log.e("WeatherBasedPlaceRecommendation", "Number of places found: ${places.size}")
                                places.forEach { place ->
                                    Log.e("WeatherBasedPlaceRecommendation", "Place: ${place.name}, Address: ${place.address}, ID: ${place.id}, Number of Reviews: ${place.userRatingsTotal}, Place rating: ${place.rating}")
                                    // Check if the place is within a certain distance from the current location
                                    val placeLatLng = place.latLng
                                    if (placeLatLng != null) {
                                        val distance = calculateDistance(currentLatLng, placeLatLng) // Implement this method
                                        if (distance <= 1000) { // Filter by distance (in meters)
                                            allFilteredPlaces.add(place) // Add the place if within the distance
                                        }
                                    }

                                }

                                completedQueries++ // Increment completed queries
                                val currentPlaceSize = currentPlaceTypes.size
                                // Check if all queries are completed
                                Log.e("WeatherBasedPlaceRecommendation", "COMPLETED QUERIES: $completedQueries / $currentPlaceSize")

                                if (completedQueries == currentPlaceSize) {
                                    Log.e("WeatherBasedPlaceRecommendation", "executed")

                                    val recommendedPlacesList = mutableListOf<RecommendedPlace>()
                                    var distancesCalculated = 0
                                    allFilteredPlaces.shuffle()
                                    val distinctList = allFilteredPlaces.distinctBy { it.name }

                                    distinctList.forEach { filters ->
                                        Log.e("WeatherBasedPlaceRecommendation", "NAME: ${filters.name}, RATING: ${filters.rating}, NUMBER OF REVIEWS: ${filters.userRatingsTotal}")
                                        checkPlaceDistance(currentLatLng, filters) { calculatedDistanceString ->
                                            val distanceNumericString = calculatedDistanceString.replace("[^\\d.]".toRegex(), "")
                                            val distance = distanceNumericString.toDoubleOrNull() ?: 0.0

                                            val recommendedPlace = RecommendedPlace(
                                                placeId = filters.id ?: "NO PLACE ID",
                                                name = filters.name ?: "Unknown Name",
                                                address = filters.address ?: "Unknown Address",
                                                placeTypes = filters.placeTypes ?: emptyList(),
                                                rating = filters.rating ?: 0.0,
                                                numReviews = filters.userRatingsTotal ?: 0,
                                                distance = distance,
                                                distanceString = calculatedDistanceString,
                                                photoMetadata = filters.photoMetadatas?.firstOrNull()
                                            )

                                            // Add the place to the list and mark its ID as added
                                            recommendedPlacesList.add(recommendedPlace)

                                            distancesCalculated++
                                            Log.e("WeatherBasedPlaceRecommendation", "DISTANCE: $distancesCalculated / ${places.size}")

                                            if (distancesCalculated == distinctList.size) {

                                                Log.e("listers", "Recommended place list size: ${recommendedPlacesList.size}")
                                                recommendedPlacesList.forEach{list->
                                                    Log.e("listers", "NAME: ${list.name}, PLACE ID: ${list.placeId}")

                                                }
                                                if (isForCarousel) {
                                                    setupRecommendationUI(context, placesClient, recommendedPlacesList, recyclerView, titleTextView, supportText)
                                                } else {
                                                    showMealRecommendationBottomSheet(context, recommendedPlacesList, placesClient)
                                                }
                                            }
                                        }


                                    }
                                    callback(allFilteredPlaces)
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("WeatherBasedPlaceRecommendation", "Error during text search: ${exception.message}")
                                completedQueries++ // Increment even on failure
                                // Check if all queries are completed
                                if (completedQueries == currentPlaceTypes.size) {
                                    callback(allFilteredPlaces)
                                }
                            }
                    }
                }
            } ?: run {
                Log.e("WeatherBasedPlaceRecommendation", "Current location is not available.")
                callback(emptyList()) // Return an empty list if the location is not available
            }
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

        val (title, description) = generateRecommendationText(weatherCondition, getTimeOfDay())
        binding.textViewRecommendationTitle.text = title
        binding.textViewRecommendationDescription.text = description



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


    private fun calculateDistance(start: LatLng, end: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return results[0].toDouble()
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
            Log.e("WeatherBasedPlaceRecommendation", "Error fetching city name: ${t.message}")
            distanceCallback("Error fetching city name")
        }
    })
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
































//    private fun createWeatherApiService(): OpenWeatherAPIService {
//        val retrofit = Retrofit.Builder()
//            .baseUrl("https://api.openweathermap.org/")
//            .addConverterFactory(GsonConverterFactory.create())
//            .build()
//
//        return retrofit.create(OpenWeatherAPIService::class.java)
//    }
//
//
//    // Function to fetch weather and provide recommendations
//    fun fetchWeatherAndRecommend(
//        context: Context,
//        onRecommendationsReady: (List<String>?) -> Unit
//    ) {
//        getCurrentLocation(context) { latLng ->
//            latLng?.let {
//                val weatherApiService = createWeatherApiService()
//                val call = weatherApiService.getCurrentWeatherData(
//                    it.latitude,
//                    it.longitude,
//                    apiKey = BuildConfig.OPEN_WEATHER_API
//                )
//
//                call.enqueue(object : Callback<WeatherResponse> {
//                    override fun onResponse(
//                        call: Call<WeatherResponse>,
//                        response: Response<WeatherResponse>
//                    ) {
//                        if (response.isSuccessful) {
//                            val weatherResponse = response.body()
//                            if (weatherResponse != null) {
//                                val weatherCondition = weatherResponse.weather[0].main
//
//                                Log.d("WeatherBasedPlaceRecommendation", response.body().toString())
//
//                                val recommendations =
//                                    mapWeatherToPlaceTypes(weatherCondition)
//                                onRecommendationsReady(recommendations)
//                            } else {
//                                Log.e(
//                                    "WeatherBasedPlaceRecommendation",
//                                    "Weather response is null."
//                                )
//                                onRecommendationsReady(null)
//                            }
//                        } else {
//                            Log.e(
//                                "WeatherBasedPlaceRecommendation",
//                                "API Error: ${response.code()} - ${response.message()}"
//                            )
//                            onRecommendationsReady(null)
//                        }
//                    }
//
//                    override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
//                        Log.e(
//                            "WeatherBasedPlaceRecommendation",
//                            "Network Error: ${t.localizedMessage}"
//                        )
//                        onRecommendationsReady(null)
//                    }
//                })
//            } ?: run {
//                Log.e("WeatherBasedPlaceRecommendation", "Failed to get current location.")
//                onRecommendationsReady(null) // Handle case where location retrieval failed
//            }
//        }
//    }

}
