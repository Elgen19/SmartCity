package com.elgenium.smartcity


import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivityDirectionsBinding
import com.elgenium.smartcity.models.OriginDestinationStops
import com.elgenium.smartcity.network_reponses.RoutesResponse
import com.elgenium.smartcity.network_reponses.TravelAdvisory
import com.elgenium.smartcity.recyclerview_adapter.OriginDestinationAdapter
import com.elgenium.smartcity.routes_network_request.Coordinates
import com.elgenium.smartcity.routes_network_request.ExtraComputation
import com.elgenium.smartcity.routes_network_request.LatLng
import com.elgenium.smartcity.routes_network_request.Location
import com.elgenium.smartcity.routes_network_request.RoutesRequest
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.singletons.RetrofitClientRoutes
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.Locale


class DirectionsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityDirectionsBinding
    private lateinit var adapter: OriginDestinationAdapter
    private val latLngList = mutableListOf<String>()
    private var polylines: MutableList<Polyline> = mutableListOf()
    private var destinationMarker: Marker? = null
    private var selectedTransportMode: String? = "Car"
    private var selectedButton: MaterialButton? = null
    private val stopResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val stopList: MutableList<OriginDestinationStops>? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                data?.getSerializableExtra("STOP_LIST", ArrayList::class.java)?.let {
                    @Suppress("UNCHECKED_CAST")
                    it as? ArrayList<OriginDestinationStops>
                }
            } else {
                @Suppress("DEPRECATION")
                (data?.getSerializableExtra("STOP_LIST") as? ArrayList<*>)?.let {
                    @Suppress("UNCHECKED_CAST")
                    it as? ArrayList<OriginDestinationStops>
                }
            }

            Log.d("DirectionsActivity", "STOP LIST: $stopList")

            // Handle the stopList here
            stopList?.let {
                adapter.updateStops(it)
                updateLatLngList(it)
                adapter.notifyDataSetChanged() // Notify the adapter about data changes

            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDirectionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Obtain the details of origin and destination from Favorites activity
        val originLatLngString = intent.getStringExtra("ORIGIN") ?: ""
        val destination = intent.getStringExtra("DESTINATION") ?: ""

        Log.d("DirectionsActivity", "ORIGIN IS: $originLatLngString")

        // Convert the latLng string to a human-readable address
        val originAddressFormatted = getAddressFromLatLng(originLatLngString)
        Log.d("DirectionsActivity", "ORIGIN ADDRESS: $originAddressFormatted")

        // Extract destination data (Place Name, Address, LatLng)
        val destinationData = destination.split("==").takeIf { it.size == 3 }
            ?: listOf("Unknown Destination", "Unknown Address", "")

        setupBottomSheet(originAddressFormatted, destinationData)


        latLngList.add(originLatLngString)
        latLngList.add(destinationData[2])
        Log.d("DirectionsActivity", "LAT LANG LIST AT ON CREATE: $latLngList")

        fetchRoute()
    }

    private fun fetchRoute() {
        if (latLngList.size < 2) {
            Log.e("DirectionsActivity", "Insufficient data to fetch route.")
            return
        }

        val origin = Location(LatLng(Coordinates(latLngList[0].split(",")[0].toDouble(), latLngList[0].split(",")[1].toDouble())))
        val destination = Location(LatLng(Coordinates(latLngList.last().split(",")[0].toDouble(), latLngList.last().split(",")[1].toDouble())))
        val travelMode = getTravelMode(selectedTransportMode)

        Log.e("DirectionsActivity", "TRAVEL MODE: $travelMode")

        val routingPreference = if (travelMode == "DRIVE" || travelMode == "TWO_WHEELER") "TRAFFIC_AWARE" else null
        val extraComputations = if (travelMode == "DRIVE" || travelMode == "TWO_WHEELER") listOf(ExtraComputation.TRAFFIC_ON_POLYLINE, ExtraComputation.HTML_FORMATTED_NAVIGATION_INSTRUCTIONS) else null
        val request = RoutesRequest(
            origin = origin,
            destination = destination,
            travelMode = travelMode,
            routingPreference = routingPreference,
            computeAlternativeRoutes = false,
            extraComputations = extraComputations
        )

        Log.d("DirectionsActivity", "Request body: ${Gson().toJson(request)}")

        RetrofitClientRoutes.routesApi.getRoutes(request).enqueue(object : Callback<RoutesResponse> {
            override fun onResponse(call: Call<RoutesResponse>, response: Response<RoutesResponse>) {
                if (response.isSuccessful) {
                    Log.e("DirectionsActivity", "RESPONSE BODY IF: ${response.body()}}")
                    Log.e("DirectionsActivity", "RESPONSE BODY IF: ${response.raw()}}")


                    // Select the best route based on distance or duration
                    val bestRoute = response.body()?.routes?.firstOrNull()

                    bestRoute?.let { route ->
                        val polyline = route.polyline.encodedPolyline
                        val travelAdvisories = route.travelAdvisory

                        plotPolyline(polyline, travelAdvisories, travelMode)

                        val durationString = route.duration // e.g., "1381s"
                        val durationInSeconds = durationString.replace("s", "").toInt() // Remove 's' and convert to integer

                        val hours = durationInSeconds / 3600
                        val minutes = (durationInSeconds % 3600) / 60
                        val seconds = durationInSeconds % 60

                        // Format the output dynamically
                        val formattedDuration = buildString {
                            if (hours > 0) {
                                append("$hours h ")
                            }
                            if (minutes > 0 || hours > 0) { // Include minutes if there are hours
                                append("$minutes min ")
                            }
                            append("$seconds s")
                        }.trim()

                        binding.travelTimeValue.text = formattedDuration


                       if (route.description == null) {
                           binding.bestRouteText.visibility = View.GONE
                           binding.bestRouteLabel.visibility = View.GONE
                       } else {
                           binding.bestRouteText.text = "Via ${route.description}"
                           binding.bestRouteText.visibility = View.VISIBLE
                           binding.bestRouteLabel.visibility = View.VISIBLE
                       }

                        val distanceInMeters = route.distanceMeters.toDouble() // Ensure it's a double for accurate division
                        val distanceInKilometers = distanceInMeters / 1000 // Convert to kilometers
                        binding.distanceValue.text = String.format("%.2f km", distanceInKilometers) // Format to 2 decimal places

                        if (travelMode == "TWO_WHEELER" || travelMode == "DRIVE") {
                            binding.trafficCondition.text = determineOverallTrafficCondition(travelAdvisories)
                        } else {
                            binding.trafficCondition.text = "A typical traffic"
                            binding.trafficCondition.setTextColor(resources.getColor(R.color.dark_gray))
                        }

                        binding.startNavigationButton.setOnClickListener {
                            bestRoute.let { route ->
                                val intent = Intent(this@DirectionsActivity, NavigationActivity::class.java)

                                // Pass the entire bestRoute object as a Parcelable
                                intent.putExtra("bestRoute", route)

                                startActivity(intent)
                            }
                        }
                    } ?: run {
                        Log.e("DirectionsActivity", "No routes found in response.")
                    }
                } else {
                    Log.e("DirectionsActivity", "RESPONSE BODY ELSE: ${response.raw()}")
                }
            }

            override fun onFailure(call: Call<RoutesResponse>, t: Throwable) {
                Log.e("DirectionsActivity", "API call failed: ${t.message}")
            }
        })
    }



    private fun plotPolyline(encodedPolyline: String, travelAdvisory: TravelAdvisory?, travelMode: String) {
        // Decode the polyline points
        val polylinePoints: List<com.google.android.gms.maps.model.LatLng> = PolyUtil.decode(encodedPolyline)

        // Clear previous polylines
        clearPolylines()

        // Draw the default polyline
        val defaultPolylineOptions = PolylineOptions().apply {
            addAll(polylinePoints)
            width(10f)
            color(getColor(R.color.brand_color)) // Default color
        }
        val defaultPolyline = mMap.addPolyline(defaultPolylineOptions)
        polylines.add(defaultPolyline) // Store in the list

        // Only overlay colored segments for DRIVE and TWO_WHEELER
        if (travelMode == "DRIVE" || travelMode == "TWO_WHEELER") {
            travelAdvisory?.speedReadingIntervals?.forEach { interval ->
                val segmentPoints = polylinePoints.subList(interval.startPolylinePointIndex, interval.endPolylinePointIndex)

                // Create polyline options based on speed
                val polylineOptions = PolylineOptions().apply {
                    addAll(segmentPoints)
                    width(10f)
                    color(when (interval.speed) {
                        "NORMAL" -> getColor(R.color.traffic_light_color)
                        "SLOW" -> getColor(R.color.traffic_moderate_color)
                        "TRAFFIC_JAM" -> getColor(R.color.traffic_heavy_color)
                        else -> getColor(R.color.brand_color)
                    })
                }


                val trafficPolyline = mMap.addPolyline(polylineOptions) // Create colored polyline
                polylines.add(trafficPolyline) // Store in the list
            }
        }

        // Add a marker at the destination
        val destinationLatLng = polylinePoints.last()
        destinationMarker = mMap.addMarker(MarkerOptions().position(destinationLatLng).title("Destination"))

        // Create bounds to focus the camera on the polyline
        val builder = LatLngBounds.Builder().apply {
            for (point in polylinePoints) {
                include(point)
            }
        }

        val bounds = builder.build()
        val padding = 100 // Padding in pixels
        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)

        // Move the camera to fit the polyline bounds
        mMap.animateCamera(cameraUpdate)
    }

    private fun clearPolylines() {
        // Remove all polylines from the map
        for (polyline in polylines) {
            polyline.remove()
        }
        polylines.clear() // Clear the list after removing
    }








    private fun determineOverallTrafficCondition(travelAdvisory: TravelAdvisory?): String {
        if (travelAdvisory == null) {
            binding.trafficCondition.setTextColor(getColor(R.color.gray)) // Or any default color
            return "No Traffic Data"
        }

        var normalCount = 0
        var slowCount = 0
        var trafficJamCount = 0

        travelAdvisory.speedReadingIntervals.forEach { interval ->
            when (interval.speed) {
                "NORMAL" -> normalCount++
                "SLOW" -> slowCount++
                "TRAFFIC_JAM" -> trafficJamCount++
            }
        }

        val overallCondition = when {
            trafficJamCount > slowCount && trafficJamCount > normalCount -> {
                binding.trafficCondition.setTextColor(getColor(R.color.traffic_heavy_color))
                "Heavy Traffic"
            }
            slowCount > normalCount -> {
                binding.trafficCondition.setTextColor(getColor(R.color.traffic_moderate_color))
                "Moderate Traffic"
            }
            else -> {
                binding.trafficCondition.setTextColor(getColor(R.color.traffic_light_color))
                "Light Traffic"
            }
        }

        return overallCondition
    }


    private fun getTravelMode(modeOfTransport: String?): String {
        return when (modeOfTransport) {
            "Car" -> "DRIVE"
            "2 Wheeler" -> "TWO_WHEELER"
            "Walk" -> "WALK"
            "Transit" -> "TRANSIT"
            else -> "DRIVE" // Default value if none of the above matches
        }
    }

    private fun updateLatLngList(stops: List<OriginDestinationStops>) {
        latLngList.clear() // Clear the existing list

        // Loop through each stop and convert its address/name to lat-lng
        for (stop in stops) {
            val latLng = convertAddressToLatLng(stop.address)
            if (latLng != null) {
                latLngList.add(latLng)
            }
        }

        Log.d("DirectionsActivity", "Updated latLngList: $latLngList")
    }

    private fun convertAddressToLatLng(address: String): String? {
        // Use geocoding to convert the address to lat-lng (or handle this in your own way)
        val geocoder = Geocoder(this, Locale.getDefault())
        return try {
            val addresses = geocoder.getFromLocationName(address, 1)
            if (addresses?.isNotEmpty() == true) {
                val location = addresses[0]
                Log.e("DirectionsActivity", "ADDRESS: $address")
                Log.e("DirectionsActivity", "GEOCODED UPDATE: $location")
                "${location.latitude},${location.longitude}"

            } else null
        } catch (e: Exception) {
            Log.e("DirectionsActivity", "Geocoding failed for address: $address", e)
            null
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


        } else {
            // Request location permission
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }
    }

    private fun setupBottomSheet(originAddress: String, destinationData: List<String>) {
        // Find the bottom sheet view from the activity layout using binding
        val bottomSheet = binding.bottomsheet

        // Retrieve the BottomSheetBehavior and configure it
        val behavior = BottomSheetBehavior.from(bottomSheet)
        behavior.peekHeight = 550  // Set the desired peek height
        behavior.isHideable = false  // Prevent it from being fully dismissed
        behavior.state = BottomSheetBehavior.STATE_COLLAPSED

        // Default mode of transport: Driving aka Car
        handleButtonSelection(binding.btnCar)

        // Get the Google Map fragment
        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.view?.isClickable = true

        // Set up UI elements using binding
        val places = mutableListOf(
            OriginDestinationStops(name = "Your Location", address = originAddress, type = "Origin"),
            OriginDestinationStops(name = destinationData[0], address = destinationData[1], type = "Destination")
        )
        adapter = OriginDestinationAdapter(places)
        binding.originDestinationRecyclerView.adapter = adapter
        binding.originDestinationRecyclerView.layoutManager = LinearLayoutManager(this)

        // Button click handlers
        binding.btnCar.setOnClickListener {
            handleButtonSelection(binding.btnCar)
            binding.transportTitle.text = "Drive Mode"
            binding.transportIcon.setImageResource(R.drawable.car)
            fetchRoute()
        }
        binding.btnTwoWheeler.setOnClickListener {
            handleButtonSelection(binding.btnTwoWheeler)
            binding.transportTitle.text = "Cycling Mode"
            binding.transportIcon.setImageResource(R.drawable.motor)
            fetchRoute()
        }
        binding.btnWalk.setOnClickListener {
            handleButtonSelection(binding.btnWalk)
            binding.transportTitle.text = "Hiking Mode"
            binding.transportIcon.setImageResource(R.drawable.walk)
            fetchRoute()
        }
        binding.btnTransit.setOnClickListener {
            handleButtonSelection(binding.btnTransit)
            binding.transportTitle.text = "Commuter Mode"
            binding.transportIcon.setImageResource(R.drawable.transit)
            fetchRoute()
        }

        binding.btnEdit.setOnClickListener {
            val stopList = ArrayList<OriginDestinationStops>()

            for (place in places) {
                val (name, address, type) = place
                val stop = OriginDestinationStops(name = name, address = address, type = type)
                stopList.add(stop)
            }

            val intent = Intent(this, StopManagementActivity::class.java).apply {
                putExtra("ROUTES", stopList)
            }
            stopResultLauncher.launch(intent)
        }

        binding.closeButton.setOnClickListener {
            // Dismiss the bottom sheet
            behavior.state = BottomSheetBehavior.STATE_HIDDEN
        }

        binding.startNavigationButton.setOnClickListener {
            // this is on a separate method that displays a bottom sheet
        }
    }

    private fun handleButtonSelection(button: MaterialButton) {
        // Reset the previous button's style
        selectedButton?.let { resetButtonStyle(it) }

        // Apply selected style to the newly pressed button
        button.iconTint = ContextCompat.getColorStateList(this, R.color.primary_color)
        button.setBackgroundColor(ContextCompat.getColor(this, R.color.brand_color))
        button.setTextColor(ContextCompat.getColor(this, R.color.primary_color))
        button.strokeColor = ContextCompat.getColorStateList(this, R.color.brand_color)

        // Set the newly selected button as the current selected button
        selectedButton = button

        selectedTransportMode = button.text.toString()

        Log.e("DirectionsActivity", "Selected Transport Mode: $selectedTransportMode")
    }

    private fun resetButtonStyle(button: MaterialButton) {
        // Reset button to its default style
        button.iconTint = ContextCompat.getColorStateList(this, R.color.secondary_color)
        button.setBackgroundColor(ContextCompat.getColor(this, android.R.color.transparent))
        button.setTextColor(ContextCompat.getColor(this, R.color.gray))
        button.strokeColor = ContextCompat.getColorStateList(this, R.color.gray)
    }

    private fun getAddressFromLatLng(latLngString: String): String {
        // Split the latLng string to get latitude and longitude
        val latLngParts = latLngString.split(",")
        if (latLngParts.size != 2) {
            return "Unknown Location"
        }

        val latitude = latLngParts[0].toDoubleOrNull()
        val longitude = latLngParts[1].toDoubleOrNull()
        if (latitude == null || longitude == null) {
            return "Invalid Coordinates"
        }

        // Use the Geocoder to convert latLng to address
        val geocoder = Geocoder(this, Locale.getDefault())
        try {
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)
            if (addresses != null) {
                if (addresses.isNotEmpty()) {
                    val address = addresses[0]
                    return address.getAddressLine(0) // Full address line
                }
            }
        } catch (e: Exception) {
            Log.e("DirectionsActivity", "Geocoder failed: ${e.localizedMessage}")
        }

        return "Address not found"
    }

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }


}