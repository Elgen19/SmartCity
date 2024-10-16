package com.elgenium.smartcity


import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.ColorDrawable
import android.location.Geocoder
import android.os.Bundle
import android.text.Html
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivityDirectionsBinding
import com.elgenium.smartcity.models.OriginDestinationStops
import com.elgenium.smartcity.models.Step
import com.elgenium.smartcity.network_reponses.RouteTravelMode
import com.elgenium.smartcity.network_reponses.RoutesResponse
import com.elgenium.smartcity.network_reponses.TravelAdvisory
import com.elgenium.smartcity.recyclerview_adapter.StepAdapter
import com.elgenium.smartcity.routes_network_request.Coordinates
import com.elgenium.smartcity.routes_network_request.ExtraComputation
import com.elgenium.smartcity.routes_network_request.LatLng
import com.elgenium.smartcity.routes_network_request.Location
import com.elgenium.smartcity.routes_network_request.RoutesRequest
import com.elgenium.smartcity.routes_network_request.Waypoint
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.singletons.RetrofitClientRoutes
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.google.maps.android.PolyUtil
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.io.IOException
import java.util.Locale


class DirectionsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityDirectionsBinding
    private val latLngList = mutableListOf<String>()
    private val optimizedIndices = mutableListOf<Int>()
    private var polylines: MutableList<Polyline> = mutableListOf()
    private var destinationMarker: Marker? = null
    private var selectedTransportMode: String? = "Car"
    private var selectedButton: MaterialButton? = null
    private var isUpdated: Boolean = false
    private val stopoverMarkers = mutableListOf<Marker>()
    private lateinit var stepsAdapter: StepAdapter
    private var stepsList: MutableList<Step> = mutableListOf()
    private var stopList: MutableList<OriginDestinationStops> = mutableListOf()
    private var isWaypointOptimized = false
    private val stopResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                stopList =
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        data?.getSerializableExtra("STOP_LIST", ArrayList::class.java)?.let {
                            @Suppress("UNCHECKED_CAST")
                            it as? ArrayList<OriginDestinationStops>
                        } ?: mutableListOf()
                    } else {
                        @Suppress("DEPRECATION")
                        (data?.getSerializableExtra("STOP_LIST") as? ArrayList<*>)?.let {
                            @Suppress("UNCHECKED_CAST")
                            it as? ArrayList<OriginDestinationStops>
                        } ?: mutableListOf()
                    }

                isUpdated = data?.getBooleanExtra("IS_UPDATED", false) ?: false


                Log.e("DirectionsActivity", "STOP LIST: $stopList")
                Log.e("DirectionsActivity", "IS UPDATED: $isUpdated")


                retrievePreferences()
                updateLatLngList(stopList)
                updateCourseCard(stopList)

                fetchRoute()


                Log.e("DirectionsActivity", "LAT LNG LIST: $latLngList")

            }
        }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDirectionsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        retrievePreferences()

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
        val destinationData = destination.split("==").takeIf { it.size == 4 }
            ?: listOf("Unknown Destination", "Unknown Address", "Unknown Latlng", "Unknown placeid")

        setupBottomSheet()

        binding.originName.text = getString(R.string.your_location)
        binding.originAddress.text = originAddressFormatted

        binding.destinationName.text = destinationData[0]
        binding.destinationAddress.text = destinationData[1]


        latLngList.add(originLatLngString)
        latLngList.add(destinationData[2])
        Log.e("DirectionsActivity", "LAT LANG LIST AT ON CREATE: $latLngList")

        fetchRoute()

        if (!isUpdated) {
            val places = mutableListOf(
                OriginDestinationStops(
                    name = "Your Location",
                    address = originAddressFormatted,
                    type = "Origin",
                    latlng = originLatLngString,
                    placeid = ""
                ),
                OriginDestinationStops(
                    name = destinationData[0],
                    address = destinationData[1],
                    type = "Destination",
                    latlng = destinationData[2],
                    placeid = destinationData[3]
                )
            )
            for (place in places) {
                val (name, address, type, latlng, placeid) = place
                val stop = OriginDestinationStops(
                    name = name,
                    address = address,
                    type = type,
                    latlng = latlng,
                    placeid = placeid
                )
                stopList.add(stop)
            }
        }

        Log.e("DirectionsActivity", "STOPLIST AT ON CREATE: $stopList")
    }

    private fun fetchRoute() {
        if (latLngList.size < 2) {
            Log.e("DirectionsActivity", "Insufficient data to fetch route.")
            return
        }

        val travelMode = getTravelMode(selectedTransportMode)
        val origin = Location(
            LatLng(
                Coordinates(
                    latLngList[0].split(",")[0].toDouble(),
                    latLngList[0].split(",")[1].toDouble()
                )
            )
        )
        val destination = Location(
            LatLng(
                Coordinates(
                    latLngList.last().split(",")[0].toDouble(),
                    latLngList.last().split(",")[1].toDouble()
                )
            )
        )
        val isStopOver = if (travelMode == "DRIVE" || travelMode == "TWO_WHEELER") true else false

        if (latLngList.size > 2 && travelMode == "TRANSIT") {
            val snackbar = Snackbar.make(
                findViewById(android.R.id.content), // Use the root view of your activity
                "Transit mode of transport does not support stop waypoint. Please remove the stops waypoint.",
                Snackbar.LENGTH_LONG // Or Snackbar.LENGTH_SHORT
            )
            // Show the Snackbar
            snackbar.show()
            handleButtonSelection(binding.btnCar)
            fetchRoute()
            return
        }

        // Create intermediates for intermediate stops (if any)
        val intermediates: List<Waypoint> = if (latLngList.size > 2) {
            latLngList.subList(1, latLngList.size - 1).map { latLngString ->
                val latLng = latLngString.split(",")
                // Ensure correct parsing with error handling
                if (latLng.size == 2) {
                    val latitude = latLng[0].toDoubleOrNull()
                    val longitude = latLng[1].toDoubleOrNull()

                    // Ensure valid latitude and longitude
                    if (latitude != null && longitude != null &&
                        latitude in -90.0..90.0 &&
                        longitude in -180.0..180.0
                    ) {
                        Waypoint(
                            location = LatLng(Coordinates(latitude, longitude)),
                            vehicleStopover = isStopOver, // Set to true if it is a stopover
                            sideOfRoad = false // Adjust as needed
                        )
                    } else {
                        // Log invalid coordinates
                        Log.e("DirectionsActivity", "Invalid coordinates: $latLngString")
                        null // Return null for invalid waypoint
                    }
                } else {
                    Log.e("DirectionsActivity", "Invalid latLng format: $latLngString")
                    null // Return null for invalid waypoint
                }
            }.filterNotNull() // Filter out any null entries
        } else {
            emptyList() // Return an empty list if there are no intermediates
        }



        Log.e("DirectionsActivity", "TRAVEL MODE: $travelMode")

        val computeAlternatives = travelMode == "TRANSIT"
        val routingPreference =
            if (travelMode == "DRIVE" || travelMode == "TWO_WHEELER") "TRAFFIC_AWARE" else null
        val extraComputations = if (travelMode == "DRIVE" || travelMode == "TWO_WHEELER") listOf(
            ExtraComputation.TRAFFIC_ON_POLYLINE,
            ExtraComputation.HTML_FORMATTED_NAVIGATION_INSTRUCTIONS
        ) else null
        val request = RoutesRequest(
            origin = origin,
            destination = destination,
            intermediates = intermediates, // Use intermediates here
            travelMode = travelMode,
            routingPreference = routingPreference,
            computeAlternativeRoutes = false,
            extraComputations = extraComputations,
            optimizeWaypointOrder = isWaypointOptimized
        )

        Log.d("DirectionsActivity", "Request body: ${Gson().toJson(request)}")



        RetrofitClientRoutes.routesApi.getRoutes(request)
            .enqueue(object : Callback<RoutesResponse> {
                override fun onResponse(
                    call: Call<RoutesResponse>,
                    response: Response<RoutesResponse>
                ) {
                    if (response.isSuccessful) {
                        Log.e("DirectionsActivity", "RESPONSE BODY IF: ${response.body()}}")
                        Log.e("DirectionsActivity", "RESPONSE BODY IF: ${response.raw()}}")


                        // Select the best route based on distance or duration
                        val bestRoute = response.body()?.routes?.firstOrNull()



                        bestRoute?.let { route ->
                            val polyline = route.polyline.encodedPolyline
                            val travelAdvisories = route.travelAdvisory
                            val optimizedIndex = route.optimizedIntermediateWaypointIndex

                            // Check if optimizedIndex is not null and not empty
                            if (optimizedIndex != null && optimizedIndex.isNotEmpty()) {
                                optimizedIndices.clear()
                                optimizedIndices.addAll(optimizedIndex)
                                rearrangeLatLngList()
                                rearrangeStopList()
                            } else {
                                Log.e("DirectionsActivity", "No optimized indices returned or optimizedIndex is null.")
                            }





                            plotPolylineWithWaypoints(
                                polyline,
                                travelAdvisories,
                                travelMode,
                                intermediates
                            )

                            Log.e("DirectionsActivity", "ROUTE TOKEN: ${bestRoute.routeToken}")
                            Log.e("DirectionsActivity", "TRAVEL MODE: $travelMode")
                            Log.e("DirectionsActivity", "OPTIMIZED WAYPOINT ORDER: $optimizedIndex")


                            // Assume allSteps is a mutable list of Step objects
                            val allSteps = mutableListOf<Step>()

                            // Get the total number of legs and calculate stopovers
                            val totalLegs = bestRoute.legs.size

                            // Iterate through each leg in the route
                            for (legIndex in bestRoute.legs.indices) {
                                val leg = bestRoute.legs[legIndex]
                                Log.e("DirectionsActivity", "Processing leg: $leg")

                                // Get the steps for the current leg
                                val stepData = leg.steps

                                // Iterate through each step and add to allSteps list
                                for (stepIndex in stepData.indices) {
                                    val step = stepData[stepIndex]
                                    Log.e("DirectionsActivity", "Processing step: $step")

                                    val distance = "${step.distanceMeters} m"
                                    Log.e("DirectionsActivity", "Step distance: $distance")

                                    // Determine the instruction based on the leg index
                                    val instruction = when {
                                        legIndex == totalLegs - 1 && stepIndex == stepData.size - 1 -> {
                                            // Last step instruction indicating arrival at the destination
                                            "You have arrived at your destination."
                                        }

                                        legIndex < totalLegs - 1 && stepIndex == stepData.size - 1 -> {
                                            // Last step of a stopover
                                            val stopoverNumber =
                                                legIndex + 1 // +1 to convert 0-indexed to 1-indexed
                                            "You have arrived at stopover $stopoverNumber."
                                        }

                                        else -> {
                                            // Regular instruction for other steps
                                            step.navigationInstruction.instructions
                                                ?: "No instruction available"
                                        }
                                    }

                                    // Add the step with instruction and distance to the list
                                    allSteps.add(Step(instruction, distance))
                                    Log.e("DirectionsActivity", "Step instruction: $instruction")
                                }
                            }


// Log total number of steps processed
                            Log.e("DirectionsActivity", "Total steps processed: ${allSteps.size}")

// Now, allSteps contains steps from all legs of the route
                            stepsList.clear()
                            stepsList.addAll(allSteps)

// Log if the stepsList is updated successfully
                            Log.e(
                                "DirectionsActivity",
                                "Steps list updated with ${stepsList.size} steps"
                            )

                            // Initialize the adapter with the stepsList and set it to the RecyclerView
                            stepsAdapter = StepAdapter(stepsList)
                            binding.stepsRecyclerview.layoutManager =
                                LinearLayoutManager(this@DirectionsActivity)

                            // Create a custom drawable with your desired color
                            val dividerDrawable = ColorDrawable(
                                ContextCompat.getColor(
                                    this@DirectionsActivity,
                                    R.color.dark_gray
                                )
                            )

                            // Create the DividerItemDecoration and set the drawable
                            val dividerItemDecoration = DividerItemDecoration(
                                binding.stepsRecyclerview.context,
                                LinearLayoutManager.VERTICAL
                            )
                            dividerItemDecoration.setDrawable(dividerDrawable)

                            // Add the custom divider to the RecyclerView
                            binding.stepsRecyclerview.addItemDecoration(dividerItemDecoration)
                            binding.stepsRecyclerview.adapter = stepsAdapter


                            val durationString = route.duration // e.g., "1381s"
                            val durationInSeconds = durationString.replace("s", "")
                                .toInt() // Remove 's' and convert to integer

                            val hours = durationInSeconds / 3600
                            val minutes = (durationInSeconds % 3600) / 60
                            val seconds = durationInSeconds % 60

                            // Format the output dynamically, excluding zero values
                            val formattedDuration = buildString {
                                if (hours > 0) {
                                    append("$hours h ")
                                }
                                if (minutes > 0) {
                                    append("$minutes min ")
                                }
                                if (seconds > 0 || (hours == 0 && minutes == 0)) { // Always show seconds if hours and minutes are both zero
                                    append("$seconds s")
                                }
                            }.trim()

                            // Set the formatted duration to the desired TextView or log it
                            Log.d("DirectionsActivity", "Formatted Duration: $formattedDuration")


                            binding.travelTimeValue.text = formattedDuration

                            val transitLayouts = listOf(
                                binding.transit1Layout,
                                binding.transit2Layout,
                                binding.transit3Layout
                            )
                            if (travelMode == "TRANSIT") {
                                binding.bestRouteLayout.visibility = View.GONE
                                transitLayouts.forEach { layout ->
                                    layout.visibility = View.GONE
                                }

                                val stepData = bestRoute.legs[0].steps

                                // List of available TextViews for names and short names
                                val transitNames =
                                    listOf(binding.transit1, binding.transit2, binding.transit3)
                                val transitShortNames = listOf(
                                    binding.transit1Sign,
                                    binding.transit2Sign,
                                    binding.transit3Sign
                                )

                                // Initialize a variable to keep track of the total fare
                                var totalFare: Double = 0.0

// List to store transit line names and their respective fares
                                val transitFares =
                                    mutableListOf<Pair<String, Double>>() // Pair of (Transit Line Name, Fare)

// Iterate through each step and filter for TRANSIT travel modes
                                stepData.filter { it.travelMode == RouteTravelMode.TRANSIT }
                                    .forEach { step ->
                                        // Convert distance from meters to kilometers
                                        val distanceInKm = step.distanceMeters / 1000.0

                                        // Calculate fare for the current transit step
                                        val fare: Double = if (distanceInKm <= 4) {
                                            13.0 // base fare for distances up to 4 km
                                        } else {
                                            // For each kilometer beyond the first 4 km, add 1.80
                                            val additionalKilometers = distanceInKm - 4
                                            val additionalFare = additionalKilometers * 1.80
                                            13.0 + additionalFare
                                        }

                                        // Add the calculated fare to the total fare
                                        totalFare += fare

                                        // Add the fare to the list of transit fares, along with the transit line name
                                        val transitLineName =
                                            step.transitDetails?.transitLine?.name ?: "Unknown Line"
                                        transitFares.add(Pair(transitLineName, fare))
                                    }

// Display the total fare in fareValue TextView
                                binding.fareValue.text =
                                    String.format("%.2f pesos", totalFare) // Display the total fare

// Only show the fare breakdown if there is more than 1 transit
                                if (transitFares.size > 1) {
                                    // Create a string builder for both the line names and the fares
                                    val lineNamesBuilder = StringBuilder()
                                    val faresBuilder = StringBuilder()

                                    // Build the breakdown string for both transit lines and fares
                                    transitFares.forEachIndexed { index, fareData ->
                                        val (lineName, fare) = fareData

                                        if (index > 0) {
                                            lineNamesBuilder.append("\n") // Add a new line between entries
                                            faresBuilder.append("\n")     // Add a new line between entries
                                        }

                                        // Append the line name and fare
                                        lineNamesBuilder.append(lineName)
                                        faresBuilder.append(String.format("%.2f pesos", fare))
                                    }

                                    // Update the fare breakdown TextViews
                                    binding.fareLabel2.text = lineNamesBuilder.toString()
                                    binding.fareBreakdownValue.text = faresBuilder.toString()

                                    // Make the fare breakdown layout visible
                                    binding.fareLayoutBreakdown.visibility = View.VISIBLE
                                } else {
                                    // Hide the fare breakdown if there is only 1 or no transit
                                    binding.fareLayoutBreakdown.visibility = View.GONE
                                }


                                // Counter to track which TextView to update
                                var transitIndex = 0
                                var firstRide = true // To track if this is the first ride

                                stepData.forEach { step ->
                                    val transitDetails = step.transitDetails

                                    if (transitDetails != null && transitIndex < transitNames.size) {
                                        val transitLine = transitDetails.transitLine

                                        // Access shortName and name from transitLine
                                        val shortName = transitLine?.nameShort
                                        val name = transitLine?.name

                                        // Update UI on the main thread
                                        runOnUiThread {
                                            if (firstRide) {
                                                // First ride, show "Ride + shortName"
                                                val htmlString = "Ride PUJ with route <b>$name</b>"
                                                transitNames[transitIndex].text = Html.fromHtml(
                                                    htmlString,
                                                    Html.FROM_HTML_MODE_LEGACY
                                                )
                                                transitShortNames[transitIndex].text = shortName
                                                firstRide = false
                                            } else {
                                                // For subsequent rides, show "Transfer to + shortName"
                                                val htmlString =
                                                    "Transfer to PUJ route <b>$name</b>"
                                                transitNames[transitIndex].text = Html.fromHtml(
                                                    htmlString,
                                                    Html.FROM_HTML_MODE_LEGACY
                                                )
                                                transitShortNames[transitIndex].text = shortName
                                            }

                                            // Make the views visible
                                            transitLayouts[transitIndex].visibility = View.VISIBLE
                                            binding.fareLayout.visibility = View.VISIBLE
                                        }

                                        Log.e("DirectionsActivity", "SHORTNAME: $shortName")

                                        // Increment the index for the next set of TextViews
                                        transitIndex++
                                    }
                                }
                            } else {
                                binding.bestRouteText.text = Html.fromHtml(
                                    "Via <b>${route.description}</b>",
                                    Html.FROM_HTML_MODE_LEGACY
                                )
                                // Set all transit layouts to GONE
                                transitLayouts.forEach { layout ->
                                    layout.visibility = View.GONE
                                }
                                binding.fareLayout.visibility = View.GONE
                                binding.fareLayoutBreakdown.visibility = View.GONE
                                binding.bestRouteLayout.visibility = View.VISIBLE

                            }


                            val distanceInMeters =
                                route.distanceMeters.toDouble() // Ensure it's a double for accurate division
                            val distanceInKilometers =
                                distanceInMeters / 1000 // Convert to kilometers
                            binding.distanceValue.text = String.format(
                                "%.2f km",
                                distanceInKilometers
                            ) // Format to 2 decimal places

                            if (travelMode == "TWO_WHEELER" || travelMode == "DRIVE") {
                                binding.trafficCondition.text =
                                    determineOverallTrafficCondition(travelAdvisories)
                            } else {
                                binding.trafficCondition.text = "A typical traffic"
                                binding.trafficCondition.setTextColor(resources.getColor(R.color.dark_gray))
                            }


                            var routeToken = bestRoute.routeToken

                            if (travelMode == "WALK" || travelMode == "TRANSIT") {
                                routeToken = "NO ROUTE TOKEN"
                            }
                            // Create a list of place IDs from the stopList, excluding entries with empty place IDs
                            val placeIds = stopList.mapNotNull { stop ->
                                stop.placeid.ifEmpty { null }
                            }

                            binding.startNavigationButton.setOnClickListener {
                                // Log or check the filtered placeIds if needed
                                Log.e("Place IDs", placeIds.joinToString())

                                // Create the intent for starting the navigation activity
                                val intent = Intent(
                                    this@DirectionsActivity,
                                    StartNavigationsActivity::class.java
                                ).apply {
                                    putExtra("ROUTE_TOKEN", routeToken)
                                    putExtra("IS_SIMULATED", false)
                                    putExtra("TRAVEL_MODE", travelMode)
                                    putStringArrayListExtra(
                                        "PLACE_IDS",
                                        ArrayList(placeIds)
                                    )  // Pass place IDs as an extra
                                }
                                startActivity(intent)
                            }

                            binding.simulateButton.setOnClickListener {
                                val intent = Intent(
                                    this@DirectionsActivity,
                                    StartNavigationsActivity::class.java
                                ).apply {
                                    putExtra("ROUTE_TOKEN", routeToken)
                                    putExtra("IS_SIMULATED", true)
                                    putExtra("TRAVEL_MODE", travelMode)
                                    putStringArrayListExtra(
                                        "PLACE_IDS",
                                        ArrayList(placeIds)
                                    )  // Pass place IDs as an extra
                                }
                                startActivity(intent)
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

    private fun retrievePreferences() {
        val sharedPreferences = getSharedPreferences("user_settings", MODE_PRIVATE)
        isWaypointOptimized = sharedPreferences.getBoolean("optimized_waypoints", false)

        Log.e("Preferences", "eventRecommender at retrievePreferences traffic overlay: $isWaypointOptimized")

    }

    private fun rearrangeStopList() {
        // Log the original stopList for debugging
        Log.e("DirectionsActivity", "Original stopList: $stopList")
        Log.e("DirectionsActivity", "Optimized indices: $optimizedIndices")

        // Ensure there are at least 2 optimized indices to work with
        if (optimizedIndices.size >= 2) {
            // Create a new list to hold the rearranged stops
            val rearrangedStops = mutableListOf<OriginDestinationStops>()

            // Check if stopList has more than 2 elements to have a first and last
            if (stopList.size > 3) {
                // Extract the first stop, the middle stops, and the last stop
                val firstStop = stopList.first()
                val lastStop = stopList.last()
                val middleStops = stopList.subList(1, stopList.size - 1).toMutableList() // Get stops between first and last

                // Log extracted middle stops
                Log.e("DirectionsActivity", "Middle stops before rearrangement: $middleStops")

                // Rearrange based on optimized indices
                val rearrangedMiddleStops = mutableListOf<OriginDestinationStops>()

                for (index in optimizedIndices) {
                    if (index in middleStops.indices) {
                        rearrangedMiddleStops.add(middleStops[index])
                    }
                }

                // Log the rearranged middle stops
                Log.e("DirectionsActivity", "Middle stops after rearrangement: $rearrangedMiddleStops")

                // Rebuild the stopList
                rearrangedStops.add(firstStop) // Add the first stop
                rearrangedStops.addAll(rearrangedMiddleStops) // Add the rearranged middle stops
                rearrangedStops.add(lastStop) // Add the last stop
            } else {
                // If there are not enough stops to rearrange
                rearrangedStops.addAll(stopList)
                Log.e("DirectionsActivity", "Not enough stops to rearrange, keeping original: $stopList")
            }

            // Clear the original list and add rearranged items
            stopList.clear()
            stopList.addAll(rearrangedStops)

            // Log the rearranged stopList for debugging
            Log.e("DirectionsActivity", "Rearranged stopList: $stopList")
        } else {
            Log.e("DirectionsActivity", "No rearrangement needed for stopList.")
        }
    }

    private fun rearrangeLatLngList() {
        // Log the original latLngList for debugging
        Log.e("DirectionsActivity", "Original latLngList: $latLngList")
        Log.e("DirectionsActivity", "Optimized indices: $optimizedIndices")

        // Check if there are enough latLng items to rearrange
        if (latLngList.size >= 4 ) {
            // Create a new list to hold the rearranged latLngs
            val rearrangedLatLngs = mutableListOf<String>()

            // Extract the first and last latLngs
            val firstLatLng = latLngList.first()
            val lastLatLng = latLngList.last()
            val middleLatLngs = latLngList.subList(1, latLngList.size - 1).toMutableList() // Get latLngs between first and last

            // Log extracted middle latLngs
            Log.e("DirectionsActivity", "Middle latLngs before rearrangement: $middleLatLngs")

            // Rearrange based on optimized indices
            val rearrangedMiddleLatLngs = mutableListOf<String>()

            for (index in optimizedIndices) {
                if (index in middleLatLngs.indices) {
                    rearrangedMiddleLatLngs.add(middleLatLngs[index])
                }
            }

            // Log the rearranged middle latLngs
            Log.e("DirectionsActivity", "Middle latLngs after rearrangement: $rearrangedMiddleLatLngs")

            // Rebuild the latLngList
            rearrangedLatLngs.add(firstLatLng) // Add the first latLng
            rearrangedLatLngs.addAll(rearrangedMiddleLatLngs) // Add the rearranged middle latLngs
            rearrangedLatLngs.add(lastLatLng) // Add the last latLng

            // Clear the original list and add rearranged items
            latLngList.clear()
            latLngList.addAll(rearrangedLatLngs)

            // Log the rearranged latLngList for debugging
            Log.e("DirectionsActivity", "Rearranged latLngList: $latLngList")
        } else {
            Log.e("DirectionsActivity", "No rearrangement needed for latLngList.")
        }
    }

    private fun plotPolylineWithWaypoints(
        encodedPolyline: String,
        travelAdvisory: TravelAdvisory?,
        travelMode: String,
        intermediates: List<Waypoint>
    ) {
        // Map intermediates (waypoints) to their LatLng objects
        val stopoverLatLngs = intermediates.map { waypoint ->
            com.google.android.gms.maps.model.LatLng(
                waypoint.location.latLng.latitude,
                waypoint.location.latLng.longitude
            )
        }

        // Clear old stopover markers
        clearStopoverMarkers()

        // Now call the original plotPolyline method with the LatLngs
        plotPolyline(encodedPolyline, travelAdvisory, travelMode, stopoverLatLngs)

        Log.e("DirectionsActivity", "STOPLIST AT PLOT: $stopList")


        val intermediateStops = stopList.subList(1, stopList.size - 1)
        Log.e("DirectionsActivity", "SUBSTRINGED WAYPOINTS: $intermediateStops")
        var placeName = ""
        // Add new stopover markers with custom icon (flag) and address as title
        intermediates.forEach { waypoint ->
            val latLng = com.google.android.gms.maps.model.LatLng(
                waypoint.location.latLng.latitude,
                waypoint.location.latLng.longitude
            )

            for (stops in intermediateStops) {
                val stopsLatLng = stops.latlng.split(",")
                // Ensure correct parsing with error handling
                val latitude = stopsLatLng[0].toDoubleOrNull() ?: 0.0
                val longitude = stopsLatLng[1].toDoubleOrNull() ?: 0.0

                val stopsLatLngDeterminator = com.google.android.gms.maps.model.LatLng(latitude, longitude)

                if (latLng == stopsLatLngDeterminator) {
                    placeName = stops.name
                    break // Break the loop when the condition is met
                }
            }


            // Add marker with the fetched address as the title
            val marker = mMap.addMarker(
                MarkerOptions()
                    .position(latLng)
                    .title(placeName) // Use the address for the title
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW))
            )
            if (marker != null) {
                stopoverMarkers.add(marker)
            } // Add the marker to the list
        }
    }

    private fun plotPolyline(
        encodedPolyline: String,
        travelAdvisory: TravelAdvisory?,
        travelMode: String,
        intermediates: List<com.google.android.gms.maps.model.LatLng>
    ) {
        // Decode the polyline points
        val polylinePoints: List<com.google.android.gms.maps.model.LatLng> =
            PolyUtil.decode(encodedPolyline)

        // Clear previous polylines and markers
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
                val segmentPoints = polylinePoints.subList(
                    interval.startPolylinePointIndex,
                    interval.endPolylinePointIndex
                )

                // Create polyline options based on speed
                val polylineOptions = PolylineOptions().apply {
                    addAll(segmentPoints)
                    width(10f)
                    color(
                        when (interval.speed) {
                            "NORMAL" -> getColor(R.color.traffic_light_color)
                            "SLOW" -> getColor(R.color.traffic_moderate_color)
                            "TRAFFIC_JAM" -> getColor(R.color.traffic_heavy_color)
                            else -> getColor(R.color.brand_color)
                        }
                    )
                }

                val trafficPolyline = mMap.addPolyline(polylineOptions) // Create colored polyline
                polylines.add(trafficPolyline) // Store in the list
            }
        }

        // Add a marker at the destination
        destinationMarker?.remove()
        val destinationLatLng = polylinePoints.last()
        destinationMarker =
            mMap.addMarker(MarkerOptions().position(destinationLatLng).title(stopList.last().name))

        // Create bounds to focus the camera on the polyline and markers
        val builder = LatLngBounds.Builder().apply {
            for (point in polylinePoints) {
                include(point)
            }
            intermediates.forEach { stopoverLatLng -> // Include stopovers in the bounds
                include(stopoverLatLng)
            }
        }

        val bounds = builder.build()
        val padding = 100 // Padding in pixels
        val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, padding)

        // Move the camera to fit the polyline bounds
        mMap.animateCamera(cameraUpdate)
    }

    // Helper function to clear previous stopover markers
    private fun clearStopoverMarkers() {
        stopoverMarkers.forEach { it.remove() } // Remove all stopover markers
        stopoverMarkers.clear() // Clear the list
    }

    // Helper function to clear previous polylines
    private fun clearPolylines() {
        polylines.forEach { it.remove() } // Remove all polylines
        polylines.clear() // Clear the list
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

    private fun updateCourseCard(stopList: List<OriginDestinationStops>) {
        // Ensure there are at least two items in the stopList (origin and destination)
        if (stopList.isNotEmpty()) {
            // Set the Origin using View Binding
            val origin = stopList.first() // index 0 is the origin
            binding.originName.text = origin.name
            binding.originAddress.text = origin.address

            // Set the Destination using View Binding
            val destination = stopList.last() // last index is the destination
            binding.destinationName.text = destination.name
            binding.destinationAddress.text = destination.address

            // Check for stops between the origin and destination
            val stopCount = stopList.size - 2 // number of stops excluding origin and destination
            if (stopCount > 0) {
                // Display the number of stops in the stopNameTextView
                binding.stopName.text = "$stopCount stop(s) along the route"
                // Make stops layout visible
                binding.stopsLayout.visibility = View.VISIBLE
            } else {
                // Hide stops layout if there are no stops
                binding.stopsLayout.visibility = View.GONE
            }
        }
    }

    private fun updateLatLngList(stops: List<OriginDestinationStops>) {
        latLngList.clear() // Clear the existing list

        // Counter to track how many requests have been made
        var requestsInProgress = 0

        for (stop in stops) {
            requestsInProgress++ // Increment for each request

            // Assuming latlng is in the format "latitude,longitude"
            val latLng = stop.latlng // Directly use the latlng property from the stop

            // Check if the latLng is not empty
            if (latLng.isNotEmpty()) {
                latLngList.add(latLng) // Add the latLng string to the list
            } else {
                Log.e("DirectionsActivity", "LatLng for stop ${stop.name} is empty.")
            }

            requestsInProgress-- // Decrement the counter after processing

            // Log the updated list only when all requests are completed
            if (requestsInProgress == 0) {
                Log.e("DirectionsActivity", "Final latLngList: $latLngList")
            }
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // setMapStyle()

        // Check for location permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Enable the My Location layer
            mMap.isMyLocationEnabled = true

            // Optional: Hide the default My Location button
            mMap.uiSettings.isMyLocationButtonEnabled = false


        } else {
            // Request location permission
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun setupBottomSheet() {
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

        binding.editCourseButton.setOnClickListener {
            val intent = Intent(this, StopManagementActivity::class.java).apply {
                putExtra("ROUTES", ArrayList(stopList)) // Ensure stopList is passed as an ArrayList
            }
            stopResultLauncher.launch(intent)
        }

        binding.closeButton.setOnClickListener {
            // Dismiss the bottom sheet
            finish()
        }
    }

    private fun setMapStyle() {
        try {
            // Load the JSON file from the res/raw directory
            val inputStream = resources.openRawResource(R.raw.map_style_night)
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            // Apply the style to the map
            val success = mMap.setMapStyle(MapStyleOptions(jsonString))
            if (!success) {
                Log.e("MapStyle", "Style parsing failed.")
            }

        } catch (e: IOException) {
            e.printStackTrace()
            // Handle the exception if loading the style fails
        } catch (e: Resources.NotFoundException) {
            e.printStackTrace()
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