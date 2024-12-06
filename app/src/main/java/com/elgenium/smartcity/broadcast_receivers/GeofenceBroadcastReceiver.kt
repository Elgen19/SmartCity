package com.elgenium.smartcity.broadcast_receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent

class GeofenceBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val geofencingEvent = GeofencingEvent.fromIntent(intent)

        if (geofencingEvent != null) {
            if (geofencingEvent.hasError()) {
                val errorMessage = GeofenceStatusCodes.getStatusCodeString(geofencingEvent.errorCode)
                Log.e("Geofence", "Error: $errorMessage")
                return
            }
        }

        // Get the type of transition (ENTER, EXIT, or DWELL)
        if (geofencingEvent != null) {
            when (val transition = geofencingEvent.geofenceTransition) {
                Geofence.GEOFENCE_TRANSITION_ENTER -> {
                    Log.d("Geofence", "Entered geofence")
                    // Handle entering geofence (e.g., show notification, start activity, etc.)
                }
                Geofence.GEOFENCE_TRANSITION_EXIT -> {
                    Log.d("Geofence", "Exited geofence")
                    // Handle exiting geofence (e.g., show notification, stop tracking, etc.)
                }
                else -> {
                    Log.e("Geofence", "Unknown transition: $transition")
                }
            }
        }
    }
}
