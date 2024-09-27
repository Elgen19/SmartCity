package com.elgenium.smartcity

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.GoogleMap.CameraPerspective
import com.google.android.libraries.navigation.CustomRoutesOptions
import com.google.android.libraries.navigation.DisplayOptions
import com.google.android.libraries.navigation.ForceNightMode.FORCE_NIGHT
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.Navigator.ArrivalListener
import com.google.android.libraries.navigation.Navigator.RemainingTimeOrDistanceChangedListener
import com.google.android.libraries.navigation.Navigator.RouteChangedListener
import com.google.android.libraries.navigation.RoadSnappedLocationProvider
import com.google.android.libraries.navigation.RoutingOptions
import com.google.android.libraries.navigation.SimulationOptions
import com.google.android.libraries.navigation.StylingOptions
import com.google.android.libraries.navigation.SupportNavigationFragment
import com.google.android.libraries.navigation.Waypoint


class StartNavigationsActivity : AppCompatActivity() {

    private val TAG = "StartNavigationsActivity"
    private lateinit var navigator: Navigator
    private lateinit var navFragment: SupportNavigationFragment
    private lateinit var routingOptions: RoutingOptions
    private var mArrivalListener: ArrivalListener? = null
    private var mRouteChangedListener: RouteChangedListener? = null
    private var mRemainingTimeOrDistanceChangedListener: RemainingTimeOrDistanceChangedListener? = null
    private var mLocationListener: RoadSnappedLocationProvider.LocationListener? = null
    private var mRoadSnappedLocationProvider: RoadSnappedLocationProvider? = null


    // Define the Sydney Opera House by specifying its place ID.
    private val SYDNEY_OPERA_HOUSE = "ChIJ616SM-GYqTMRMNepa3oD2v4"

