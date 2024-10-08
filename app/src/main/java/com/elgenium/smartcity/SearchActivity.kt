package com.elgenium.smartcity

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivitySearchBinding
import com.elgenium.smartcity.models.Search
import com.elgenium.smartcity.recyclerview_adapter.RecentSearchAdapter
import com.elgenium.smartcity.recyclerview_adapter.TextSearchAdapter
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.singletons.PlacesNewClientSingleton
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private val placesClient by lazy { PlacesNewClientSingleton.getPlacesClient(this) }
    private lateinit var textSearchAdapter: TextSearchAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationBias: RectangularBounds? = null
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var recentSearchAdapter: RecentSearchAdapter
    private val recentSearches = mutableListOf<Search>()
    private var lastQuery: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySearchBinding.inflate(layoutInflater)
        setContentView(binding.root)


        // sets the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        auth = FirebaseAuth.getInstance()
        val currentUser = auth.currentUser
        if (currentUser != null) {
            val userId = currentUser.uid
            database = FirebaseDatabase.getInstance().reference.child("Users").child(userId).child("recent_searches")
        } else {
            // Handle user not signed in
            Log.e("SearchActivity", "User not signed in")
            return
        }

        // Initialize FusedLocationProviderClient to get the current location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Check and request location permissions
        if (checkLocationPermission()) {
            getCurrentLocation()
        }

        // Initialize the RecyclerView with a LinearLayoutManager
        val layoutManager = LinearLayoutManager(this)
        binding.textSearchRecyclerView.layoutManager = layoutManager

        // Create a custom drawable with your desired color
        val dividerDrawable = ColorDrawable(ContextCompat.getColor(this, R.color.dark_gray))

        // Create the DividerItemDecoration and set the drawable
        val dividerItemDecoration = DividerItemDecoration(
            binding.textSearchRecyclerView.context,
            layoutManager.orientation
        )
        dividerItemDecoration.setDrawable(dividerDrawable)

        // Add the custom divider to the RecyclerView
        binding.textSearchRecyclerView.addItemDecoration(dividerItemDecoration)

        val fromDirectionsActivity = intent.getStringExtra("fromDirectionsActivity") ?: ""

        textSearchAdapter = TextSearchAdapter(emptyList()) { selectedPlace ->
            // Save recent search
            val placeId = selectedPlace.id // Assuming selectedPlace has id property
            val placeName = selectedPlace.name // Assuming selectedPlace has name property
            val placeAddress = selectedPlace.address // Assuming selectedPlace has address property
            val placeType = selectedPlace.placeTypes

            Log.e("SearchActivity", "PLACE TYPE: $placeType")
            Log.e("SearchActivity", "PLACE NAME: $placeName")



            if (placeName != null && placeAddress != null && placeId != null && placeType != null) {
                saveRecentSearch(placeName, placeAddress, placeId, placeType.toString())
                trackSearchAction(placeName, placeType.toString())

            }

            if (fromDirectionsActivity == "yes") {
                // Create an intent to send data back
                val resultIntent = Intent().apply {
                    putExtra("PLACE_ID", placeId)
                    putExtra("PLACE_NAME", placeName)
                    putExtra("PLACE_ADDRESS", placeAddress)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish() // Close SearchActivity and return to StopManagementActivity
            } else {
                val intent = Intent(this, PlacesActivity::class.java)
                intent.putExtra("PLACE_ID", placeId)
                startActivity(intent)
                finish()
            }
        }

        binding.textSearchRecyclerView.adapter = textSearchAdapter

        setupAutocomplete()
        searchViewAppearance()
        setupCategoryButtonListeners()


        recentSearchAdapter = RecentSearchAdapter(recentSearches) { recentSearch ->
            // Get the place ID using the place name
            val placeId = recentSearch.placeId
            val placeName = recentSearch.placeName
            val placeAddress = recentSearch.placeAddress
            val placeType = recentSearch.placeType

            trackSearchAction(placeName, placeType)

            if (fromDirectionsActivity == "yes") {
                // Create an intent to send data back
                val resultIntent = Intent().apply {
                    putExtra("PLACE_ID", placeId)
                    putExtra("PLACE_NAME", placeName)
                    putExtra("PLACE_ADDRESS", placeAddress)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish() // Close SearchActivity and return to StopManagementActivity
            } else {
                val intent = Intent(this, PlacesActivity::class.java)
                intent.putExtra("PLACE_ID" , placeId)
                startActivity(intent)
                finish()

            }
        }


        // Set up the RecyclerView for recent searches
        binding.recentSearchRecyclerView.layoutManager = LinearLayoutManager(this)

        // Create a custom drawable with your desired color
        val dividerDrawableForRecentSearches = ColorDrawable(ContextCompat.getColor(this, R.color.dark_gray))

        val dividerItemDecorationForRecentSearches = DividerItemDecoration(
            binding.recentSearchRecyclerView.context,
            (binding.recentSearchRecyclerView.layoutManager as LinearLayoutManager).orientation
        )
        dividerItemDecorationForRecentSearches.setDrawable(dividerDrawableForRecentSearches)
        binding.recentSearchRecyclerView.addItemDecoration(dividerItemDecorationForRecentSearches)

        binding.recentSearchRecyclerView.adapter = recentSearchAdapter


        loadRecentSearches()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Define the behavior when the back button is pressed
                val intent = Intent(this@SearchActivity, PlacesActivity::class.java)
                startActivity(intent)
                finish() // This will finish the current activity
            }
        })

    }

    private fun trackSearchAction(query: String, type: String) {
        val userId = auth.currentUser?.uid
        val userRef =
            userId?.let { FirebaseDatabase.getInstance().reference.child("Users").child(it).child("interactions").child("searchedPlaces") }

        // Generate a unique ID for this search (could use a timestamp or any unique identifier)
        val searchId = userRef?.push()?.key // Generate a unique key
        val searchData = mapOf(
            "query" to query,
            "timestamp" to System.currentTimeMillis(),
            "type" to type
        )

        searchId?.let {
            userRef.child(it).setValue(searchData).addOnSuccessListener {
                Log.e("DashboardActivity", "Search action tracked successfully for query: $query")
            }.addOnFailureListener { exception ->
                Log.e("DashboardActivity", "Error tracking search action: $exception")
            }
        }
    }


    private fun setupCategoryButtonListeners() {
        binding.btnHotels.setOnClickListener { launchCategoryIntent("hotel") }
        binding.btnRestaurants.setOnClickListener { launchCategoryIntent("restaurant") }
        binding.btnPharmacy.setOnClickListener { launchCategoryIntent("pharmacy") }
        binding.btnATM.setOnClickListener { launchCategoryIntent("atm") }
        binding.btnSuperMarket.setOnClickListener { launchCategoryIntent("supermarket") }
        binding.btnGas.setOnClickListener { launchCategoryIntent("gas_station") }
    }

    private fun launchCategoryIntent(category: String) {
        val intent = Intent(this, PlacesActivity::class.java)
        intent.putExtra("CATEGORY", category)
        startActivity(intent)
    }

    private fun loadRecentSearches() {
        database.get().addOnSuccessListener { dataSnapshot ->
            recentSearches.clear()
            for (snapshot in dataSnapshot.children) {
                val search = snapshot.getValue(Search::class.java)
                search?.let { recentSearches.add(it) }
            }
            recentSearchAdapter.notifyDataSetChanged()
            updateViews()
        }.addOnFailureListener { exception ->
            Log.e("SearchActivity", "Failed to load recent searches", exception)
        }
    }

    private fun searchViewAppearance(){
        // set search view text hint color
        val searchEditText = binding.searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
        searchEditText.setTextColor(ContextCompat.getColor(this, R.color.gray))
        searchEditText.setHintTextColor(ContextCompat.getColor(this, R.color.dark_gray))

        // close icon color
        val closeIcon = binding.searchView.findViewById<ImageView>(androidx.appcompat.R.id.search_close_btn)
        closeIcon.setColorFilter(ContextCompat.getColor(this, R.color.red))
    }

    private fun saveRecentSearch(placeName: String, placeAddress: String, placeId: String, placeType: String) {
        val timestamp = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date())

        // Create a new Search object with the updated timestamp
        val search = Search(placeName, placeAddress, timestamp, placeId, placeType )
        Log.e("SearchActivity", "Search: $search")


        // Query the database to check if the search already exists
        database.orderByChild("location").equalTo(placeName).get().addOnSuccessListener { dataSnapshot ->
            if (dataSnapshot.exists()) {
                // If search exists, update the timestamp for all matching entries
                for (snapshot in dataSnapshot.children) {
                    snapshot.ref.child("timestamp").setValue(timestamp)
                        .addOnSuccessListener {
                            Log.d("SearchActivity", "Search timestamp updated successfully.")
                        }
                        .addOnFailureListener { exception ->
                            Log.e("SearchActivity", "Error updating timestamp", exception)
                        }
                }
            } else {
                // If search does not exist, add a new entry
                val newSearchRef = database.push()
                newSearchRef.setValue(search)
                    .addOnSuccessListener {
                        Log.d("SearchActivity", "New search entry added successfully.")
                    }
                    .addOnFailureListener { exception ->
                        Log.e("SearchActivity", "Error adding new search entry", exception)
                    }
            }
        }.addOnFailureListener { exception ->
            Log.e("SearchActivity", "Error querying database", exception)
        }
    }

    private fun updateViews() {
        // Check if there are recent searches or autocomplete predictions
        if (recentSearches.isEmpty() && textSearchAdapter.itemCount == 0) {
            binding.lotifyAnimation.visibility = View.VISIBLE
            binding.emptyDataLabel.visibility = View.VISIBLE
            binding.recentSearchesLabel.visibility = View.GONE
        } else {
            binding.lotifyAnimation.visibility = View.GONE
            binding.emptyDataLabel.visibility = View.GONE
        }
    }

    private fun checkLocationPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            true
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            false
        }
    }

    private fun getCurrentLocation() {
        // Check if the location permission is granted
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            // Permission is granted, get the location
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    location?.let {
                        setLocationBias(it)
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("SearchActivity", "Failed to get location", exception)
                }
        } else {
            // Permission is not granted, request it
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    // Permission granted, proceed to get the location
                    getCurrentLocation()
                } else {
                    // Permission denied, handle accordingly
                    Log.e("SearchActivity", "Location permission denied")
                    // Optionally, inform the user why the permission is needed
                }
            }
        }
    }

    private fun setLocationBias(location: Location) {
        // Define the rectangular bounds around the current location
        val southwest = LatLng(location.latitude - 0.1, location.longitude - 0.1) // Southwest corner
        val northeast = LatLng(location.latitude + 0.1, location.longitude + 0.1) // Northeast corner
        locationBias = RectangularBounds.newInstance(southwest, northeast)
    }




    private fun setupAutocomplete() {
        // Define a debounce delay (in milliseconds)
        val debounceDelay: Long = 500 // Adjust this value as needed
        var searchJob: Job? = null

        // Set the OnQueryTextListener
        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                binding.searchView.requestFocus()
                // Handle the query submission with Text Search
                query?.let { searchText ->
                    // Show loading animation

                    binding.lottieAnimation.visibility = View.VISIBLE

                    val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.TYPES)

                    // Use the builder to create a SearchByTextRequest object
                    val searchByTextRequest = SearchByTextRequest.builder(searchText, placeFields)
                        .setMaxResultCount(15)
                        .setPlaceFields(placeFields)
                        .build()

                    Log.e("SearchActivity", "SEARCH TEXT IS: $searchText")


                    // Call PlacesClient.searchByText() to perform the search
                    placesClient.searchByText(searchByTextRequest)
                        .addOnSuccessListener { response ->
                            val places: List<Place> = response.places
                            // Process the List of Place objects returned
                            Log.e("SearchActivity", "Found ${places.size} places.")
                            places.forEach { place ->
                                Log.e("SearchActivity", "Place ID: ${place.id}, Name: ${place.name}, TYPES: ${place.placeTypes}")
                            }
                            binding.textSearchRecyclerView.visibility = View.VISIBLE
                            textSearchAdapter.updatePlaces(places)
                        }
                        .addOnFailureListener { exception ->
                            Log.e("SearchActivity", "Error retrieving places", exception)
                        }
                        .addOnCompleteListener {
                            // Hide loading animation
                            binding.lottieAnimation.visibility = View.GONE
                        }
                }
                return true
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                val query = newText.orEmpty()
                if (query == lastQuery) return true // Skip if the query is the same as the last one
                lastQuery = query

                searchJob?.cancel()
                searchJob = lifecycleScope.launch {
                    // Show loading animation
                    binding.lottieAnimation.visibility = View.VISIBLE
                    binding.searchView.requestFocus()
                    delay(debounceDelay) // Wait for debounce delay
                    if (query.isNotEmpty()) {
                        // Show the RecyclerView and hide recent searches
                        binding.textSearchRecyclerView.visibility = View.VISIBLE
                        binding.recentSearchesLabel.visibility = View.GONE
                        binding.recentSearchRecyclerView.visibility = View.GONE
                        binding.emptyDataLabel.visibility = View.GONE
                        binding.lotifyAnimation.visibility = View.GONE

                        val placeFields = listOf(Place.Field.ID, Place.Field.NAME, Place.Field.ADDRESS, Place.Field.TYPES)

                        // Use the builder to create a SearchByTextRequest object
                        val searchByTextRequest = SearchByTextRequest.builder(query, placeFields)
                            .setMaxResultCount(15)
                            .build()

                        // Call PlacesClient.searchByText() to perform the search
                        placesClient.searchByText(searchByTextRequest)
                            .addOnSuccessListener { response ->
                                val places: List<Place> = response.places
                                textSearchAdapter.updatePlaces(places)
                                Log.e("SearchActivity", "Found ${places.size} places.")
                                places.forEach { place ->
                                    Log.e("SearchActivity", "Place ID: ${place.id}, Name: ${place.name}")
                                }
                            }
                            .addOnFailureListener { exception ->
                                Log.e("SearchActivity", "Error retrieving places", exception)
                            }
                            .addOnCompleteListener {
                                // Hide loading animation
                                binding.lottieAnimation.visibility = View.GONE
                            }
                    } else {
                        // No query text, show recent searches and hide text search RecyclerView
                        binding.textSearchRecyclerView.visibility = View.GONE
                        binding.recentSearchRecyclerView.visibility = View.VISIBLE
                        binding.recentSearchesLabel.visibility = View.VISIBLE

                        // Reload recent searches to ensure they are visible
                        loadRecentSearches()
                        textSearchAdapter.updatePlaces(emptyList())
                        updateViews() // Update view visibility when no query text

                        // Hide loading animation
                        binding.lottieAnimation.visibility = View.GONE
                    }
                }
                return true
            }
        })

        binding.searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // When search view loses focus, show recent searches
                if (binding.searchView.query.isEmpty()) {
                    binding.textSearchRecyclerView.visibility = View.GONE
                    binding.recentSearchRecyclerView.visibility = View.VISIBLE
                    binding.recentSearchesLabel.visibility = View.VISIBLE

                    // Reload recent searches to ensure they are visible
                    loadRecentSearches()
                    updateViews()
                }
            }
        }
    }


    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }
}


