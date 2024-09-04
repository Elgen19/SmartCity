package com.elgenium.smartcity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ImageButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.databinding.ActivityPlacesBinding
import com.elgenium.smartcity.models.PlacesResponse
import com.elgenium.smartcity.network.PlacesService
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
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

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
            fetchPlaceDetails(it)
        }

        binding.fabMapStyles.setOnClickListener {
            showMapStylesBottomSheet()
        }

    }

    private fun showMapStylesBottomSheet() {
        // Inflate the bottom sheet layout
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_map_options, null)
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


    private fun fetchPlaceDetails(placeId: String) {
        val request = FetchPlaceRequest.builder(placeId, listOf(Place.Field.LAT_LNG)).build()

        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                val place = response.place
                place.latLng?.let { latLng ->
                    // Animate the camera to the place location
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))

                    // Add a marker at the place location
                    mMap.addMarker(MarkerOptions().position(latLng).title("Selected Place"))
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
                    fetchAndAddPois(userLatLng)
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
        } else {
            // Request location permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }


    private fun fetchAndAddPois(userLatLng: LatLng) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            val apiKey = BuildConfig.MAPS_API_KEY

            val retrofit = Retrofit.Builder()
                .baseUrl("https://maps.googleapis.com/maps/api/")
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            val service = retrofit.create(PlacesService::class.java)

            val location = "${userLatLng.latitude},${userLatLng.longitude}"
            val radius = 5000 // Radius in meters
            val types = listOf("restaurant", "hotel", "bar") // Example categories

            // Define custom icons
            val restaurantIcon = createCustomMarker(R.drawable.restaurant)
            val hotelIcon = createCustomMarker(R.drawable.hotel)
            val barIcon = createCustomMarker(R.drawable.bar)
            val defaultIcon = createCustomMarker(R.drawable.marker_custom)

            for (type in types) {
                service.getNearbyPlaces(location, radius, type, apiKey).enqueue(object :
                    Callback<PlacesResponse> {
                    override fun onResponse(call: Call<PlacesResponse>, response: Response<PlacesResponse>) {
                        if (response.isSuccessful) {
                            response.body()?.results?.forEach { place ->
                                val latLng = LatLng(place.geometry.location.lat, place.geometry.location.lng)
                                val markerOptions = MarkerOptions()
                                    .position(latLng)
                                    .title(place.name)
                                    .snippet("Category: $type")

                                // Set the icon based on the category
                                when (type) {
                                    "restaurant" -> markerOptions.icon(restaurantIcon)
                                    "hotel" -> markerOptions.icon(hotelIcon)
                                    "bar" -> markerOptions.icon(barIcon)
                                    else -> markerOptions.icon(defaultIcon) // Use default icon for other types
                                }

                                mMap.addMarker(markerOptions)
                            }
                        } else {
                            Log.e("PlacesActivity", "Response error for type: $type. Error code: ${response.code()}")
                        }
                    }

                    override fun onFailure(call: Call<PlacesResponse>, t: Throwable) {
                        Log.e("PlacesActivity", "Error fetching POI data", t)
                    }
                })
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }



    private fun createCustomMarker(iconId: Int): BitmapDescriptor {
        val markerWidth = 100 // Desired width of the marker in pixels
        val markerHeight = 100 // Desired height of the marker in pixels
        val iconSize = 30 // Desired size of the icon in pixels

        // Create the base marker bitmap with desired size
        val markerDrawable = ContextCompat.getDrawable(this, R.drawable.marker_custom)?.apply {
            setBounds(0, 0, markerWidth, markerHeight)
        }
        val markerBitmap = Bitmap.createBitmap(markerWidth, markerHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(markerBitmap)
        markerDrawable?.draw(canvas)

        // Create the icon bitmap and resize it to fit within the marker
        val iconDrawable = ContextCompat.getDrawable(this, iconId)?.apply {
            setBounds(0, 0, iconSize, iconSize) // Resize icon to fit within the marker
        }
        val iconBitmap = Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
        val iconCanvas = Canvas(iconBitmap)
        iconDrawable?.draw(iconCanvas)

        // Combine the marker and icon
        val markerWithIconBitmap = Bitmap.createBitmap(markerWidth, markerHeight, Bitmap.Config.ARGB_8888)
        val markerWithIconCanvas = Canvas(markerWithIconBitmap)
        markerWithIconCanvas.drawBitmap(markerBitmap, 0f, 0f, null)

        // Position the icon in the center of the marker
        val left = (markerWidth - iconSize) / 2
        val top = (markerHeight - iconSize) / 2
        markerWithIconCanvas.drawBitmap(iconBitmap, left.toFloat(), top.toFloat(), null)

        return BitmapDescriptorFactory.fromBitmap(markerWithIconBitmap)
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
