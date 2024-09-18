package com.elgenium.smartcity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivityDirectionsBinding
import com.elgenium.smartcity.databinding.BottomSheetGetDirectionsBinding
import com.elgenium.smartcity.models.OriginDestinationStops
import com.elgenium.smartcity.recyclerview_adapter.OriginDestinationAdapter
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.material.bottomsheet.BottomSheetDialog

class DirectionsActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var binding: ActivityDirectionsBinding
    private  val searchActivityResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // Retrieve the place data from SearchActivity
            val placeId = result.data?.getStringExtra("PLACE_ID")
            val placeName = result.data?.getStringExtra("PLACE_NAME")
            val placeAddress = result.data?.getStringExtra("PLACE_ADDRESS")

            // Handle the returned data
            if (placeId != null && placeName != null && placeAddress != null) {
                resultFromSearchActivity = "$placeId==$placeName==$placeAddress"
                Log.d("DirectionsActivity", "result: $resultFromSearchActivity")
            }
        }
    }
    private var resultFromSearchActivity = "No result"



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
        val origin = intent.getStringExtra("ORIGIN") ?: ""
        val destination = intent.getStringExtra("DESTINATION") ?: ""

        // Extract destination data (Place Name, Address, LatLng)
        val destinationData = destination.split("==").takeIf { it.size == 3 }
            ?: listOf("Unknown Destination", "Unknown Address", "")

        setupBottomSheet(destinationData)

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

    private fun setupBottomSheet(destinationData: List<String>) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetBinding = BottomSheetGetDirectionsBinding.inflate(layoutInflater)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        val places = mutableListOf(
            OriginDestinationStops(name = "Your Location", address = "", type = "Origin"),
            OriginDestinationStops(name = destinationData[0], address = destinationData[1], type = "Destination")
        )
        val adapter = OriginDestinationAdapter(places)
        bottomSheetBinding.originDestinationRecyclerView.adapter = adapter
        bottomSheetBinding.originDestinationRecyclerView.layoutManager = LinearLayoutManager(this)


        bottomSheetDialog.show()

        bottomSheetDialog.show()


        bottomSheetBinding.btnEdit.setOnClickListener {
            // Convert the places list into an ArrayList of stops to pass via intent
            val stopList = ArrayList<OriginDestinationStops>()  // Create an empty ArrayList

            // Convert the places (Pair<String, String>) to OriginDestinationStops
            for (place in places) {
                val (name, address, type) = place
                val stop = OriginDestinationStops(name = name, address = address, type = type)
                stopList.add(stop)
            }

            // Pass the list to StopManagementActivity
            val intent = Intent(this, StopManagementActivity::class.java)
            intent.putExtra("ROUTES", stopList)  // Use putExtra instead of putParcelableArrayListExtra
            startActivity(intent)
        }


        bottomSheetBinding.closeButton.setOnClickListener {
            bottomSheetDialog.dismiss()
        }

    }


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }


}