    // Set fields for requesting location permission.
    private val PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 1
    private var locationPermissionGranted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_start_navigations)

        val routeToken = intent.getStringExtra("ROUTE_TOKEN")
        val placeIds = intent.getStringArrayListExtra("PLACE_IDS")

        // Use the placeIds and routeToken for further navigation logic
        if (placeIds != null && routeToken != null) {
            requestLocationPermissions(routeToken, placeIds)
        }


    }

    private fun initializeNavigationSdk(routeToken: String, placeIds: ArrayList<String>) {
        // Request location permission.
        if (ContextCompat.checkSelfPermission(
                applicationContext, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED) {
            locationPermissionGranted = true
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION
            )
        }

        if (!locationPermissionGranted) {
            displayMessage("Error loading Navigation SDK: The user has not granted location permission.")
            return
        }

        // Get a navigator.
        NavigationApi.getNavigator(
            this,
            object : NavigationApi.NavigatorListener {
                override fun onNavigatorReady(navigator: Navigator) {
                    displayMessage("Navigator ready.")
                    this@StartNavigationsActivity.navigator = navigator
                    navFragment = supportFragmentManager.findFragmentById(R.id.navigation_fragment) as SupportNavigationFragment
                    navFragment.setForceNightMode(FORCE_NIGHT)
                    navFragment.setTripProgressBarEnabled(true)
                    navFragment.setSpeedometerEnabled(true)
                    navFragment.setSpeedLimitIconEnabled(true)
                    navFragment.setStylingOptions(StylingOptions()
                        .headerGuidanceRecommendedLaneColor(resources.getColor(R.color.brand_color))
                        .primaryDayModeThemeColor(resources.getColor(R.color.secondary_color)))

                    // Set the camera to follow the device location with 'TILTED' driving view.
                    navFragment.getMapAsync { googleMap ->
                        try {
                            if (ContextCompat.checkSelfPermission(
                                    applicationContext,
                                    Manifest.permission.ACCESS_FINE_LOCATION
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                googleMap.followMyLocation(CameraPerspective.TILTED)
                            } else {
                                displayMessage("Location permission is not granted. Unable to follow location.")
                            }
                        } catch (e: SecurityException) {
                            displayMessage("Error accessing location: ${e.message}")
                        }
                    }

                    navigateWithMultipleStops(routeToken, placeIds)
                   // setupArrivalListener()
                }

                override fun onError(@NavigationApi.ErrorCode errorCode: Int) {
                    when (errorCode) {
                        NavigationApi.ErrorCode.NOT_AUTHORIZED -> displayMessage("Error loading Navigation SDK: Your API key is invalid or not authorized to use the Navigation SDK.")
                        NavigationApi.ErrorCode.TERMS_NOT_ACCEPTED -> displayMessage("Error loading Navigation SDK: User did not accept the Navigation Terms of Use.")
                        NavigationApi.ErrorCode.NETWORK_ERROR -> displayMessage("Error loading Navigation SDK: Network error.")
                        NavigationApi.ErrorCode.LOCATION_PERMISSION_MISSING -> displayMessage("Error loading Navigation SDK: Location permission is missing.")
                        else -> displayMessage("Error loading Navigation SDK: $errorCode")
                    }
                }
            }
        )
    }

    private fun requestLocationPermissions(routeToken: String, placeIds: ArrayList<String>) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.FOREGROUND_SERVICE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.FOREGROUND_SERVICE_LOCATION),
                PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION)
        } else {
            // Permissions are already granted, you can start your service or navigation
            initializeNavigationSdk(routeToken, placeIds)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        locationPermissionGranted = false
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                locationPermissionGranted = true
            }
        }
    }

    private fun setupArrivalListener() {
        // Add an ArrivalListener to the Navigator
        navigator.addArrivalListener { showArrivalNotification() }
    }

    private fun showArrivalNotification() {
        // Example of showing an in-app alert using a Toast (you can customize this with dialogs or notifications)
        Toast.makeText(this, "You have arrived at your destination!", Toast.LENGTH_LONG).show()

        // Optionally, you could also trigger a vibration or play a sound
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))

        // You could also play a voice instruction here if needed
         //textToSpeech.speak("You have arrived", TextToSpeech.QUEUE_FLUSH, null, null)

        // Alternatively, you can also use a notification
        showNotification()
    }

    private fun showNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = 1 // Arbitrary ID
        val channelId = "arrival_channel" // Create a unique channel ID

        // Create an explicit intent to launch the specific activity
        val intent = Intent(this, StartNavigationsActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            // You can add extras here if needed
            putExtra("notification_type", "arrival")
        }

        // Create a pending intent that wraps the intent to open StartNavigationsActivity
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // For Android Oreo and above, you need to create a notification channel
        val importance = NotificationManager.IMPORTANCE_HIGH
        val notificationChannel = NotificationChannel(channelId, "Arrival Notifications", importance)
        notificationChannel.description = "Notifications for arrival at destinations"
        notificationManager.createNotificationChannel(notificationChannel)

        // Build the notification
        val notification = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.smart_city_logo) // Replace with your app icon
            .setContentTitle("Arrived!")
            .setContentText("You have arrived at the destination.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent) // Set the pending intent to be triggered when the notification is pressed
            .build()

        // Display the notification
        notificationManager.notify(notificationId, notification)
    }

    private fun navigateWithMultipleStops(routeToken: String, placesIds: List<String>) {
        // Create a list of waypoints based on the provided place IDs
        val waypoints = mutableListOf<Waypoint>()

        try {
            for (placeId in placesIds) {
                // Build each waypoint using the placeId and add to the list
                val waypoint = Waypoint.builder()
                    .setPlaceIdString(placeId)
                    .setVehicleStopover(true)
                    .build()
                waypoints.add(waypoint)
            }
        } catch (e: Waypoint.UnsupportedPlaceIdException) {
            displayMessage("Error starting navigation: One or more Place IDs are not supported.")
            return
        }

        val customRoutesOptions =
            CustomRoutesOptions.builder()
                .setRouteToken(routeToken)
                .setTravelMode(CustomRoutesOptions.TravelMode.DRIVING)
                .build()

        val displayOptions = DisplayOptions().apply {
            showStopSigns(true)
            showTrafficLights(true)
        }


        // Set multiple destinations using the list of waypoints
        val pendingRoute = navigator.setDestinations(waypoints,customRoutesOptions, displayOptions)

        // Handle the route calculation result
        pendingRoute.setOnResultListener { code ->
            when (code) {
                Navigator.RouteStatus.OK -> {
                    // Route found, start navigation
                    displayMessage("Route successfully calculated with multiple stops!")

                    // Enable voice audio guidance for turn-by-turn navigation
                    navigator.setAudioGuidance(Navigator.AudioGuidance.VOICE_ALERTS_AND_GUIDANCE)

                    registerNavigationListeners();


                    navigator.simulator.simulateLocationsAlongExistingRoute(SimulationOptions().speedMultiplier(5F))


                    // Start turn-by-turn guidance along the route
                    navigator.startGuidance()
                }
                Navigator.RouteStatus.NO_ROUTE_FOUND -> {
                    displayMessage("Error starting navigation: No route found.")
                }
                Navigator.RouteStatus.NETWORK_ERROR -> {
                    displayMessage("Error starting navigation: Network error.")
                }
                Navigator.RouteStatus.ROUTE_CANCELED -> {
                    displayMessage("Error starting navigation: Route canceled.")
                }
                else -> {
                    displayMessage("Error starting navigation: $code")
                }
            }
        }
    }


    private fun registerNavigationListeners() {
        mArrivalListener =
            ArrivalListener { arrivalEvent ->
                displayMessage(
                    ("onArrival: You've arrived at a waypoint: "
                            + navigator.currentRouteSegment.destinationWaypoint
                        .title)
                )
                // Start turn-by-turn guidance for the next leg of the route.
                if (arrivalEvent.isFinalDestination) {
                    displayMessage(
                        "onArrival: You've arrived at the final destination."
                    )
                } else {
                    navigator.continueToNextDestination()
                    navigator.startGuidance()
                }
            }
        // Listens for arrival at a waypoint.
        navigator.addArrivalListener(mArrivalListener)

        mRouteChangedListener =
            RouteChangedListener {
                displayMessage(
                    ("onRouteChanged: The driver's route has changed. Current waypoint: "
                            + navigator.currentRouteSegment.destinationWaypoint
                        .title),
                )
            }
        // Listens for changes in the route.
        navigator.addRouteChangedListener(mRouteChangedListener)

        // Listens for road-snapped location updates.
        mRoadSnappedLocationProvider = NavigationApi.getRoadSnappedLocationProvider(application)
        mLocationListener =
            object : RoadSnappedLocationProvider.LocationListener {
                override fun onLocationChanged(location: Location) {
//                    displayMessage(
//                        "onLocationUpdated: Navigation engine has provided a new"
//                                + " road-snapped location: "
//                                + location.toString()
//                    )
                }

                override fun onRawLocationUpdate(location: Location) {
//                    displayMessage(
//                        (("onLocationUpdated: Navigation engine has provided a new"
//                                + " raw location: "
//                                + location.toString()))
//                    )
                }
            }
        if (mRoadSnappedLocationProvider != null) {
            mRoadSnappedLocationProvider!!.addLocationListener(mLocationListener)
        } else {
            displayMessage("ERROR: Failed to get a location provider")
        }

        mRemainingTimeOrDistanceChangedListener =
            RemainingTimeOrDistanceChangedListener {
                displayMessage(
                    "onRemainingTimeOrDistanceChanged: Time or distance estimate" + " has changed."
                )
            }
        // Listens for changes in time or distance.
        navigator.addRemainingTimeOrDistanceChangedListener(
            60, 100, mRemainingTimeOrDistanceChangedListener
        )
    }



    private fun displayMessage(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        Log.e(TAG, message)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop any ongoing navigation
        if (::navigator.isInitialized) {
            navigator.stopGuidance()
        }

        // Perform any other necessary cleanups
        supportFragmentManager.findFragmentById(R.id.navigation_fragment)?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }


        if (this.isFinishing) {
            navigator.removeArrivalListener(mArrivalListener);
            navigator.removeRouteChangedListener(mRouteChangedListener);
            navigator.removeRemainingTimeOrDistanceChangedListener(
                mRemainingTimeOrDistanceChangedListener);
            mRoadSnappedLocationProvider?.removeLocationListener(mLocationListener)
            displayMessage("OnDestroy: Released navigation listeners.");
        }
        navigator.clearDestinations()
        navigator.cleanup()

    }

}
