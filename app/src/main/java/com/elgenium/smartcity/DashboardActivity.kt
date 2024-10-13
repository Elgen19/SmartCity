package com.elgenium.smartcity

import android.Manifest
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.elgenium.smartcity.contextuals.MealPlaceRecommendationManager
import com.elgenium.smartcity.contextuals.PopularPlaceRecommendationManager
import com.elgenium.smartcity.contextuals.WeatherBasedPlaceRecommendation
import com.elgenium.smartcity.databinding.ActivityDashboardBinding
import com.elgenium.smartcity.models.Event
import com.elgenium.smartcity.models.Leaderboard
import com.elgenium.smartcity.models.RecommendedPlace
import com.elgenium.smartcity.network.OpenWeatherAPIService
import com.elgenium.smartcity.network.RoadsApiService
import com.elgenium.smartcity.network.TomTomApiService
import com.elgenium.smartcity.network_reponses.RoadLocation
import com.elgenium.smartcity.network_reponses.RoadsResponse
import com.elgenium.smartcity.network_reponses.TrafficResponse
import com.elgenium.smartcity.network_reponses.WeatherResponse
import com.elgenium.smartcity.recyclerview_adapter.LeaderboardAdapter
import com.elgenium.smartcity.recyclerview_adapter.RecommendedEventAdapter
import com.elgenium.smartcity.recyclerview_adapter.RecommendedPlaceAdapter
import com.elgenium.smartcity.singletons.BottomNavigationManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.singletons.PlacesNewClientSingleton
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Calendar
import java.util.Locale
import kotlin.math.ln
import kotlin.math.sqrt

