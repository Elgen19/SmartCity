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
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.elgenium.smartcity.databinding.ActivityDashboardBinding
import com.elgenium.smartcity.models.Leaderboard
import com.elgenium.smartcity.network.OpenWeatherApiService
import com.elgenium.smartcity.network.RoadsApiService
import com.elgenium.smartcity.network.TomTomApiService
import com.elgenium.smartcity.network_reponses.RoadLocation
import com.elgenium.smartcity.network_reponses.RoadsResponse
import com.elgenium.smartcity.network_reponses.TrafficResponse
import com.elgenium.smartcity.network_reponses.WeatherResponse
import com.elgenium.smartcity.recyclerview_adapter.LeaderboardAdapter
import com.elgenium.smartcity.singletons.BottomNavigationManager
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
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

class DashboardActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDashboardBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var apiService: OpenWeatherApiService
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


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // Initialize Retrofit for open weather
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(OpenWeatherApiService::class.java)

        val retrofitForTraffic = Retrofit.Builder()
            .baseUrl("https://api.tomtom.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        apiServiceForTraffic  = retrofitForTraffic.create(TomTomApiService::class.java)

        // Initialize Retrofit for Roads API
        val retrofitForRoads = Retrofit.Builder()
            .baseUrl("https://roads.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiServiceForRoads = retrofitForRoads.create(RoadsApiService::class.java)


        // Singleton object that will handle bottom navigation functionality
        BottomNavigationManager.setupBottomNavigation(this, binding.bottomNavigation, DashboardActivity::class.java)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference.child("Users")

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

        // Call methods to get data
        fetchNearestRoad(apiServiceForRoads, apiServiceForTraffic)

        fetchLeaderboardData()
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
                    val name = userSnapshot.child("fullName").getValue(String::class.java) ?: "Unknown"
                    val profileImageUrl = userSnapshot.child("profilePicUrl").getValue(String::class.java)
                    val points = userSnapshot.child("points").getValue(Int::class.java) ?: 0

                    // Only add users with points greater than 0
                    if (points > 0) {
                        leaderboardList.add(Leaderboard(userId, name, profileImageUrl, points))
                    }
                }

                // Sort by points descending and then by userId for tiebreaking
                val sortedLeaderboardList = leaderboardList.sortedWith(
                    compareByDescending<Leaderboard> { it.points }
                        .thenBy { it.userId } // Use userId as a tiebreaker
                )

                // Limit to top 5 users
                val limitedLeaderboardData = sortedLeaderboardList.take(5)

                binding.leaderboardRecyclerView.layoutManager = LinearLayoutManager(this@DashboardActivity)
                leaderboardAdapter = LeaderboardAdapter(limitedLeaderboardData)
                binding.leaderboardRecyclerView.adapter = leaderboardAdapter
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("DashboardActivity", "Failed to load leaderboard", error.toException())
                Toast.makeText(this@DashboardActivity, "Failed to load leaderboard", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private val locationRequest = LocationRequest.create().apply {
        interval = 10000 // 10 seconds
        fastestInterval = 5000 // 5 seconds
        priority = LocationRequest.PRIORITY_HIGH_ACCURACY
    }

    private fun fetchNearestRoad(roadsApiService: RoadsApiService, trafficApiService: TomTomApiService) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Initialize LocationCallback
            locationCallback = object : LocationCallback() {
                override fun onLocationResult(locationResult: LocationResult) {
                    val location = locationResult.lastLocation
                    if (location != null) {
                        val latitude = location.latitude
                        val longitude = location.longitude
                        val path = "$latitude,$longitude"

                        Log.d("DashboardActivity", "Location retrieved: Lat $latitude, Long $longitude")

                        roadsApiService.getSnappedRoads(path, BuildConfig.MAPS_API_KEY).enqueue(object : Callback<RoadsResponse> {
                            override fun onResponse(call: Call<RoadsResponse>, response: Response<RoadsResponse>) {
                                if (response.isSuccessful) {
                                    val snappedPoints = response.body()?.snappedPoints ?: emptyList()
                                    Log.d("DashboardActivity", "Roads response: ${response.body()}")
                                    if (snappedPoints.isNotEmpty()) {
                                        val roadLocation = snappedPoints.first().location
                                        Log.d("DashboardActivity", "Road location: $roadLocation")

                                        // Fetch traffic data using the obtained road location
                                        fetchTrafficData(trafficApiService, roadLocation)

                                        // Use these coordinates to get the address
                                        val address = getStreetNameFromCoordinates(latitude, longitude)
                                        binding.roadName.text = address
                                        Log.d("DashboardActivity", "Address: $address")

                                        // Stop location updates once you have the location
                                        fusedLocationClient.removeLocationUpdates(locationCallback)
                                    } else {
                                        Log.e("DashboardActivity", "No snapped points found in response")
                                        Toast.makeText(this@DashboardActivity, "No roads found near your location", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Log.e("DashboardActivity", "Failed to get roads data: ${response.message()}")
                                    Toast.makeText(this@DashboardActivity, "Failed to get roads data", Toast.LENGTH_SHORT).show()
                                }
                            }

                            override fun onFailure(call: Call<RoadsResponse>, t: Throwable) {
                                Log.e("DashboardActivity", "Error fetching roads data", t)
                                Toast.makeText(this@DashboardActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
                            }
                        })
                    } else {
                        Log.e("DashboardActivity", "Location is null in LocationCallback")
                        Toast.makeText(this@DashboardActivity, "Unable to retrieve location", Toast.LENGTH_SHORT).show()
                    }
                }
            }

            // Request location updates
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            Log.e("DashboardActivity", "Location permission not granted")
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationRequestCode)
        }
    }

    private fun getStreetNameFromCoordinates(latitude: Double, longitude: Double): String? {
        val geocoder = Geocoder(this, Locale.getDefault())
        Log.d("DashboardActivity", "Starting geocoding for coordinates: Latitude = $latitude, Longitude = $longitude")

        return try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val streetName = address.thoroughfare ?: "Street name not found" // Get the street name

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
        trafficApiService.getTrafficData(BuildConfig.TOM_TOM_TRAFFIC_API, point).enqueue(object : Callback<TrafficResponse> {
            override fun onResponse(call: Call<TrafficResponse>, response: Response<TrafficResponse>) {
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
                                    binding.trafficStatus.setTextColor(ContextCompat.getColor(applicationContext, R.color.traffic_light_color))
                                }
                                "Moderate Traffic" -> {
                                    Log.d("DashboardActivity", "Traffic Status: MODERATE")
                                    binding.trafficStatus.text = getString(R.string.moderate)
                                    binding.trafficStatus.setTextColor(ContextCompat.getColor(applicationContext, R.color.traffic_moderate_color))
                                }
                                "Heavy Traffic" -> {
                                    Log.d("DashboardActivity", "Traffic Status: HEAVY")
                                    binding.trafficStatus.text = getString(R.string.heavy)
                                    binding.trafficStatus.setTextColor(ContextCompat.getColor(applicationContext, R.color.traffic_heavy_color))
                                }
                            }


                            Log.d("DashboardActivity", "Current Speed: $currentSpeed km/h")
                            Log.d("DashboardActivity", "Free Flow Speed: $freeFlowSpeed km/h")
                            Log.d("DashboardActivity", "Current Travel Time: $currentTravelTime seconds")
                            Log.d("DashboardActivity", "Free Flow Travel Time: $freeFlowTravelTime seconds")
                            Log.d("DashboardActivity", "Road Closure: $roadClosure")

                               // Update UI with the traffic data
                            binding.currentSpeedValue.text = getString(R.string.current_speed_format, currentSpeed)
                            binding.freeFlowSpeedValue.text = getString(R.string.free_flow_speed_format, freeFlowSpeed)
                            binding.freeFlowTravelTimeValue.text = getString(R.string.free_flow_travel_time_format, freeFlowTravelTime)
                            binding.currentTravelTimeValue.text = getString(R.string.current_travel_time_format, currentTravelTime)
                            binding.roadClosureValue.text = if (roadClosure == true) "Closed" else "Open"
                        }
                    }
                } else {
                    Log.e("DashboardActivity", "Failed to get traffic data: ${response.message()}")
                    Toast.makeText(this@DashboardActivity, "Failed to get traffic data", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onFailure(call: Call<TrafficResponse>, t: Throwable) {
                Log.e("DashboardActivity", "Error fetching traffic data", t)
                Toast.makeText(this@DashboardActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
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
                        Log.d("DashboardActivity", "Location retrieved: Lat ${location.latitude}, Long ${location.longitude}")
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
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            Log.e("DashboardActivity", "Location permission not granted")
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationRequestCode)
        }
    }

    private fun handleUserLocationAndWeatherFetching(location: Location){
        Log.d("DashboardActivity", "Location retrieved: Lat ${location.latitude}, Long ${location.longitude}")
        val latitude = location.latitude
        val longitude = location.longitude
        val addressList = geocoder.getFromLocation(latitude, longitude, 1)
        val cityName = addressList?.firstOrNull()?.locality ?: "Unknown"

        // Get the detailed address from the location
        val address = addressList?.firstOrNull()
        val preciseAddress = address?.getAddressLine(0) ?: "Unknown Location"

        Log.d("DashboardActivity", "City name: $cityName")

        apiService.getWeather(cityName, apiKey).enqueue(object : Callback<WeatherResponse> {
            override fun onResponse(call: Call<WeatherResponse>, response: Response<WeatherResponse>) {
                if (response.isSuccessful) {
                    val weatherResponse = response.body()
                    weatherResponse?.let {
                        val weatherDescription = it.weather.firstOrNull()?.description ?: "Unknown"
                        val temperature = it.main.temp
                        val heatIndex = it.main.feels_like
                        val iconCode = it.weather.firstOrNull()?.icon ?: "01d"
                        val iconUrl = "https://openweathermap.org/img/wn/$iconCode.png"

                        Log.d("DashboardActivity", "Weather description: $weatherDescription")
                        Log.d("DashboardActivity", "Temperature: $temperature, Heat index: $heatIndex")
                        Log.d("DashboardActivity", "Icon URL: $iconUrl")

                        binding.weatherStatusText.text = weatherDescription.replaceFirstChar { char ->
                            if (char.isLowerCase()) char.titlecase(Locale.ROOT) else char.toString()
                        }
                        binding.temperatureText.text = getString(R.string.temperature_format, temperature)
                        binding.heatIndexValue.text = getString(R.string.temperature_format, heatIndex)
                        binding.cityNameText.text = cityName
                        binding.locationText.text = preciseAddress

                        // Check if the activity is still alive before loading the image
                        if (!isFinishing && !isDestroyed) {
                            Glide.with(this@DashboardActivity)
                                .load(iconUrl)
                                .placeholder(R.drawable.cloud) // Optional placeholder
                                .into(binding.weatherIcon)
                        } else {
                            Log.w("DashboardActivity", "Activity is destroyed or finishing, not loading image.")
                        }
                    }
                } else {
                    Log.e("DashboardActivity", "Failed to get weather data: ${response.message()}")
                    Toast.makeText(this@DashboardActivity, "Failed to get weather data", Toast.LENGTH_SHORT).show()
                }
            }


            override fun onFailure(call: Call<WeatherResponse>, t: Throwable) {
                Log.e("DashboardActivity", "Error fetching weather data", t)
                Toast.makeText(this@DashboardActivity, "Error: ${t.message}", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "Please enable location permission to use the app", Toast.LENGTH_LONG).show()
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
            val dialogView: View = LayoutInflater.from(this).inflate(R.layout.location_permission_dialog, null)
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
        val cutoffTime = (System.currentTimeMillis() - ACTIVE_TIMEFRAME_MS).toDouble() // Convert to Double
        val usersRef = FirebaseDatabase.getInstance().getReference("Users")

        usersRef.orderByChild("lastActive").startAt(cutoffTime).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activeUserCount = snapshot.childrenCount // Count of active users
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error if needed
            }
        })
    }


}
