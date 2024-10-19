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

        // Return the first text part of the first candidate
        return response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.asTextOrNull() ?: ""
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

    private fun checkPlaceTypesValidity(placeTypesFromResponse: String): String {

        var placeTypes = placeTypesFromResponse
        // Log the initial placeType for debugging
        Log.d("AIProcessor", "Initial placeType: $placeTypes")

        // Check if placeType is "Unspecified"
        if (placeTypes == "Unspecified") {
            placeTypes = ""
            Log.e("AIProcessor", "placeType is Unspecified, setting it to empty string.")
        } else {
            // Use the isPlaceTypeFromResponseHasMatch function to check validity
            if (!isPlaceTypeFromResponseHasMatch(placeTypes)) {
                Log.e("AIProcessor", "placeType '$placeTypes' is not valid, setting it to empty string.")
                placeTypes = "" // Set to empty string if not a valid place type
            } else {
                Log.e("AIProcessor", "placeType '$placeTypes' is valid.")
            }
        }

        // Return the validated placeTypes value
        return placeTypes
    }

    private suspend fun handleSearchPlace(userQueryParams: UserQueryParams): List<Place> {
        val placeTypes = checkPlaceTypesValidity(userQueryParams.placeType)
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
                    if (placeTypes.isNotEmpty()) {
                        includedType = placeTypes
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

    private fun isPlaceTypeFromResponseHasMatch(placeTypeFromResponse: String): Boolean {
        val supportedPlaceTypes = listOf(
            // Car-related
            "car_dealer", "car_rental", "car_repair", "car_wash",
            "electric_vehicle_charging_station", "gas_station", "parking", "rest_stop",

            // Farm-related
            "farm",

            // Art and culture
            "art_gallery", "museum", "performing_arts_theater",

            // Education
            "library", "preschool", "primary_school", "secondary_school", "university",

            // Amusement and recreation
            "amusement_center", "amusement_park", "aquarium", "banquet_hall",
            "bowling_alley", "casino", "community_center", "convention_center",
            "cultural_center", "dog_park", "event_venue", "hiking_area",
            "historical_landmark", "marina", "movie_rental", "movie_theater",
            "national_park", "night_club", "park", "tourist_attraction",
            "visitor_center", "wedding_venue", "zoo",

            // Financial
            "accounting", "atm", "bank",

            // Restaurants
            "american_restaurant", "bakery", "bar", "barbecue_restaurant",
            "brazilian_restaurant", "breakfast_restaurant", "brunch_restaurant",
            "cafe", "chinese_restaurant", "coffee_shop", "fast_food_restaurant",
            "french_restaurant", "greek_restaurant", "hamburger_restaurant",
            "ice_cream_shop", "indian_restaurant", "indonesian_restaurant",
            "italian_restaurant", "japanese_restaurant", "korean_restaurant",
            "lebanese_restaurant", "meal_delivery", "meal_takeaway",
            "mediterranean_restaurant", "mexican_restaurant",
            "middle_eastern_restaurant", "pizza_restaurant", "ramen_restaurant",
            "restaurant", "sandwich_shop", "seafood_restaurant",
            "spanish_restaurant", "steak_house", "sushi_restaurant",
            "thai_restaurant", "turkish_restaurant", "vegan_restaurant",
            "vegetarian_restaurant", "vietnamese_restaurant",

            // Administrative
            "administrative_area_level_1", "administrative_area_level_2",
            "country", "locality", "postal_code", "school_district",

            // Government
            "city_hall", "courthouse", "embassy", "fire_station",
            "local_government_office", "police", "post_office",

            // Healthcare
            "dental_clinic", "dentist", "doctor", "drugstore",
            "hospital", "medical_lab", "pharmacy", "physiotherapist", "spa",

            // Lodging
            "bed_and_breakfast", "campground", "camping_cabin", "cottage",
            "extended_stay_hotel", "farmstay", "guest_house", "hostel",
            "hotel", "lodging", "motel", "private_guest_room",
            "resort_hotel", "rv_park",

            // Places of worship
            "church", "hindu_temple", "mosque", "synagogue",

            // Services
            "barber_shop", "beauty_salon", "cemetery", "child_care_agency",
            "consultant", "courier_service", "electrician", "florist",
            "funeral_home", "hair_care", "hair_salon", "insurance_agency",
            "laundry", "lawyer", "locksmith", "moving_company", "painter",
            "plumber", "real_estate_agency", "roofing_contractor", "storage",
            "tailor", "telecommunications_service_provider", "travel_agency",
            "veterinary_care",

            // Retail
            "auto_parts_store", "bicycle_store", "book_store", "cell_phone_store",
            "clothing_store", "convenience_store", "department_store",
            "discount_store", "electronics_store", "furniture_store",
            "gift_shop", "grocery_store", "hardware_store", "home_goods_store",
            "home_improvement_store", "jewelry_store", "liquor_store",
            "market", "pet_store", "shoe_store", "shopping_mall",
            "sporting_goods_store", "store", "supermarket", "wholesaler",

            // Sports and fitness
            "athletic_field", "fitness_center", "golf_course", "gym",
            "playground", "ski_resort", "sports_club", "sports_complex",
            "stadium", "swimming_pool",

            // Transportation
            "airport", "bus_station", "bus_stop", "ferry_terminal",
            "heliport", "light_rail_station", "park_and_ride",
            "subway_station", "taxi_stand", "train_station",
            "transit_depot", "transit_station", "truck_stop"
        )

        return supportedPlaceTypes.contains(placeTypeFromResponse)
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
