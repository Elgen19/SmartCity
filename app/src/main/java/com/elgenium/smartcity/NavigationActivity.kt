package com.elgenium.smartcity

import android.Manifest
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.text.Html
import android.util.Log
import android.view.animation.LinearInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.elgenium.smartcity.databinding.ActivityNavigationBinding
import com.elgenium.smartcity.network.RoadsApiService
import com.elgenium.smartcity.network_reponses.RoadsResponse
import com.elgenium.smartcity.network_reponses.Routes
import com.elgenium.smartcity.network_reponses.Step
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import com.google.maps.android.PolyUtil
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class NavigationActivity : AppCompatActivity(), OnMapReadyCallback, TextToSpeech.OnInitListener {

    private lateinit var binding: ActivityNavigationBinding
    private var steps: List<Step> = listOf()
    private var currentStepIndex = 0
    private lateinit var googleMap: GoogleMap
    private var bestRoute: Routes? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var userMarker: Marker? = null
    private var hasDeparted = false
    private lateinit var roadsApiService: RoadsApiService
    private var lastLocation: Location? = null
    private var isFirstLocationUpdate = true
    private val MIN_ROTATION_ANGLE = 20
    private var currentMarkerRotation = 0f
    private lateinit var textToSpeech: TextToSpeech
    private var isTTSReady = false
    private var hasSpokenCurrentStep = false




    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            locationResult.lastLocation?.let { location ->
                updateMapForNavigation(location)
                updateETA(location)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNavigationBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize TextToSpeech
        textToSpeech = TextToSpeech(this, this)

        // Pre-load TextToSpeech in background
        initializeTextToSpeech()

        displayNextInstruction()


        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        bestRoute = intent.getParcelableExtra("bestRoute")
        bestRoute?.let { route ->
            steps = route.legs.first().steps
            val initialETA = calculateInitialETA()
            updateETATextView(initialETA)
        } ?: run {
            Log.e("NavigationActivity", "No route found in intent.")
        }

        binding.recenterButton.setOnClickListener {
            recenterCameraOnUser()
        }

        val retrofit = Retrofit.Builder()
            .baseUrl("https://roads.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        roadsApiService = retrofit.create(RoadsApiService::class.java)
    }

    private fun convertDurationToSeconds(duration: String): Int {
        return duration.removeSuffix("s").toInt() // Convert duration string to Int
    }

    private fun calculateInitialETA(): Long {
        val totalDurationSeconds = bestRoute?.legs?.sumOf { convertDurationToSeconds(it.duration) } ?: 0
        val currentTimeMillis = System.currentTimeMillis()
        return currentTimeMillis + (totalDurationSeconds * 1000L) // Convert seconds to milliseconds
    }

    private fun updateETA(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)
        val totalDistance = bestRoute?.legs?.sumOf { it.distanceMeters }?.toFloat() ?: 0f
        var distanceTraveled = 0f

        // Calculate distance traveled for completed steps
        for (i in 0 until currentStepIndex) {
            distanceTraveled += steps[i].distanceMeters.toFloat()
        }

        // Add the distance traveled within the current step
        if (currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]
            val stepEndLocation = LatLng(
                currentStep.endLocation.latLng.latitude.toDouble(),
                currentStep.endLocation.latLng.longitude.toDouble()
            )
            val distanceToStepEnd = FloatArray(1)
            Location.distanceBetween(
                currentLatLng.latitude,
                currentLatLng.longitude,
                stepEndLocation.latitude,
                stepEndLocation.longitude,
                distanceToStepEnd
            )
            distanceTraveled += currentStep.distanceMeters.toFloat() - distanceToStepEnd[0]
        }

        // Calculate remaining distance
        val remainingDistance = totalDistance - distanceTraveled

        // Check the user's speed
        val speed = location.speed // Speed in m/s
        val currentTime = System.currentTimeMillis()

        // Update ETA based on speed
        val newETA = if (speed > 0) {
            val estimatedTimeInSeconds = (remainingDistance / speed).toLong() // time = distance / speed
            currentTime + estimatedTimeInSeconds * 1000 // Convert seconds to milliseconds
        } else {
            // If stationary, add a fixed amount of time (e.g., 2 minutes) to ETA
            val stationaryDelaySeconds = 120 // 2 minutes in seconds
            currentTime + stationaryDelaySeconds * 1000
        }

        // Update the ETA TextView
        updateETATextView(newETA)
    }

    private fun updateETATextView(etaInMillis: Long) {
        val dateFormat = SimpleDateFormat("hh:mm a", Locale.getDefault())
        val etaString = dateFormat.format(Date(etaInMillis))
        binding.etaValueTextView.text = etaString
    }




    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = textToSpeech.setLanguage(Locale.US) // Set the language to US English
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language is not supported!")
            } else {
                isTTSReady = true // TextToSpeech is ready
            }
        } else {
            Log.e("TTS", "Initialization failed!")
        }
    }

    private fun stripHtml(html: String): String {
        return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString()
    }

    private fun initializeTextToSpeech() {
        // Run initialization in a background thread to avoid UI delay
        Executors.newSingleThreadExecutor().execute {
            textToSpeech = TextToSpeech(this) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    val result = textToSpeech.setLanguage(Locale.US)
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Log.e("TTS", "The Language is not supported!")
                    } else {
                        isTTSReady = true
                    }
                } else {
                    Log.e("TTS", "TextToSpeech initialization failed!")
                }
            }
        }
    }

    private fun updateMapForNavigation(location: Location) {
        val currentLatLng = LatLng(location.latitude, location.longitude)

        // Smooth out location updates
        if (lastLocation == null || location.distanceTo(lastLocation!!) > 2) { // 5 meters threshold
            lastLocation = location

            snapToRoad(location)

            // Update camera position
            val cameraPosition = CameraPosition.Builder()
                .target(currentLatLng)
                .zoom(19f)
                .bearing(if (isFirstLocationUpdate) location.bearing else googleMap.cameraPosition.bearing)
                .build()

            // Use a longer animation duration for smoother movement
            googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null)

            // Update user marker
            if (userMarker == null) {
                val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.truck)
                val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 100, 100, false)

                userMarker = googleMap.addMarker(MarkerOptions()
                    .position(currentLatLng)
                    .icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))
                    .anchor(0.5f, 0.5f)
                    .flat(true))
            } else {
                userMarker?.position = currentLatLng
            }

            // Smoothly rotate the marker
            val targetRotation = location.bearing
            val rotationAnimator = ValueAnimator.ofFloat(currentMarkerRotation, targetRotation)
            rotationAnimator.duration = 1000 // 1 second duration for smooth rotation
            rotationAnimator.interpolator = LinearInterpolator()
            rotationAnimator.addUpdateListener { animation ->
                val animatedRotation = animation.animatedValue as Float
                userMarker?.rotation = animatedRotation
                currentMarkerRotation = animatedRotation
            }
            rotationAnimator.start()

            // Only rotate the map for significant direction changes
            if (abs(targetRotation - googleMap.cameraPosition.bearing) > MIN_ROTATION_ANGLE) {
                val rotateCamera = CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder(googleMap.cameraPosition)
                        .bearing(targetRotation)
                        .build()
                )
                googleMap.animateCamera(rotateCamera, 1000, null)
            }

            isFirstLocationUpdate = false

            // display speedometer
            showSpeed(location)

            // Update the step logic
            if (currentStepIndex < steps.size) {
                val currentStep = steps[currentStepIndex]
                val stepEndLocation = LatLng(
                    currentStep.endLocation.latLng.latitude.toDouble(),
                    currentStep.endLocation.latLng.longitude.toDouble()
                )

                val distanceToStepEnd = FloatArray(1)
                Location.distanceBetween(
                    currentLatLng.latitude,
                    currentLatLng.longitude,
                    stepEndLocation.latitude,
                    stepEndLocation.longitude,
                    distanceToStepEnd
                )

                if (!hasDeparted && distanceToStepEnd[0] < 10) {
                    hasDeparted = true
                }

                // Move to the next step if the user reaches the step's end location
                if (hasDeparted && distanceToStepEnd[0] < 10) {
                    currentStepIndex++
                    hasSpokenCurrentStep = false
                }

                displayNextInstruction()
            }

            // Track total and remaining distance
            val totalDistance = bestRoute?.legs?.sumOf { it.distanceMeters }?.toFloat() ?: 0f

            // Track the distance covered so far by adding up completed steps
            var distanceTraveled = 0f

            // Add the distance of each completed step
            for (i in 0 until currentStepIndex) {
                distanceTraveled += steps[i].distanceMeters.toFloat()
            }

            // Add the distance traveled within the current step
            if (currentStepIndex < steps.size) {
                val currentStep = steps[currentStepIndex]
                val stepEndLocation = LatLng(
                    currentStep.endLocation.latLng.latitude.toDouble(),
                    currentStep.endLocation.latLng.longitude.toDouble()
                )

                val distanceToStepEnd = FloatArray(1)
                Location.distanceBetween(
                    currentLatLng.latitude,
                    currentLatLng.longitude,
                    stepEndLocation.latitude,
                    stepEndLocation.longitude,
                    distanceToStepEnd
                )

                distanceTraveled += currentStep.distanceMeters.toFloat() - distanceToStepEnd[0]
            }

            // Calculate remaining distance
            val remainingDistance = totalDistance - distanceTraveled

            // Update distance TextView
            val distanceText = String.format("%.1f m", remainingDistance)
            binding.distanceValueTextView.text = distanceText

        }
    }

    private fun showSpeed(location: Location) {
        // Update speed
        val speedInKmh = (location.speed * 3.6).toInt()
        binding.speedValueTextView.text = "$speedInKmh"
    }

    private fun displayNextInstruction() {
        if (currentStepIndex < steps.size) {
            val currentStep = steps[currentStepIndex]

            // Format and display instructions in the TextView
            val formattedInstruction = Html.fromHtml(
                currentStep.navigationInstruction.instructions,
                Html.FROM_HTML_MODE_COMPACT
            )
            binding.instructionsTextView.text = formattedInstruction

            // Set maneuver icon
            val maneuverIconResId = getManeuverIcon(currentStep.navigationInstruction.maneuver)
            binding.maneuverIcon.setImageResource(maneuverIconResId)

            // Strip HTML tags before passing to TextToSpeech
            val instructionToSpeak = stripHtml(currentStep.navigationInstruction.instructions)

            // Speak the instruction only if it hasn't been spoken yet
            if (isTTSReady && !hasSpokenCurrentStep) {
                speakInstruction(instructionToSpeak)
                hasSpokenCurrentStep = true // Set flag to prevent repeating the same instruction
            }

        } else {
            // Final destination logic
            binding.instructionsTextView.text = "You have reached your destination."
            binding.maneuverIcon.setImageResource(R.drawable.location)

            // Speak final destination message
            if (isTTSReady) {
                speakInstruction("You have reached your destination.")
            }
        }
    }

    private fun speakInstruction(instruction: String) {
        if (isTTSReady) {
            textToSpeech.speak(instruction, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    private fun snapToRoad(location: Location) {
        val path = "${location.latitude},${location.longitude}"
        val apiKey = BuildConfig.MAPS_API_KEY

        roadsApiService.getSnappedRoads(path, apiKey).enqueue(object : Callback<RoadsResponse> {
            override fun onResponse(call: Call<RoadsResponse>, response: Response<RoadsResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val snappedPoints = response.body()!!.snappedPoints
                    if (snappedPoints.isNotEmpty()) {
                        val snappedLocation = LatLng(snappedPoints[0].location.latitude, snappedPoints[0].location.longitude)
                        updateMapWithSnappedLocation(snappedLocation)
                    }
                } else {
                    Log.e("SnapToRoad", "Failed to snap to road: ${response.errorBody()?.string()}")
                }
            }

            override fun onFailure(call: Call<RoadsResponse>, t: Throwable) {
                Log.e("SnapToRoad", "API call failed: ${t.message}")
            }
        })
    }

    private fun updateMapWithSnappedLocation(snappedLocation: LatLng) {
        // Update camera position to the snapped location
        googleMap.animateCamera(CameraUpdateFactory.newLatLng(snappedLocation))

        // Update user marker position to the snapped location
        if (userMarker == null) {
            val originalBitmap = BitmapFactory.decodeResource(resources, R.drawable.truck)
            val scaledBitmap = Bitmap.createScaledBitmap(originalBitmap, 100, 100, false)

            userMarker = googleMap.addMarker(MarkerOptions()
                .position(snappedLocation)
                .icon(BitmapDescriptorFactory.fromBitmap(scaledBitmap))
                .anchor(0.5f, 0.5f)
                .flat(true))
        } else {
            userMarker?.position = snappedLocation
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
        enableUserLocation()
        drawPolyline()
        addDestinationMarker()
        startLocationUpdates()

        // Center on user's location immediately
        centerOnUserLocation()
    }

    private fun centerOnUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val latLng = LatLng(location.latitude, location.longitude)
                    val cameraPosition = CameraPosition.Builder()
                        .target(latLng)
                        .zoom(19f)
                        .bearing(location.bearing)
                        .build()
                    googleMap.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                }
            }
        }
    }

    private fun recenterCameraOnUser() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val currentLatLng = LatLng(location.latitude, location.longitude)
                    val cameraPosition = CameraPosition.Builder()
                        .target(currentLatLng)
                        .zoom(19f)
                        .bearing(location.bearing)
                        .build()
                    googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000, null)
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1
            )
        }
    }

    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                1
            )
            return
        }

        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000).apply {
            setMinUpdateIntervalMillis(1000) // Set the desired update interval in milliseconds
            setGranularity(Granularity.GRANULARITY_FINE)
        }.build()


        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
    }

    private fun enableUserLocation() {
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            return
        }

        googleMap.isMyLocationEnabled = false
    }

    private fun addDestinationMarker() {
        bestRoute?.let { route ->
            val destinationLatLng = LatLng(
                route.legs.first().steps.last().endLocation.latLng.latitude.toDouble(),
                route.legs.first().steps.last().endLocation.latLng.longitude.toDouble()
            )

            googleMap.addMarker(
                MarkerOptions().position(destinationLatLng).title("Destination")
            )
        }
    }

    private fun drawPolyline() {
        bestRoute?.let { route ->
            val polylinePoints = PolyUtil.decode(route.polyline.encodedPolyline)
            val polylineOptions = PolylineOptions()
                .addAll(polylinePoints)
                .color(resources.getColor(R.color.brand_color))
                .width(10f)

            googleMap.addPolyline(polylineOptions)
        }
    }

    private fun getManeuverIcon(maneuver: String): Int {
        return when (maneuver) {
            "TURN_SHARP_LEFT" -> R.drawable.turn_sharp_left
            "U_TURN_RIGHT" -> R.drawable.u_turn_right
            "TURN_SLIGHT_RIGHT" -> R.drawable.turn_slight_right
            "MERGE" -> R.drawable.merge
            "ROUNDABOUT_LEFT" -> R.drawable.roundabout_left
            "ROUNDABOUT_RIGHT" -> R.drawable.roundabout_right
            "U_TURN_LEFT" -> R.drawable.u_turn_left
            "TURN_SLIGHT_LEFT" -> R.drawable.turn_slight_left
            "TURN_LEFT" -> R.drawable.turn_left
            "RAMP_RIGHT" -> R.drawable.ramp_right
            "TURN_RIGHT" -> R.drawable.turn_right
            "FORK_RIGHT" -> R.drawable.fork_right
            "STRAIGHT" -> R.drawable.straight
            "FORK_LEFT" -> R.drawable.fork_left
            "TURN_SHARP_RIGHT" -> R.drawable.turn_sharp_right
            "RAMP_LEFT" -> R.drawable.ramp_left
            else -> R.drawable.head // Default icon
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)

        if (::textToSpeech.isInitialized) {
            textToSpeech.stop()
            textToSpeech.shutdown()
        }
    }
}




