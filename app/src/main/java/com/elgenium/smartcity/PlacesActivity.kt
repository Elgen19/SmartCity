package com.elgenium.smartcity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.elgenium.smartcity.databinding.ActivityPlacesBinding
import com.elgenium.smartcity.helpers.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.network.PlaceDistanceService
import com.elgenium.smartcity.network_reponses.PlaceDistanceResponse
import com.elgenium.smartcity.viewpager_adapter.PhotoPagerAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@Suppress("PrivatePropertyName")
class PlacesActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityPlacesBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var placesClient: PlacesClient
    private val DEFAULT_ZOOM_LEVEL = 15f
    private val DEFAULT_HEADING = 0f
    private val DEFAULT_ORIENTATION = 0f
    private var isFollowingUser = true


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlacesBinding.inflate(layoutInflater)
        setContentView(binding.root)

//        // Handle deep link
//        intent?.data?.let { uri ->
//            if (uri.scheme == "smartcity" && uri.host == "places") {
//                val deepLinkPlaceId = uri.lastPathSegment
//                if (!deepLinkPlaceId.isNullOrEmpty()) {
//                    fetchPlaceDetails(deepLinkPlaceId)
//                }
//            }
//        }

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // get google maps API key in the secrets.properties
        val apiKey = BuildConfig.MAPS_API_KEY

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        }
        placesClient = Places.createClient(this)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)



        // Initialize FloatingActionButton using ViewBinding
        binding.fabCurrentLocation.setOnClickListener {
            resetMapToDefaultLocation()
        }


        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Set the selected item in the BottomNavigationView
        binding.bottomNavigation.selectedItemId = R.id.navigation_places

        // Set up the BottomNavigationView listener
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    navigateToActivity(DashboardActivity::class.java)
                    true
                }
                R.id.navigation_places -> true
                R.id.navigation_favorites -> true
                R.id.navigation_events -> true
                R.id.navigation_settings -> {
                    navigateToActivity(SettingsActivity::class.java)
                    true
                }
                else -> false
            }
        }

        // Set up the Search FAB to navigate to SearchActivity
        binding.fabSearch.setOnClickListener {
            navigateToActivity(SearchActivity::class.java)
        }

        val placeId = intent.getStringExtra("PLACE_ID")

        placeId?.let {
            fetchPlaceDetailsAndPlotMarkerOnMap(it)
        }

        binding.fabMapStyles.setOnClickListener {
            showMapStylesBottomSheet()
        }

    }

    private fun showMapStylesBottomSheet() {
        // Inflate the bottom sheet layout
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_map_options, findViewById(android.R.id.content), false)
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        // Get references to the buttons
        val btnClose: ImageButton = bottomSheetView.findViewById(R.id.btnClose)
        val btnSatellite: ImageButton = bottomSheetView.findViewById(R.id.btnSatellite)
        val btnStandard: ImageButton = bottomSheetView.findViewById(R.id.btnStandard)
        val btnTerrain: ImageButton = bottomSheetView.findViewById(R.id.btnTerrain)
        val btnTraffic: ImageButton = bottomSheetView.findViewById(R.id.btnTraffic)

        // Set click listeners for the buttons
        btnClose.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

        btnSatellite.setOnClickListener {
            mMap.mapType = GoogleMap.MAP_TYPE_SATELLITE
        }

        btnStandard.setOnClickListener {
            // Set the map to the default, flatter map style
            mMap.mapType = GoogleMap.MAP_TYPE_NORMAL
            // Ensure the tilt is set to zero to maintain a flat view
            val currentCameraPosition = mMap.cameraPosition
            val flatCameraPosition = CameraPosition.builder(currentCameraPosition)
                .tilt(0f) // Set tilt to zero for a flat view
                .build()
            mMap.animateCamera(CameraUpdateFactory.newCameraPosition(flatCameraPosition))
        }

        btnTerrain.setOnClickListener {
            mMap.mapType = GoogleMap.MAP_TYPE_TERRAIN
        }

        btnTraffic.setOnClickListener {
            // Toggle traffic layer visibility
            mMap.isTrafficEnabled = !mMap.isTrafficEnabled
        }

        bottomSheetDialog.show()
    }

    private fun resetMapToDefaultLocation() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    val cameraPosition = CameraPosition.builder()
                        .target(userLatLng)
                        .zoom(DEFAULT_ZOOM_LEVEL)
                        .bearing(DEFAULT_HEADING)
                        .tilt(DEFAULT_ORIENTATION)
                        .build()

                    // Use animateCamera to smoothly transition to the new camera position
                    mMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
                }
            }
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun fetchPlaceDetailsAndPlotMarkerOnMap(placeId: String) {
        val fields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.EDITORIAL_SUMMARY,
            Place.Field.ADDRESS,
            Place.Field.PHONE_NUMBER,
            Place.Field.WEBSITE_URI,
            Place.Field.PHOTO_METADATAS,
            Place.Field.RATING,
            Place.Field.OPENING_HOURS,
            Place.Field.LAT_LNG // Ensure this is included
        )
        val request = FetchPlaceRequest.builder(placeId, fields).build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                place.latLng?.let { latLng ->
                    // Handle the valid latLng
                    val marker = mMap.addMarker(MarkerOptions().position(latLng).title(place.name))
                    marker?.tag = placeId
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
                    fetchPlaceDetails(placeId)
                } ?: run {
                    Log.d("PlacesActivity", "Place does not have a LatLng.")
                }
            }
            .addOnFailureListener { exception ->
                Log.e("PlacesActivity", "Error fetching place details", exception)
            }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Check for location permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Enable the My Location layer
            mMap.isMyLocationEnabled = true

            // Optional: Hide the default My Location button
            mMap.uiSettings.isMyLocationButtonEnabled = false

            // Move the camera to the user's location once
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val userLatLng = LatLng(it.latitude, it.longitude)
                    mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(userLatLng, 15f))

                    // Fetch and add POIs after setting the initial location
