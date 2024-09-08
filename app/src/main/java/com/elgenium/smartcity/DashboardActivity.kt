package com.elgenium.smartcity

import android.Manifest
import android.app.ActivityOptions
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.elgenium.smartcity.databinding.ActivityDashboardBinding
import com.elgenium.smartcity.helpers.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.models.WeatherResponse
import com.elgenium.smartcity.network.OpenWeatherApiService
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
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
    private val locationRequestCode = 1001
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var geocoder: Geocoder
    private val apiKey = BuildConfig.OPEN_WEATHER_API


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDashboardBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // Initialize Retrofit
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/data/2.5/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        apiService = retrofit.create(OpenWeatherApiService::class.java)

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        geocoder = Geocoder(this, Locale.getDefault())



        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().getReference("Users")

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
    }


    private fun fetchWeather() {
        Log.d("DashboardActivity", "Fetching weather")
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                if (location != null) {
                    Log.d("DashboardActivity", "Location retrieved: Lat ${location.latitude}, Long ${location.longitude}")
                    val latitude = location.latitude
                    val longitude = location.longitude
                    val addressList = geocoder.getFromLocation(latitude, longitude, 1)
                    val cityName = addressList?.firstOrNull()?.locality ?: "Unknown"

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

                                    // Load the weather icon into the ImageView
                                    Glide.with(this@DashboardActivity)
                                        .load(iconUrl)
                                        .placeholder(R.drawable.cloud) // Optional placeholder
                                        .into(binding.weatherIcon)
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
                } else {
                    Log.e("DashboardActivity", "Unable to retrieve location")
                    Toast.makeText(this, "Unable to retrieve location", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            Log.e("DashboardActivity", "Location permission not granted")
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), locationRequestCode)
        }
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

        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    // Handle Home action
                    true
                }
                R.id.navigation_places -> {
                    // Handle Places action
                    val intent = Intent(this, PlacesActivity::class.java)
                    val options = ActivityOptions.makeCustomAnimation(
                        this,
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    startActivity(intent, options.toBundle())
                    finish()
                    true
                }
                R.id.navigation_favorites -> {
                    // Handle Favorites action
                    true
                }
                R.id.navigation_events -> {
                    // Handle Events action
                    true
                }
                R.id.navigation_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    val options = ActivityOptions.makeCustomAnimation(
                        this,
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    startActivity(intent, options.toBundle())
                    finish()
                    true
                }
                else -> false
            }
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
                    // Load the profile picture using Glide or Picasso
                    Glide.with(this)
                        .load(photoUrl)
                        .placeholder(R.drawable.female) // Use a placeholder if needed
                        .into(binding.profileImage)
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
}
