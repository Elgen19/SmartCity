import android.content.Context
import com.elgenium.smartcity.BuildConfig
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient

object PlacesClientSingleton {
    private var placesClient: PlacesClient? = null

    fun getClient(context: Context): PlacesClient {
        if (placesClient == null) {
            val apiKey = BuildConfig.MAPS_API_KEY
            if (!Places.isInitialized()) {
                Places.initialize(context, apiKey)
            }
            placesClient = Places.createClient(context)
        }
        return placesClient!!
    }
}
