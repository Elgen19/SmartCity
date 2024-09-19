package com.elgenium.smartcity

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivityStopManagementBinding
import com.elgenium.smartcity.models.OriginDestinationStops
import com.elgenium.smartcity.recyclerview_adapter.StopManagementAdapter
import com.elgenium.smartcity.recyclerview_helpers.StopTouchHelperCallback

class StopManagementActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStopManagementBinding
    private val stopList = mutableListOf<OriginDestinationStops>()
    private lateinit var adapter: StopManagementAdapter
    private val getPlaceResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            val placeId = data?.getStringExtra("PLACE_ID")
            val placeName = data?.getStringExtra("PLACE_NAME")
            val placeAddress = data?.getStringExtra("PLACE_ADDRESS")

            if (placeId != null && placeName != null && placeAddress != null) {
                val newStop = OriginDestinationStops(
                    name = placeName,
                    address = placeAddress,
                    type = "Stop" // or any other type you prefer
                )

               Log.d("StopManagementActivity", "STOPS AT SEARCH: $newStop" )

                stopList.add(newStop)
                adapter.updateStopTypes()
                adapter.notifyDataSetChanged() // Notify the adapter about data change
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStopManagementBinding.inflate(layoutInflater)
        setContentView(binding.root)

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
                putExtra("fromDirectionsActivity", "yes")
            }
            getPlaceResultLauncher.launch(intent)
        }

        binding.saveButton.setOnClickListener {
            // Convert the stopList into an ArrayList of OriginDestinationStops
            val stopList = ArrayList<OriginDestinationStops>(stopList)

            // Create an intent to hold the result
            val resultIntent = Intent().apply {
                putExtra("STOP_LIST", stopList)  // Use putExtra to pass the ArrayList
            }

            Log.d("StopManagementActivity", "STOP LIST: $stopList")


            // Set the result and finish the activity
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }

    }

    private fun saveAndReturnResults() {
        // Create an intent to return the result
        val resultIntent = Intent().apply {
            // Put the list of stops as an extra
            putExtra("ROUTES", ArrayList(stopList))
        }

        // Set the result and finish the activity
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }


}
