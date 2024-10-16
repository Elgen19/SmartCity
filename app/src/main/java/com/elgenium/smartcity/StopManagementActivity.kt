package com.elgenium.smartcity

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivityStopManagementBinding
import com.elgenium.smartcity.models.OriginDestinationStops
import com.elgenium.smartcity.network_reponses.PlusCodeResponse
import com.elgenium.smartcity.recyclerview_adapter.StopManagementAdapter
import com.elgenium.smartcity.recyclerview_helpers.StopTouchHelperCallback
import com.elgenium.smartcity.shared_preferences_keys.SettingsKeys
import com.elgenium.smartcity.singletons.PlusCodesSingleton
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class StopManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStopManagementBinding
    private val stopList = mutableListOf<OriginDestinationStops>()
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var adapter: StopManagementAdapter
    private val getPlaceResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val placeId = data?.getStringExtra("PLACE_ID")
            val placeName = data?.getStringExtra("PLACE_NAME")
            val placeAddress = data?.getStringExtra("PLACE_ADDRESS")
            val address = "$placeName $placeAddress"
            if (placeName != null && placeAddress != null && placeId != null) {
                fetchLatLng(address) { latLngString ->
                    if (latLngString != null) {
                        Log.d("StopManagementActivity", "Fetched LatLng: $latLngString")

                        // Create a new stop with the fetched latLng value
                        val newStop = OriginDestinationStops(
                            name = placeName,
                            address = placeAddress,
                            type = "Stop", // or any other type you prefer
                            latlng = latLngString, // Assigning the fetched latLng value here
                            placeid = placeId
                        )

                        // Now you can add the newStop to your stopList and notify the adapter
                        stopList.add(newStop)
                        adapter.updateStopTypes()
                        adapter.notifyDataSetChanged() // Notify the adapter about data change

                    } else {
                        Log.e("StopManagementActivity", "Failed to fetch LatLng for address: $placeName")
                    }
                }
            }

        }
    }




    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStopManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

        sharedPreferences = getSharedPreferences(SettingsKeys.PREFS_NAME, Context.MODE_PRIVATE)

        binding.setOptimizedWaypointSwitch.isChecked = sharedPreferences.getBoolean(SettingsKeys.KEY_OPTIMIZED_WAYPOINTS, false)
        Log.e("StopManagementActivity", "IS WAYPOINT OPTIMIZED AT ONCREATE: ${binding.setOptimizedWaypointSwitch.isChecked}")

        val initialStopList: MutableList<OriginDestinationStops>? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getSerializableExtra("ROUTES", ArrayList::class.java)?.let {
                @Suppress("UNCHECKED_CAST")
                it as? ArrayList<OriginDestinationStops>
            }
        } else {
            @Suppress("DEPRECATION")
            (intent.getSerializableExtra("ROUTES") as? ArrayList<*>)?.let {
                @Suppress("UNCHECKED_CAST")
                it as? ArrayList<OriginDestinationStops>
            }
        }

        Log.d("StopManagementActivity", "$stopList")

        // Initialize the stopList with the data from the intent
        initialStopList?.let { stopList.addAll(it) }

        // Set up RecyclerView
        adapter = StopManagementAdapter(this, stopList)
        binding.stopsRecyclerView.layoutManager = LinearLayoutManager(this)
        binding.stopsRecyclerView.adapter = adapter
        // Attach ItemTouchHelper to RecyclerView



        val itemTouchHelper = ItemTouchHelper(StopTouchHelperCallback(adapter))
        itemTouchHelper.attachToRecyclerView(binding.stopsRecyclerView)


        binding.addStopsButton.setOnClickListener {
            val intent = Intent(this, SearchActivity::class.java).apply {
                putExtra("FROM_STOP_MANAGEMENT_ACTIVITY", true)
            }
            getPlaceResultLauncher.launch(intent)
        }



        binding.saveButton.setOnClickListener {
            // Convert the stopList into an ArrayList of OriginDestinationStops
            val stopList = ArrayList<OriginDestinationStops>(stopList)

            with(sharedPreferences.edit()) {
                putBoolean(
                    SettingsKeys.KEY_OPTIMIZED_WAYPOINTS,
                    binding.setOptimizedWaypointSwitch.isChecked
                )
                apply()
            }

            // Create an intent to hold the result
            val resultIntent = Intent().apply {
                putExtra("STOP_LIST", stopList)  // Use putExtra to pass the ArrayList
                putExtra("IS_UPDATED", true)
            }

            Log.d("StopManagementActivity", "STOP LIST: $stopList")


            // Set the result and finish the activity
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

    }

    private fun fetchLatLng(address: String, onResult: (String?) -> Unit) {
        val apiKey = BuildConfig.MAPS_API_KEY // Replace with your actual API key

        val call = PlusCodesSingleton.instance.getPlusCode(address, apiKey)

        call.enqueue(object : Callback<PlusCodeResponse> {
            override fun onResponse(call: Call<PlusCodeResponse>, response: Response<PlusCodeResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val plusCodeResponse = response.body()

                    // Extract latitude and longitude
                    val latitude = plusCodeResponse?.plus_code?.geometry?.location?.lat
                    val longitude = plusCodeResponse?.plus_code?.geometry?.location?.lng

                    if (latitude != null && longitude != null) {
                        val latLngString = "$latitude,$longitude" // Format as "lat,lng"
                        Log.d("LatLng", "LatLng: $latLngString")
                        onResult(latLngString) // Pass the formatted string to the callback
                    } else {
                        Log.e("LatLng", "Latitude or longitude is null")
                        onResult(null) // Return null if lat/lng is not found
                    }
                } else {
                    Log.e("Plus Code", "Error in response: ${response.message()}")
                    onResult(null) // Return null in case of an error
                }
            }

            override fun onFailure(call: Call<PlusCodeResponse>, t: Throwable) {
                Log.e("Plus Code", "Error fetching Plus Code", t)
                onResult(null) // Return null in case of failure
            }
        })
    }



}