@Suppress("PrivatePropertyName")
class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var apiService: OpenWeatherAPIService
    private val placesClient by lazy { PlacesNewClientSingleton.getPlacesClient(this) }
    private lateinit var apiServiceForTraffic: TomTomApiService
    private lateinit var apiServiceForRoads: RoadsApiService
    private val locationRequestCode = 1001
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder
    private val apiKey = BuildConfig.OPEN_WEATHER_API
    private lateinit var locationCallback: LocationCallback
    private lateinit var leaderboardAdapter: LeaderboardAdapter
    private val COOLDOWN_TIME_MS: Long = 60 * 1000 // 1 minute in milliseconds
    private var lastActiveUpdateTime: Long = 0 // To track the last update time
    private val ACTIVE_TIMEFRAME_MS: Long = 2 * 60 * 1000 // 5 minutes in milliseconds
    private var recommendedPlaces: List<RecommendedPlace> = emptyList()
    private lateinit var popularPlaceRecommendationManager: PopularPlaceRecommendationManager
    private lateinit var mealRecommendationManager: MealPlaceRecommendationManager
    private lateinit var weatherRecommendation: WeatherBasedPlaceRecommendation
    private var contextRecommender: Boolean = false
    private var eventRecommender: Boolean = false



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // get the shared preferences
        retrievePreferences()

        // Initialize Retrofit for open weather
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(OpenWeatherAPIService::class.java)

        val retrofitForTraffic = Retrofit.Builder()
            .baseUrl("https://api.tomtom.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiServiceForTraffic = retrofitForTraffic.create(TomTomApiService::class.java)

        // Initialize Retrofit for Roads API
        val retrofitForRoads = Retrofit.Builder()
            .baseUrl("https://roads.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiServiceForRoads = retrofitForRoads.create(RoadsApiService::class.java)


        // Singleton object that will handle bottom navigation functionality
        BottomNavigationManager.setupBottomNavigation(
            this,
            binding.bottomNavigation,
            DashboardActivity::class.java
        )

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference.child("Users")

        val userId = auth.currentUser?.uid ?: "NO UID"
        popularPlaceRecommendationManager = PopularPlaceRecommendationManager(this, userId )
        mealRecommendationManager = MealPlaceRecommendationManager(this)
        weatherRecommendation = WeatherBasedPlaceRecommendation(this)

        binding.notificationButton.setOnClickListener {
            val intent = Intent(this, NotificationHistoryActivity::class.java)
            val options = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            finish()
        }


        fetchWeather()
        updateGreeting()
        loadProfileImage()
        setupListeners()
        fetchNearestRoad(apiServiceForRoads, apiServiceForTraffic)
        fetchLeaderboardData()
        handleViewVisibilityBasedOnSettings()
        //getFilteredPlacesForRecommendationBasedOnType()




    }

    private fun handleViewVisibilityBasedOnSettings() {

        if (contextRecommender) {
            showNextRecommendation()
        } else {
            binding.recommendationLayout.visibility = View.GONE
            val layoutParams = binding.weatherUpdatesTitle.layoutParams as RelativeLayout.LayoutParams
            layoutParams.topMargin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                16f,
                resources.displayMetrics
            ).toInt()
            binding.weatherUpdatesTitle.layoutParams = layoutParams


        }

        if (eventRecommender)  {
            fetchUserPreferencesAndEvents()
        } else {
            binding.recommendedEventsTitle.visibility = View.GONE
        }
    }

    private fun retrievePreferences() {
        val sharedPreferences = getSharedPreferences("user_settings", MODE_PRIVATE)
        contextRecommender = sharedPreferences.getBoolean("context_recommender", false)
        eventRecommender = sharedPreferences.getBoolean("event_recommender", false)
        // Optionally log the retrieved value
        Log.e("Preferences", "contextRecommender at retrievePreferences: $contextRecommender")
        Log.e("Preferences", "eventRecommender at retrievePreferences: $eventRecommender")

    }

    private fun processRecommendationTag(nextTag: String? = null): String {
        val MEAL_TAG = "MEAL"
        val RECOMMENDATION_PREFS = "recommendation_prefs"
        val CURRENT_RECOMMENDATION_KEY = "current_recommendation"

        val sharedPreferences = this.getSharedPreferences(RECOMMENDATION_PREFS, Context.MODE_PRIVATE)

        // If nextTag is provided, save it.
        if (nextTag != null) {
            Log.d("Recommendation", "Setting next tag: $nextTag")
            sharedPreferences.edit().putString(CURRENT_RECOMMENDATION_KEY, nextTag).apply()
        }

        // Retrieve and return the current recommendation tag
        val currentTag = sharedPreferences.getString(CURRENT_RECOMMENDATION_KEY, MEAL_TAG) ?: MEAL_TAG
        Log.d("Recommendation", "Current tag: $currentTag")
        return currentTag
    }

    private fun showNextRecommendation() {
        val currentTag = processRecommendationTag() // Get the current recommendation tag

        when (currentTag) {
            "MEAL" -> {
                Log.d("Recommendation", "Fetching meal recommendations...")
                fetchRecommendedMealPlaces {
                    Log.d("Recommendation", "Meal recommendations fetched.")
                    processRecommendationTag("POPULAR") // Set the next tag
                }
            }
            "POPULAR" -> {
                Log.d("Recommendation", "Fetching popular recommendations...")
                fetchPopularPlaceRecommendations {
                    Log.d("Recommendation", "Popular recommendations fetched.")
                    processRecommendationTag("WEATHER") // Set the next tag
                }
            }

            "WEATHER" -> {
                Log.d("Recommendation", "Fetching popular recommendations...")
                fetchWeatherRecommendations {
                    Log.d("Recommendation", "Popular recommendations fetched.")
                    processRecommendationTag("MEAL") // Set the next tag
                }
            }



        }
    }

    private fun fetchWeatherRecommendations(callback: () -> Unit) {
        weatherRecommendation.fetchWeatherAndRecommend(this) { recommendations ->
            recommendations?.let {
                Log.d("WeatherTestActivity", "Recommendations: $it")

                // Call performTextSearch without modifying its parameters
                weatherRecommendation.performTextSearch(placesClient, this, recommendations, binding.recyclerViewContextRecommendations, binding.textViewRecommendationTitle, binding.textViewRecommendationDescription, true) { _ ->
                    // Pass the results to the provided callback
                    callback()
                }

            } ?: run {
                Log.e("WeatherTestActivity", "No recommendations available.")
                // Call the callback with null or handle the case accordingly
                callback()
            }
        }
    }

    private fun fetchRecommendedMealPlaces(callback: () -> Unit) {
        val mealTime = mealRecommendationManager.getMealTime()
        val recommendedPlaceTypes = mealRecommendationManager.mealTimePlaceMappings[mealTime]

        if (!recommendedPlaceTypes.isNullOrEmpty()) {
            Log.d("Recommendation", "Performing text search for meal places...")
            mealRecommendationManager.performTextSearch(placesClient, recommendedPlaceTypes, this, binding.recyclerViewContextRecommendations, binding.textViewRecommendationTitle, binding.textViewRecommendationDescription, true) {
                Log.d("Recommendation", "Text search for meal places complete.")
                callback() // Invoke the callback once the text search is complete
            }
        } else {
            Log.e("MealRecommendationActivity", "No recommended place types found for meal time: $mealTime")
            callback() // Invoke the callback to continue the loop
        }
    }

    private fun fetchPopularPlaceRecommendations(callback: () -> Unit) {
        Log.d("Recommendation", "Fetching preferred visit places for popular recommendations...")
        popularPlaceRecommendationManager.fetchPreferredVisitPlaces { preferredPlaceTypes ->
            if (preferredPlaceTypes.isNotEmpty()) {
                Log.d("Recommendation", "Performing text search for popular places...")
                popularPlaceRecommendationManager.performTextSearch(
                    placesClient,
                    preferredPlaceTypes,
                    this,
                    binding.recyclerViewContextRecommendations,
                    binding.textViewRecommendationTitle,
                    binding.textViewRecommendationDescription,
                    true
                ) {
                    Log.d("Recommendation", "Text search for popular places complete.")
                    callback() // Invoke the callback once the text search is complete
                }
            } else {
                Log.e("DashboardActivity", "No preferred visit places found for user.")
                callback() // Invoke the callback to continue the loop
            }
        }
    }

    private fun fetchLeaderboardData() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, "User not authenticated", Toast.LENGTH_SHORT).show()
            return
        }

        // Query Users node and order by points to get leaderboard data
        database.orderByChild("points").addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val leaderboardList = mutableListOf<Leaderboard>()

                // Collect user data into the leaderboard list
                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    val name =
                        userSnapshot.child("fullName").getValue(String::class.java) ?: "Unknown"
                    val profileImageUrl =
                        userSnapshot.child("profilePicUrl").getValue(String::class.java)
                    val points = userSnapshot.child("points").getValue(Int::class.java) ?: 0

                    // Only add users with points greater than 0
                    if (points > 0) {
                        leaderboardList.add(Leaderboard(userId, name, profileImageUrl, points))
                    }
                }

                // Sort by points descending and then by userId for tie breaking
                val sortedLeaderboardList = leaderboardList.sortedWith(
                    compareByDescending<Leaderboard> { it.points }
                        .thenBy { it.userId } // Use userId as a tiebreaker
                )

                // Limit to top 5 users
                val limitedLeaderboardData = sortedLeaderboardList.take(5)

                binding.leaderboardRecyclerView.layoutManager =
                    LinearLayoutManager(this@DashboardActivity)
                leaderboardAdapter = LeaderboardAdapter(limitedLeaderboardData)
                binding.leaderboardRecyclerView.adapter = leaderboardAdapter
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DashboardActivity", "Failed to load leaderboard", error.toException())
                Toast.makeText(
                    this@DashboardActivity,
                    "Failed to load leaderboard",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
        .setMinUpdateIntervalMillis(5000) // Set the fastest interval
        .build()


    private fun fetchNearestRoad(
        roadsApiService: RoadsApiService,
        trafficApiService: TomTomApiService
    ) {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Initialize LocationCallback
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        val latitude = location.latitude
                        val longitude = location.longitude
                        val path = "$latitude,$longitude"

                        Log.d(
                            "DashboardActivity",
                            "Location retrieved: Lat $latitude, Long $longitude"
                        )

                        roadsApiService.getSnappedRoads(path, BuildConfig.MAPS_API_KEY)
                            .enqueue(object : Callback<RoadsResponse> {
                                override fun onResponse(
                                    call: Call<RoadsResponse>,
                                    response: Response<RoadsResponse>
                                ) {
                                    if (response.isSuccessful) {
                                        val snappedPoints =
                                            response.body()?.snappedPoints ?: emptyList()
                                        Log.d(
                                            "DashboardActivity",
                                            "Roads response: ${response.body()}"
                                        )
                                        if (snappedPoints.isNotEmpty()) {
                                            val roadLocation = snappedPoints.first().location
                                            Log.d(
                                                "DashboardActivity",
                                                "Road location: $roadLocation"
                                            )

                                            // Fetch traffic data using the obtained road location
                                            fetchTrafficData(trafficApiService, roadLocation)

                                            // Use these coordinates to get the address
                                            val address =
                                                getStreetNameFromCoordinates(latitude, longitude)
                                            binding.roadName.text = address
                                            Log.d("DashboardActivity", "Address: $address")

                                            // Stop location updates once you have the location
                                            fusedLocationClient.removeLocationUpdates(
                                                locationCallback
                                            )
                                        } else {
                                            Log.e(
                                                "DashboardActivity",
                                                "No snapped points found in response"
                                            )
                                            Toast.makeText(
                                                this@DashboardActivity,
                                                "No roads found near your location",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    } else {
                                        Log.e(
                                            "DashboardActivity",
                                            "Failed to get roads data: ${response.message()}"
                                        )
                                        Toast.makeText(
                                            this@DashboardActivity,
                                            "Failed to get roads data",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }

                                override fun onFailure(call: Call<RoadsResponse>, t: Throwable) {
                                    Log.e("DashboardActivity", "Error fetching roads data", t)
                                    Toast.makeText(
                                        this@DashboardActivity,
                                        "Error: ${t.message}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            })
                    } else {
                        Log.e("DashboardActivity", "Location is null in LocationCallback")
                        Toast.makeText(
                            this@DashboardActivity,
                            "Unable to retrieve location",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }

            // Request location updates
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            Log.e("DashboardActivity", "Location permission not granted")
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationRequestCode
            )
        }
    }


    private fun getStreetNameFromCoordinates(latitude: Double, longitude: Double): String? {
        val geocoder = Geocoder(this, Locale.getDefault())
        Log.d(
            "DashboardActivity",
            "Starting geocoding for coordinates: Latitude = $latitude, Longitude = $longitude"
        )

        return try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val streetName =
                    address.thoroughfare ?: "Street name not found" // Get the street name

                Log.d("DashboardActivity", "Street name found: $streetName")
                streetName
            } else {
                Log.w("DashboardActivity", "No address found for the given coordinates.")
                "No address found"
            }
        } catch (e: Exception) {
            Log.e("DashboardActivity", "Geocoding failed due to an exception: ${e.message}", e)
            "Geocoder failed"
        }
    }

    private fun fetchTrafficData(trafficApiService: TomTomApiService, roadLocation: RoadLocation) {
        val point = "${roadLocation.latitude},${roadLocation.longitude}"
        trafficApiService.getTrafficData(BuildConfig.TOM_TOM_TRAFFIC_API, point)
            .enqueue(object : Callback<TrafficResponse> {
                override fun onResponse(
                    call: Call<TrafficResponse>,
                    response: Response<TrafficResponse>
                ) {
                    if (response.isSuccessful) {
                        val trafficData = response.body()
                        if (trafficData != null) {
                            val flowSegmentData = trafficData.flowSegmentData
                            Log.d("DashboardActivity", "Segment Data: $flowSegmentData")
                            if (flowSegmentData != null) {
                                val currentSpeed = flowSegmentData.currentSpeed
                                val freeFlowSpeed = flowSegmentData.freeFlowSpeed
                                val currentTravelTime = flowSegmentData.currentTravelTime
                                val freeFlowTravelTime = flowSegmentData.freeFlowTravelTime
                                val roadClosure = flowSegmentData.roadClosure

                                // Classify the traffic
                                val trafficStatus = classifyTraffic(currentSpeed, freeFlowSpeed)
                                Log.d("DashboardActivity", "Traffic Status: $trafficStatus")

                                // Update UI based on traffic status
                                when (trafficStatus) {
                                    "Light Traffic" -> {
                                        Log.d("DashboardActivity", "Traffic Status: LIGHT")
                                        binding.trafficStatus.text = getString(R.string.light)
                                        binding.trafficStatus.setTextColor(
                                            ContextCompat.getColor(
                                                applicationContext,
                                                R.color.traffic_light_color
                                            )
                                        )
                                    }

                                    "Moderate Traffic" -> {
                                        Log.d("DashboardActivity", "Traffic Status: MODERATE")
                                        binding.trafficStatus.text = getString(R.string.moderate)
                                        binding.trafficStatus.setTextColor(
                                            ContextCompat.getColor(
                                                applicationContext,
                                                R.color.traffic_moderate_color
                                            )
                                        )
                                    }

                                    "Heavy Traffic" -> {
                                        Log.d("DashboardActivity", "Traffic Status: HEAVY")
                                        binding.trafficStatus.text = getString(R.string.heavy)
                                        binding.trafficStatus.setTextColor(
                                            ContextCompat.getColor(
                                                applicationContext,
                                                R.color.traffic_heavy_color
                                            )
                                        )
                                    }
                                }


                                Log.d("DashboardActivity", "Current Speed: $currentSpeed km/h")
                                Log.d("DashboardActivity", "Free Flow Speed: $freeFlowSpeed km/h")
                                Log.d(
                                    "DashboardActivity",
                                    "Current Travel Time: $currentTravelTime seconds"
                                )
                                Log.d(
                                    "DashboardActivity",
                                    "Free Flow Travel Time: $freeFlowTravelTime seconds"
                                )
                                Log.d("DashboardActivity", "Road Closure: $roadClosure")

                                // Update UI with the traffic data
                                binding.currentSpeedValue.text =
                                    getString(R.string.current_speed_format, currentSpeed)
                                binding.freeFlowSpeedValue.text =
                                    getString(R.string.free_flow_speed_format, freeFlowSpeed)
                                binding.freeFlowTravelTimeValue.text = getString(
                                    R.string.free_flow_travel_time_format,
                                    freeFlowTravelTime
                                )
                                binding.currentTravelTimeValue.text = getString(
                                    R.string.current_travel_time_format,
                                    currentTravelTime
                                )
                                binding.roadClosureValue.text =
                                    if (roadClosure == true) "Closed" else "Open"
                            }
                        }
                    } else {
                        Log.e(
                            "DashboardActivity",
                            "Failed to get traffic data: ${response.message()}"
                        )
                        Toast.makeText(
                            this@DashboardActivity,
                            "Failed to get traffic data",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onFailure(call: Call<TrafficResponse>, t: Throwable) {
                    Log.e("DashboardActivity", "Error fetching traffic data", t)
                    Toast.makeText(
                        this@DashboardActivity,
                        "Error: ${t.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
    }

    private fun classifyTraffic(currentSpeed: Double?, freeFlowSpeed: Double?): String {
        if (currentSpeed == null || freeFlowSpeed == null) {
            return "Unknown"
        }

        // Calculate the percentage difference
        val speedRatio = currentSpeed / freeFlowSpeed

        // Define thresholds for classification
        return when {
            speedRatio >= 0.8 -> "Light Traffic"   // 80% or more of free flow speed
            speedRatio >= 0.5 -> "Moderate Traffic" // 50% to 79% of free flow speed
            else -> "Heavy Traffic"                // Less than 50% of free flow speed
        }
    }

    private fun fetchWeather() {
        Log.d("DashboardActivity", "Fetching weather")
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {

            // Use the class-level locationRequest
            val locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    // Called when location results are available
                    val location = locationResult.lastLocation
                    if (location != null) {
                        Log.d(
                            "DashboardActivity",
                            "Location retrieved: Lat ${location.latitude}, Long ${location.longitude}"
                        )
                        // Proceed with weather fetching
                        handleUserLocationAndWeatherFetching(location)
                        // Remove updates once location is fetched
                        fusedLocationClient.removeLocationUpdates(this)
                    } else {
                        Log.e("DashboardActivity", "Location is null")
                    }
                }
            }

            // Request location updates using the existing locationRequest
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } else {
            Log.e("DashboardActivity", "Location permission not granted")
            requestPermissions(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                locationRequestCode
            )
        }
    }

    private fun handleUserLocationAndWeatherFetching(location: Location) {
        Log.d(
            "DashboardActivity",
            "Location retrieved: Lat ${location.latitude}, Long ${location.longitude}"
        )

        val latitude = location.latitude
        val longitude = location.longitude

        // Get the detailed address from the location
        val addressList = geocoder.getFromLocation(latitude, longitude, 1)
        val address = addressList?.firstOrNull()
        val preciseAddress = address?.getAddressLine(0) ?: "Unknown Location"
        val cityName = address?.locality ?: "Unknown City"

        Log.d("DashboardActivity", "Precise address: $preciseAddress")
        Log.d("DashboardActivity", "City Name: $cityName")


        // Update the API call to use latitude and longitude
        apiService.getCurrentWeatherData(latitude, longitude, apiKey= apiKey).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(
                call: Call<WeatherResponse>,
                response: Response<WeatherResponse>
            ) {
                if (response.isSuccessful) {
                    val weatherResponse = response.body()
                    weatherResponse?.let {
                        val weatherDescription = it.weather.firstOrNull()?.description ?: "Unknown"
                        val temperature = it.main.temp
                        val heatIndex = it.main.feels_like
                        val iconCode = it.weather.firstOrNull()?.icon ?: "01d"
                        val iconUrl = "https://openweathermap.org/img/wn/$iconCode.png"

                        Log.d("DashboardActivity", "Weather description: $weatherDescription")
                        Log.d(
                            "DashboardActivity",
                            "Temperature: $temperature, Heat index: $heatIndex"
                        )
                        Log.d("DashboardActivity", "Icon URL: $iconUrl")

                        binding.weatherStatusText.text =
                            weatherDescription.replaceFirstChar { char ->
                                if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
                            }
                        binding.temperatureText.text =
                            getString(R.string.temperature_format, temperature)
                        binding.heatIndexValue.text =
                            getString(R.string.temperature_format, heatIndex)
                        binding.locationText.text = preciseAddress // Keep the precise address

                        binding.cityNameText.text = cityName

                        // Check if the activity is still alive before loading the image
                        if (!isFinishing && !isDestroyed) {
                            Glide.with(this@DashboardActivity)
                                .load(iconUrl)
                                .placeholder(R.drawable.cloud) // Optional placeholder
                                .into(binding.weatherIcon)
                        } else {
                            Log.w(
                                "DashboardActivity",
                                "Activity is destroyed or finishing, not loading image."
                            )
                        }
                    }
                } else {
                    Log.e("DashboardActivity", "Failed to get weather data: ${response.message()}")
                    Toast.makeText(
                        this@DashboardActivity,
                        "Failed to get weather data",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                Log.e("DashboardActivity", "Error fetching weather data", t)
                Toast.makeText(this@DashboardActivity, "Error: ${t.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        })
    }

    private fun setupListeners() {
        binding.profileImage.setOnClickListener {
            val intent = Intent(this, ProfileActivity::class.java)
            val options = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            finish()
        }


    }

    private fun updateGreeting() {
        val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        val greeting = when {
            currentHour == 12 -> getString(R.string.good_noon)
            currentHour < 12 -> getString(R.string.good_morning)
            currentHour < 17 -> getString(R.string.good_afternoon)
            else -> getString(R.string.good_evening)
        }
        binding.greetingText.text = greeting
    }

    private fun loadProfileImage() {
        val userId = auth.currentUser?.uid ?: return
        database.child(userId).get().addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val photoUrl = snapshot.child("profilePicUrl").getValue(String::class.java)
                val fullName = snapshot.child("fullName").getValue(String::class.java)
                val firstName = fullName?.split(" ")?.firstOrNull() ?: "User"

                if (photoUrl != null) {
                    // Ensure the activity is not finishing or destroyed before loading the image
                    if (!isDestroyed && !isFinishing) {
                        Glide.with(this)  // Use lifecycle-aware Glide
                            .load(photoUrl)
                            .placeholder(R.drawable.female) // Placeholder if needed
                            .into(binding.profileImage)
                    }
                }
                // Set the first name in the user name text view
                binding.userNameText.text = firstName

            } else {
                Toast.makeText(this, "User data not found", Toast.LENGTH_SHORT).show()
            }
        }.addOnFailureListener {
            Toast.makeText(this, "Failed to load profile picture", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == locationRequestCode) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                fetchWeather()
                fetchNearestRoad(apiServiceForRoads, apiServiceForTraffic)
            } else {
                Toast.makeText(
                    this,
                    "Please enable location permission to use the app",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun checkLocationSettings() {
        if (!isLocationEnabled()) {
            val dialogView: View =
                LayoutInflater.from(this).inflate(R.layout.location_permission_dialog, null)
            val dialogBuilder = AlertDialog.Builder(this)
            dialogBuilder.setView(dialogView)

            // Create the AlertDialog instance
            val alertDialog = dialogBuilder.create()

            val positiveButton: Button = dialogView.findViewById(R.id.positive_button)
            val negativeButton: Button = dialogView.findViewById(R.id.negative_button)

            positiveButton.setOnClickListener {
                val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
                startActivity(intent)
                alertDialog.dismiss()
            }

            negativeButton.setOnClickListener {
                alertDialog.dismiss()
                finish()
            }

            alertDialog.setCancelable(false)
            alertDialog.show()
        }
    }

    override fun onResume() {
        super.onResume()
        checkLocationSettings()


        // Update last active timestamp
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastActiveUpdateTime >= COOLDOWN_TIME_MS) {
            val userId = FirebaseAuth.getInstance().currentUser?.uid
            val userRef = FirebaseDatabase.getInstance().getReference("Users/$userId")
            userRef.child("lastActive").setValue(currentTime)
            lastActiveUpdateTime = currentTime // Update the last update time
        }

        // Count active users
        countActiveUsers() // This will update the active user count
    }

    private fun countActiveUsers() {
        val cutoffTime =
            (System.currentTimeMillis() - ACTIVE_TIMEFRAME_MS).toDouble() // Convert to Double
        val usersRef = FirebaseDatabase.getInstance().getReference("Users")

        usersRef.orderByChild("lastActive").startAt(cutoffTime)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val activeUserCount = snapshot.childrenCount // Count of active users
                    Log.e("DashboardActivity", "ACTIVE USERS: $activeUserCount")
                }

                override fun onCancelled(error: DatabaseError) {
                    // Handle error if needed
                }
            })
    }

    private fun getFilteredPlacesForRecommendationBasedOnType() {
        Log.e("DashboardActivity", "Fetching user preferences for recommendations...")
        fetchUserPreferences { preferredPlaces ->
            if (preferredPlaces.isEmpty()) {
                Log.e("DashboardActivity", "No preferred places found for recommendations.")
                return@fetchUserPreferences
            }

            Log.e("DashboardActivity", "Preferred places: $preferredPlaces")

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

            val query = preferredPlaces.joinToString(" OR ")
            Log.e("DashboardActivity", "Search query for places: $query")

            val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                .setMaxResultCount(20)
                .build()

            placesClient.searchByText(searchByTextRequest)
                .addOnSuccessListener { response ->
                    Log.e(
                        "DashboardActivity",
                        "Successfully retrieved places. Total places found: ${response.places.size}"
                    )

                    val places: List<Place> = response.places
                    val placesList = mutableListOf<RecommendedPlace>()

                    // Get the current user location
                    getCurrentLocation { userLocation ->
                        Log.e("DashboardActivity", "User location retrieved: $userLocation")

                        places.forEach { place ->
                            // Ensure place.types is not null and convert to a list of strings
                            val placeTypes = place.types?.map { it.toString() } ?: emptyList()

                            // Calculate the score based on the weighted sum
                            val score =
                                calculateScore(place, placeTypes, preferredPlaces, userLocation)
                            Log.e("DashboardActivity", "Place: ${place.name}, Score: $score")

                            val placeModel = place.id?.let {
                                RecommendedPlace(
                                    placeId = it,
                                    name = place.name ?: "",
                                    address = place.address ?: "",
                                    placeTypes = placeTypes,
                                    score = score, // Assign calculated score
                                    rating = place.rating ?: 0.0, // Store rating
                                    numReviews = place.userRatingsTotal
                                        ?: 0, // Store number of reviews
                                    distance = calculateDistance(
                                        userLocation,
                                        place.latLng ?: LatLng(0.0, 0.0)
                                    ), // Store distance
                                    photoMetadata = place.photoMetadatas?.firstOrNull()

                                )
                            }
                            if (placeModel != null) {
                                placesList.add(placeModel)
                            }
                        }

                        // Sort places based on the calculated score in descending order
                        placesList.sortByDescending { it.score }

                        // Get top 10 places based on score
                        val topPlaces = placesList.take(10)
                        Log.e("DashboardActivity", "Top recommended places: $topPlaces")

                        // Update the places variable
                        recommendedPlaces = topPlaces

                        // Set up RecyclerView with an empty adapter initially
                        binding.recyclerViewPlaces.layoutManager =
                            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                        // Update RecyclerView with new data
                        binding.recyclerViewPlaces.adapter =
                            RecommendedPlaceAdapter(recommendedPlaces, true, placesClient) { place ->
                                // Handle the click event for the place here
                                Toast.makeText(this, "Clicked: ${place.name}", Toast.LENGTH_SHORT)
                                    .show()

                                // Create an Intent to navigate to PlacesActivity
                                val intent = Intent(this, PlacesActivity::class.java)

                                // Put the placeId of the clicked item as an extra in the Intent
                                intent.putExtra("DASHBOARD_RECOMMENDED_PLACE_ID", place.placeId)

                                // Start the new activity
                                startActivity(intent)
                            }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("DashboardActivity", "Error retrieving places", exception)
                }
        }
    }

    private fun calculateScore(
        place: Place,
        placeTypes: List<String>,
        preferredPlaces: List<String>,
        userLocation: LatLng
    ): Double {
        // Define weights for the preferred types
        val weights = mutableMapOf<String, Double>().apply {
            // Initialize base weight for all place types
            placeTypes.forEach { type -> this[type] = 0.5 } // Base weight
            preferredPlaces.forEach { placeType ->
                this[placeType] =
                    (this[placeType] ?: 0.5) + 0.5 // Increase weight for preferred types
            }
        }

        // Extract ratings and number of reviews
        val rating = place.rating ?: 0.0
        val numReviews = place.userRatingsTotal ?: 0

        // Calculate distance (if latitude and longitude are available)
        val placeLocation = place.latLng
        val distance = placeLocation?.let { calculateDistance(userLocation, it) } ?: 0.0

        // Calculate the score based on the weighted sum
        return placeTypes.sumOf { type -> weights[type] ?: 0.0 } +
                (rating * 0.5) +  // Weight for rating
                (numReviews / 100.0) - // Normalize number of reviews
                (distance / 1000.0) // Normalize distance (in kilometers
    }

    private fun getCurrentLocation(callback: (LatLng) -> Unit) {
        // Check if the location permission is granted
        if (ContextCompat.checkSelfPermission(
                this,
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
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("DashboardActivity", "Failed to get location", exception)
                }
        } else {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        }
    }

    private fun calculateDistance(start: LatLng, end: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(
            start.latitude,
            start.longitude,
            end.latitude,
            end.longitude,
            results
        )
        return results[0].toDouble() // Distance in meters
    }


    private fun fetchUserPreferences(callback: (preferredPlaces: List<String>) -> Unit) {
        val userId = auth.currentUser?.uid
        val database = FirebaseDatabase.getInstance().reference
        val userPreferencesRef =
            userId?.let { database.child("Users").child(it).child("preferredVisitPlaces") }

        userPreferencesRef?.get()?.addOnSuccessListener { snapshot ->
            if (snapshot.exists()) {
                val preferencesList = mutableListOf<String>()
                snapshot.children.forEach { it ->
                    val placeType = it.getValue(String::class.java)
                    placeType?.let { preferencesList.add(it) }
                }
                callback(preferencesList)
            } else {
                Log.e("DashboardActivity", "No preferences found for user.")
                callback(emptyList()) // Return empty list if no preferences are found
            }
        }?.addOnFailureListener { exception ->
            Log.e("DashboardActivity", "Error fetching preferences: ", exception)
        }
    }

    private fun fetchUserPreferencesAndEvents() {
        getUserPreferences(
            onSuccess = { userPreferences ->
                Log.d("DashboardActivity", "User Preferences: $userPreferences")
                getEventData(
                    onSuccess = { events ->
                        Log.e("DashboardActivity", "Events fetched: ${events.size}")
                        Log.e("DashboardActivity", "Events all: $events")

//                        // Generate recommendations
//                        val filteredEvents = events.filter { event ->
//                            userPreferences.contains(event.eventCategory)
//                        }

                        val recommendedEvents = recommendEvents(events, userPreferences)
                        val eventAdapter = RecommendedEventAdapter(recommendedEvents)
                        // Set up the adapter with recommended events
                        binding.recyclerViewEvents.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
                        binding.recyclerViewEvents.adapter = eventAdapter

                    },
                    onFailure = { exception ->
                        Log.e("DashboardActivity", "Failed to fetch events: ${exception.message}")
                    }
                )
            },
            onFailure = { exception ->
                Log.e("DashboardActivity", "Failed to fetch user preferences: ${exception.message}")
            }
        )
    }


    private fun getUserPreferences(onSuccess: (List<String>) -> Unit, onFailure: (Exception) -> Unit) {
        val userId = auth.currentUser?.uid ?: "NO USER ID"
        val database = FirebaseDatabase.getInstance().getReference("Users").child(userId).child("preferredEvents")
        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val preferences = mutableListOf<String>()
                for (snapshot in dataSnapshot.children) {
                    val preference = snapshot.getValue(String::class.java)
                    preference?.let { preferences.add(it) }
                }
                onSuccess(preferences)
                Log.e("DashboardActivity", "PREFERENCES: $preferences")
            }

            override fun onCancelled(databaseError: DatabaseError) {
                onFailure(databaseError.toException())
            }
        })
    }

    private fun getEventData(onSuccess: (List<Event>) -> Unit, onFailure: (Exception) -> Unit) {
        val database = FirebaseDatabase.getInstance().getReference("Events")

        database.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(dataSnapshot: DataSnapshot) {
                val events = mutableListOf<Event>()
                for (snapshot in dataSnapshot.children) {
                    val event = snapshot.getValue(Event::class.java)
                    event?.let { events.add(it) }
                }
                onSuccess(events)
            }

            override fun onCancelled(databaseError: DatabaseError) {
                onFailure(databaseError.toException())
            }
        })
    }


    private fun expandVector(userVector: Map<String, Double>, synonyms: Map<String, List<String>>): Map<String, Double> {
        val expandedVector = userVector.toMutableMap()

        userVector.forEach { (key, weight) ->
            synonyms[key]?.forEach { synonym ->
                expandedVector[synonym] = expandedVector.getOrDefault(synonym, 0.0) + (weight * 0.5) // Add 50% weight to synonyms
            }
        }

        return expandedVector
    }

    private fun containsPartialMatch(text: String?, keyword: String): Boolean {
        if (text.isNullOrEmpty() || keyword.isEmpty()) return false

        // Trim whitespace and check for exact or partial match
        val trimmedText = text.trim()
        val matches = trimmedText.contains(keyword.trim(), ignoreCase = true)

        // Debugging log
        Log.d("DashboardActivity", "Text: '$trimmedText', Keyword: '$keyword', Matches Found: $matches")

        return matches
    }

    private fun recommendEvents(
        events: List<Event>,
        userPreferences: List<String>,
        topN: Int = 5
    ): List<Event> {
        val eventSynonyms = mapOf(
            "Concerts & Live Performances" to listOf(
                "concert", "live performance", "music event", "gig", "band",
                "musical", "show", "musical performance", "live show",
                "orchestra", "symphony"
            ),
            "Festivals & Celebrations" to listOf(
                "festival", "celebration", "carnival", "fair", "parade",
                "cultural festival", "local festival", "seasonal festival",
                "block party", "holiday celebration", "community festival",
                "street fair", "food festival"
            ),
            "Sales & Promotions" to listOf(
                "sale", "promotion", "discount", "clearance", "deal",
                "bargain", "offer", "markdown", "limited time offer",
                "price drop", "special offer", "sale event", "flash sale"
            ),
            "Workshops & Seminars" to listOf(
                "workshop", "seminar", "class", "course", "training",
                "learning session", "educational event", "panel discussion",
                "lecture", "skills training", "hands-on workshop"
            ),
            "Community Events" to listOf(
                "community event", "local event", "neighborhood gathering",
                "public gathering", "town hall meeting", "meetup",
                "social event", "charity event", "volunteer opportunity",
                "outreach event", "community outreach"
            ),
            "Outdoor & Adventure Events" to listOf(
                "outdoor event", "adventure", "hiking", "camping", "outdoor",
                "nature walk", "exploration", "trail running",
                "rock climbing", "picnic", "outdoor concert",
                "scavenger hunt", "sports event"
            )
        )

        // Step 2: Apply the filter to events based on user preferences with logging
        val filteredEvents = events.filter { event ->
            userPreferences.any { preference ->
                // Use equals for category matching to ensure exact match
                val categoryMatches = event.eventCategory?.equals(preference, ignoreCase = true) == true

                // Check for matches in synonyms, description, and name
                val descriptionMatches = containsPartialMatch(event.eventDescription, preference)
                val nameMatches = containsPartialMatch(event.eventName, preference)

                // Log the match results
                Log.d("DashboardActivity", "Checking event: ${event.eventName}, Category: ${event.eventCategory}, User Preference: $preference")
                Log.d("DashboardActivity", "categoryMatches: $categoryMatches, descriptionMatches: $descriptionMatches, nameMatches: $nameMatches")

                // Return true if any of the matches are true
                categoryMatches || descriptionMatches || nameMatches
            }
        }

        // Debugging log to show the filtered result count
        Log.d("DashboardActivity", "Filtered Events: ${filteredEvents.size}")

        // Log the filtered events
        Log.d("DashboardActivity", "Filtered Events: ${filteredEvents.map { it.eventName }}")

        // If no events match preferences, return empty list
        // Return default recommendations if no events match preferences
        if (filteredEvents.isEmpty()) {
            Log.d("DashboardActivity", "No events found matching user preferences.")
            return events.take(topN) // Return top N default events if no matches are found
        }

        val tfIdfScores = mutableMapOf<String, MutableMap<String, Double>>() // eventId -> (word -> score)

        // Step 2: Build the term frequency for each filtered event
        filteredEvents.forEach { event ->
            val words = extractWords(event)
            Log.d("DashboardActivity", "Event: ${event.eventName}, Words: $words") // Log event words

            // Check if event.checker is not null
            val checkerKey = event.checker ?: return@forEach // Skip if checker is null
            if (tfIdfScores[checkerKey] == null) {
                tfIdfScores[checkerKey] = mutableMapOf()
            }

            words.forEach { word ->
                tfIdfScores[checkerKey]!![word] = tfIdfScores[checkerKey]!!.getOrDefault(word, 0.0) + 1.0
            }
        }

        // Step 3: Calculate TF-IDF scores
        tfIdfScores.forEach { (eventId, wordMap) ->
            val totalWords = wordMap.values.sum()
            wordMap.forEach { (word, count) ->
                val tf = count / totalWords
                val idf = calculateIdf(word, filteredEvents) // Pass filteredEvents for IDF calculation
                // Only set TF-IDF if IDF is not zero
                if (idf > 0) {
                    wordMap[word] = tf * idf
                    Log.d("DashboardActivity", "Event ID: $eventId, Word: $word, TF: $tf, IDF: $idf, TF-IDF: ${tf * idf}") // Log TF-IDF scores
                } else {
                    wordMap[word] = 0.0
                    Log.d("DashboardActivity", "Event ID: $eventId, Word: $word, TF: $tf, IDF: $idf, TF-IDF: 0.0 (IDF is zero)") // Log zero TF-IDF
                }
            }
        }

        // Step 4: Expand the user vector with synonyms
        val expandedUserVector = expandVector(userPreferences.groupingBy { it }.eachCount().mapValues { it.value.toDouble() }, eventSynonyms)

        // Step 5: Calculate cosine similarity between user preferences and filtered events
        val recommendations = mutableListOf<Pair<Event, Double>>() // event -> similarity score
        filteredEvents.forEach { event ->
            val cosineSimilarity = calculateCosineSimilarity(tfIdfScores[event.checker], expandedUserVector)
            recommendations.add(Pair(event, cosineSimilarity))
            Log.d("DashboardActivity", "Event: ${event.eventName}, Cosine Similarity: $cosineSimilarity") // Log cosine similarity
        }

        // Step 6: Sort recommendations by similarity and return top N
        val topRecommendations = recommendations.sortedByDescending { it.second }.take(topN).map { it.first }
        Log.d("DashboardActivity", "Top $topN Recommendations: ${topRecommendations.map { it.eventName }}") // Log top recommendations

        return topRecommendations
    }



    private fun extractWords(event: Event): List<String> {
        val words = mutableListOf<String>()
        event.eventName?.let { words.addAll(it.split("\\W+".toRegex())) }
        event.eventDescription?.let { words.addAll(it.split("\\W+".toRegex())) }
        return words.map { it.lowercase() } // Normalize to lowercase
    }


    private fun calculateIdf(word: String, events: List<Event>): Double {
        val totalEvents = events.size.toDouble()
        val count = events.count { event -> extractWords(event).contains(word.lowercase()) }.toDouble()

        // Use a smoothing constant to prevent division by zero and extreme values
        val idf = ln((totalEvents + 1) / (count + 1))
        Log.d("DashboardActivity", "Word: $word, IDF: $idf") // Log IDF value
        return idf
    }


    private fun calculateCosineSimilarity(eventScores: Map<String, Double>?, userVector: Map<String, Double>): Double {
        if (eventScores == null) return 0.0

        val dotProduct = eventScores.keys.intersect(userVector.keys).sumOf { key ->
            eventScores[key]!! * userVector[key]!!
        }
        val eventMagnitude = sqrt(eventScores.values.sumOf { it * it })
        val userMagnitude = sqrt(userVector.values.sumOf { it * it })

        val similarity = if (eventMagnitude > 0 && userMagnitude > 0) {
            dotProduct / (eventMagnitude * userMagnitude)
        } else {
            0.0
        }

        Log.d("DashboardActivity", "Event Scores: $eventScores, User Vector: $userVector, Cosine Similarity: $similarity") // Log event and user vector
        return similarity
    }



}
