package com.elgenium.smartcity.geofences

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.elgenium.smartcity.broadcast_receivers.GeofenceBroadcastReceiver
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofencingClient
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices

class GeofenceManager(private val context: Context) {

    private val geofencingClient: GeofencingClient = LocationServices.getGeofencingClient(context)
    private lateinit var geofencePendingIntent: PendingIntent

    // Method to create and add geofence
    fun addGeofence(
        latitude: Double,
        longitude: Double,
        radius: Float,
        geofenceId: String,
        expirationDuration: Long = Geofence.NEVER_EXPIRE
    ) {

        // Create a geofence
        val geofence = Geofence.Builder()
            .setRequestId(geofenceId)
            .setCircularRegion(latitude, longitude, radius)
            .setExpirationDuration(expirationDuration)
            .setTransitionTypes(Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_EXIT)
            .build()

        // Create geofencing request
        val geofencingRequest = GeofencingRequest.Builder()
            .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_ENTER)  // Trigger on entering
            .addGeofence(geofence)
            .build()

        // Create a pending intent for handling geofence transitions
        val intent = Intent(context, GeofenceBroadcastReceiver::class.java)
        geofencePendingIntent = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        // Add geofence to geofencing client
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        geofencingClient.addGeofences(geofencingRequest, geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("Geofence", "Geofence added successfully")
            }
            .addOnFailureListener { exception ->
                Log.e("Geofence", "Failed to add geofence: ${exception.message}")
            }
    }




    // Method to remove geofence
    fun removeGeofence(geofenceId: String) {
        geofencingClient.removeGeofences(geofencePendingIntent)
            .addOnSuccessListener {
                Log.d("Geofence", "Geofence removed successfully")
            }
            .addOnFailureListener { exception ->
                Log.e("Geofence", "Failed to remove geofence: ${exception.message}")
            }
    }

    // Method to start monitoring geofences
    fun startGeofenceMonitoring() {
        // Register receiver for geofence transition events
        val intentFilter = IntentFilter()
        intentFilter.addAction("com.elgenium.smartcity.geofence.TRANSITION")

        // Register receiver with RECEIVER_NOT_EXPORTED to protect from other apps
        ContextCompat.registerReceiver(
            context,
            GeofenceBroadcastReceiver(),
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }


    // Method to stop monitoring geofences
    fun stopGeofenceMonitoring() {
        context.unregisterReceiver(GeofenceBroadcastReceiver())
    }
}