//                    fetchAndAddPois(userLatLng)
                }
            }

            // Start location updates without moving the camera
            startLocationUpdates()

            // Listen for user interactions with the map
            mMap.setOnCameraMoveStartedListener { reason ->
                if (reason == GoogleMap.OnCameraMoveStartedListener.REASON_GESTURE) {
                    // User moved the map; stop following
                    isFollowingUser = false
                }
            }

            // Marker click listener to show the bottom sheet
            mMap.setOnMarkerClickListener { marker ->
                // Fetch the place ID (you can save this when adding the marker)
                val placeId = marker.tag as? String
                placeId?.let {
                    fetchPlaceDetailsAndPlotMarkerOnMap(it)
                }
                false
            }


        } else {
            // Request location permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }


    private fun setupMoreButton(bottomSheetView: View, bottomSheetDialog: BottomSheetDialog, place: Place) {
        val btnMore: MaterialButton = bottomSheetView.findViewById(R.id.btnMore)

        btnMore.setOnClickListener {
            // Dismiss the current bottom sheet if needed
            bottomSheetDialog.dismiss()

            // Show the options bottom sheet
            setupOptionsBottomSheet(place)
        }
    }


    private fun setupOptionsBottomSheet(place: Place) {
        // Inflate a new view for the bottom sheet
        val inflater = LayoutInflater.from(this)
        val bottomSheetView = inflater.inflate(R.layout.bottom_sheet_more_options, null)

        // Create a new BottomSheetDialog instance
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        val callOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.callOptionLayout)
        val shareOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.shareOptionLayout)
        val reportOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.reportOptionLayout)

        val phoneNumber = place.phoneNumber

        // Set up call option
        if (phoneNumber.isNullOrEmpty()) {
            callOptionLayout.visibility = View.GONE
        } else {
            callOptionLayout.visibility = View.VISIBLE
            callOptionLayout.setOnClickListener {
                val intent = Intent(Intent.ACTION_DIAL).apply {
                    data = Uri.parse("tel:$phoneNumber")
                }
                startActivity(intent)
            }
        }

        shareOptionLayout.setOnClickListener {
            // Check if place.id is not null
            val placeId = place.id
            if (placeId != null) {
                // Create a sharing intent with more details
                val shareText = """
            📍 Check out this place:
            
            Name: ${place.name}
            Address: ${place.address}
            Phone: ${place.phoneNumber ?: "No phone number available"}
            Description: ${place.editorialSummary ?: "No description available."}
            Rating: ${place.rating ?: "No rating available."}
        """.trimIndent()

                val shareIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, shareText)
                    type = "text/plain"
                }
                startActivity(Intent.createChooser(shareIntent, "Share via"))
            } else {
                // Handle the case where place.id is null
                Toast.makeText(this, "Place ID is missing. Cannot share the place.", Toast.LENGTH_SHORT).show()
            }
        }


        // Set up report an event option
        reportOptionLayout.setOnClickListener {
            // Handle report an event option click
            Toast.makeText(this, "Report an event option clicked", Toast.LENGTH_SHORT).show()
        }

        // Show the bottom sheet dialog
        bottomSheetDialog.show()
    }


    private fun fetchPlaceDetails(placeId: String) {
        // Define the fields to fetch for the place
        val placeFields = listOf(
            Place.Field.ID,
            Place.Field.NAME,
            Place.Field.EDITORIAL_SUMMARY,
            Place.Field.ADDRESS,
            Place.Field.PHONE_NUMBER,
            Place.Field.PHOTO_METADATAS,
            Place.Field.LAT_LNG,
            Place.Field.OPENING_HOURS,
            Place.Field.RATING,
            Place.Field.WEBSITE_URI
        )

        // Build the request to fetch the place details
        val request = FetchPlaceRequest.builder(placeId, placeFields).build()

        // Inflate a new view for the bottom sheet
        val inflater = LayoutInflater.from(this)
        val bottomSheetView = inflater.inflate(R.layout.bottom_sheet_place_details, null)

        // Create a new BottomSheetDialog instance
        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetView)

        // Fetch the place details using the Places API
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                // On successful response, update the UI with place details
                val place = response.place
                updatePlaceDetailsUI(place, bottomSheetView) // Update place details UI
                handleOpeningHours(place, bottomSheetView) // Handle and display opening hours
                checkLocationPermissionAndFetchDistance(place, bottomSheetView) // Check location permission and fetch distance
                displayPlacePhotos(place, bottomSheetView) // Display place photos
                setupMoreButton(bottomSheetView, bottomSheetDialog, place)
                setupCloseButton(bottomSheetView, bottomSheetDialog)
            }
            .addOnFailureListener { exception ->
                // Log an error message if the request fails
                Log.e("PlacesActivity", "Error fetching place details for bottom sheet", exception)
            }

        // Show the bottom sheet dialog
        bottomSheetDialog.show()
    }



    private fun updatePlaceDetailsUI(place: Place, bottomSheetView: View) {
        // Find the UI elements in the bottom sheet view.
        val placeName: TextView = bottomSheetView.findViewById(R.id.placeName)
        val placeAddress: TextView = bottomSheetView.findViewById(R.id.placeAddress)
        val placePhone: TextView = bottomSheetView.findViewById(R.id.placePhone)
        val placeWebsite: TextView = bottomSheetView.findViewById(R.id.placeWebsite)
        val placeRating: TextView = bottomSheetView.findViewById(R.id.placeRating)

        // Update the TextViews with the details of the place.
        placeName.text = place.name ?: "Unknown Place" // Set place name or default text if null.
        placeAddress.text = place.address ?: "No Address Available" // Set place address or default text if null.
        placePhone.text = place.phoneNumber ?: "No Phone Available" // Set place phone number or default text if null.
        placeWebsite.text = place.websiteUri?.toString() ?: "No Website Available" // Set place website or default text if null.
        placeRating.text = getString(R.string.rating, place.rating ?: "No Rating") // Set place rating or default text if null.

        // Handle the case where the place does not have latitude and longitude information.
        if (place.latLng == null) {
            placeAddress.text = getString(R.string.location_details_not_available) // Display a message indicating location details are not available.
        }
    }

    private fun handleOpeningHours(place: Place, bottomSheetView: View) {
        // Retrieve the opening hours from the place object.
        val openingHours = place.openingHours

        // Define the time format to be used for parsing and formatting time.
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val calendar = Calendar.getInstance()
        val currentTime = calendar.time // Get the current time.
        val currentDay = calendar.get(Calendar.DAY_OF_WEEK) // Get the current day of the week.
        val currentTimeOnly = timeFormat.parse(timeFormat.format(currentTime)) // Format the current time.

        // Format the days of the week for which the place is open.
        val daysText = openingHours?.periods?.joinToString("\n") { period ->
            val day = period.open?.day?.name ?: "Unknown Day"
            day
        } ?: "No information for hours or days available"

        // Format the opening and closing times for each period.
        val timesText = openingHours?.periods?.joinToString("\n") { period ->
            val openTime = formatTimeString(period.open?.time.toString())
            val closeTime = formatTimeString(period.close?.time.toString())
            "$openTime - $closeTime"
        } ?: ""

        // Find the UI elements for displaying hours and status.
        val placeHoursDays: TextView = bottomSheetView.findViewById(R.id.placeHoursDays)
        val placeHoursTime: TextView = bottomSheetView.findViewById(R.id.placeHoursTime)
        val openStatus: TextView = bottomSheetView.findViewById(R.id.openStatus)
        val placeDistance: TextView = bottomSheetView.findViewById(R.id.placeDistance)

        // Set the formatted days and times text.
        placeHoursDays.text = daysText
        placeHoursTime.text = timesText

        // Determine if the place is currently open.
        if (openingHours != null) {
            var isOpen = false
            openingHours.periods.forEach { period ->
                val dayOfWeek = period.open?.day?.ordinal?.plus(1) // Calendar day starts from 1 (Sunday)
                if (dayOfWeek == currentDay) {
                    val openTimeString = period.open?.time?.let { "${it.hours}:${it.minutes}" }
                    val closeTimeString = period.close?.time?.let { "${it.hours}:${it.minutes}" }

                    // Parse the opening and closing times.
                    val openTimeParsed = openTimeString?.let { timeFormat.parse(it) }
                    val closeTimeParsed = closeTimeString?.let { timeFormat.parse(it) }

                    // Check if the current time is within the opening hours.
                    if (openTimeParsed != null && closeTimeParsed != null && currentTimeOnly != null) {
                        if (currentTimeOnly.after(openTimeParsed) && currentTimeOnly.before(closeTimeParsed)) {
                            isOpen = true
                        }
                    }
                }
            }

            // Update the UI based on the open/closed status.
            if (isOpen) {
                openStatus.text = getString(R.string.open_status)
                openStatus.setBackgroundResource(R.drawable.open_pill_background)
            } else {
                openStatus.text = getString(R.string.closed)
                openStatus.setBackgroundResource(R.drawable.closed_pill_background)
            }

            openStatus.visibility = View.VISIBLE
        } else {
            // Hide the open status if no opening hours information is available.
            openStatus.visibility = View.GONE
            // Adjust margin for placeDistance TextView.
            val layoutParams = placeDistance.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.marginStart = 0
            placeDistance.layoutParams = layoutParams
        }
    }

    private fun checkLocationPermissionAndFetchDistance(place: Place, bottomSheetView: View) {
        // Check if location permissions are granted.
        checkLocationPermission()

        // Retrieve the last known location from the fused location client.
        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
            if (location != null) {
                val userLocation = LatLng(location.latitude, location.longitude) // User's current location.
                val placeLocation = place.latLng // Location of the place.

                if (placeLocation != null) {
                    // Build the API request URL parameters.
                    val apiKey = BuildConfig.MAPS_API_KEY
                    val origin = "${userLocation.latitude},${userLocation.longitude}"
                    val destination = "${placeLocation.latitude},${placeLocation.longitude}"

                    // Create a Retrofit instance for API requests.
                    val api = createRetrofitInstance()

                    // Enqueue the API request to get directions and distance.
                    api.getDirections(origin, destination, apiKey)
                        .enqueue(object : retrofit2.Callback<PlaceDistanceResponse> {
                            override fun onResponse(call: Call<PlaceDistanceResponse>, response: retrofit2.Response<PlaceDistanceResponse>) {
                                // Handle the response from the API.
                                if (response.isSuccessful && response.body() != null) {
                                    val directionsResponse = response.body()
                                    if (directionsResponse?.routes?.isNotEmpty() == true) {
                                        // Extract and display the distance from the response.
                                        val distance = directionsResponse.routes[0].legs[0].distance.text
                                        bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(
                                            R.string.distance_from_location,
                                            distance
                                        )
                                    } else {
                                        // Display a message if distance information is not available.
                                        bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(R.string.distance_not_available)
                                    }
                                } else {
                                    // Display an error message if the API response is not successful.
                                    bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(R.string.distance_error)
                                }
                            }

                            override fun onFailure(call: Call<PlaceDistanceResponse>, t: Throwable) {
                                // Log the error and display an error message if the API request fails.
                                Log.e("RetrofitError", "Error fetching directions: ${t.message}")
                                bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(R.string.distance_error)
                            }
                        })
                } else {
                    // Display a message if the place location is not available.
                    bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(R.string.distance_not_available)
                }
            } else {
                // Display a message if the user's current location is not available.
                bottomSheetView.findViewById<TextView>(R.id.placeDistance).text = getString(R.string.distance_location_not_available)
            }
        }
    }

    private fun displayPlacePhotos(place: Place, bottomSheetView: View) {
        // Get the ViewPager2 instance from the bottom sheet view to display photos.
        val placePhoto: ViewPager2 = bottomSheetView.findViewById(R.id.viewPager)

        // Retrieve the list of photo metadata for the place. Use an empty list if none are available.
        val photoMetadatas = place.photoMetadatas ?: emptyList()

        // Check if there are any photo metadata available.
        if (photoMetadatas.isNotEmpty()) {
            // Load the photos into the ViewPager2.
            loadPhotosIntoViewPager(photoMetadatas, placePhoto)

            // Make the ViewPager2 visible if photos are available.
            placePhoto.visibility = View.VISIBLE
        } else {
            // Hide the ViewPager2 if no photos are available.
            placePhoto.visibility = View.GONE
        }
    }

    private fun setupCloseButton(bottomSheetView: View, bottomSheetDialog: BottomSheetDialog) {
        val btnClose: ImageButton = bottomSheetView.findViewById(R.id.closeButton)
        btnClose.setOnClickListener {
            // Dismiss the bottom sheet dialog
            bottomSheetDialog.dismiss()
        }
    }


    private fun formatTimeString(timeString: String?): String {
        if (timeString.isNullOrEmpty()) return "No Time"

        // Remove 'LocalTime{hours=' and '}'
        val trimmedTimeString = timeString
            .replace("LocalTime{hours=", "")
            .replace(", minutes=", ":")
            .replace("}", "")

        // Create a SimpleDateFormat for parsing and formatting
        val inputFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val outputFormat = SimpleDateFormat("h:mm a", Locale.getDefault())

        return try {
            val date = inputFormat.parse(trimmedTimeString.trim()) // Parse the time string
            if (date != null) outputFormat.format(date) else "Invalid Time"
        } catch (e: ParseException) {
            Log.e("PlacesActivity", "Error parsing time string: ${e.message}")
            "Invalid Time"
        }
    }

    private fun loadPhotosIntoViewPager(photoMetadatas: List<PhotoMetadata>, viewPager: ViewPager2) {
        val photoBitmaps = mutableListOf<Bitmap>()

        // Fetch each photo
        photoMetadatas.forEach { photoMetadata ->
            val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(400)
                .setMaxHeight(400)
                .build()
            placesClient.fetchPhoto(photoRequest)
                .addOnSuccessListener { response ->
                    // Get the Bitmap and add it to the list
                    val photoBitmap = response.bitmap
                    photoBitmap.let {
                        photoBitmaps.add(it)
                        // Only set the adapter once, when all photos are loaded
                        if (photoBitmaps.size == photoMetadatas.size) {
                            viewPager.adapter = PhotoPagerAdapter(photoBitmaps) // Pass Bitmap list directly
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("PlacesActivity", "Error fetching photo", exception)
                }
        }
    }

    private fun createRetrofitInstance(): PlaceDistanceService {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://maps.googleapis.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        return retrofit.create(PlaceDistanceService::class.java)
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY, 1000L
        ).apply {
            setMinUpdateIntervalMillis(500L)
        }.build()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            // No need to move the camera here
            // The My Location layer handles the user's location and heading indicator
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val view = currentFocus
            if (view is View) {
                val outRect = Rect()
                view.getGlobalVisibleRect(outRect)
                if (!outRect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                    view.clearFocus()
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.hideSoftInputFromWindow(view.windowToken, 0)
                }
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun navigateToActivity(activityClass: Class<*>) {
        val intent = Intent(this, activityClass)
        startActivity(intent)
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}
