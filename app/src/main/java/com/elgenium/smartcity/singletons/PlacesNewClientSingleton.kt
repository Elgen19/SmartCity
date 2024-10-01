package com.elgenium.smartcity.singletons

import android.content.Context
import com.elgenium.smartcity.BuildConfig
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient

object PlacesNewClientSingleton {
    private var instance: PlacesClient? = null

    fun getPlacesClient(context: Context): PlacesClient {
        if (instance == null) {
            // Initialize the PlacesClient if it doesn't exist
            val apiKey = BuildConfig.MAPS_API_KEY
            if (apiKey.isEmpty() || apiKey == "DEFAULT_API_KEY") {
                throw IllegalStateException("No API key found.")
            }
            Places.initializeWithNewPlacesApiEnabled(context.applicationContext, apiKey)
            instance = Places.createClient(context)
        }
        return instance!!
    }
}