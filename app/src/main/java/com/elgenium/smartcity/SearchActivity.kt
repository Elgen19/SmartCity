package com.elgenium.smartcity

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.SearchView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivitySearchBinding
import com.elgenium.smartcity.helpers.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.models.Search
import com.elgenium.smartcity.recyclerview_adapter.AutocompleteAdapter
import com.elgenium.smartcity.recyclerview_adapter.RecentSearchAdapter
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompletePrediction
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SearchActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySearchBinding
    private lateinit var placesClient: PlacesClient
    private lateinit var autocompleteAdapter: AutocompleteAdapter
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var locationBias: RectangularBounds? = null
    private lateinit var database: DatabaseReference
    private lateinit var auth: FirebaseAuth
    private lateinit var recentSearchAdapter: RecentSearchAdapter
    private val recentSearches = mutableListOf<Search>()

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

        // get google maps API key in the secrets.properties
        val apiKey = BuildConfig.MAPS_API_KEY

        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        }
        placesClient = Places.createClient(this)

        // Initialize FusedLocationProviderClient to get the current location
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Check and request location permissions
        if (checkLocationPermission()) {
            getCurrentLocation()
        }

        // Initialize the RecyclerView with a LinearLayoutManager
        val layoutManager = LinearLayoutManager(this)
        binding.autocompleteRecyclerView.layoutManager = layoutManager

        // Create a custom drawable with your desired color
        val dividerDrawable = ColorDrawable(ContextCompat.getColor(this, R.color.dark_gray))

        // Create the DividerItemDecoration and set the drawable
        val dividerItemDecoration = DividerItemDecoration(
            binding.autocompleteRecyclerView.context,
            layoutManager.orientation
        )
        dividerItemDecoration.setDrawable(dividerDrawable)

        // Add the custom divider to the RecyclerView
        binding.autocompleteRecyclerView.addItemDecoration(dividerItemDecoration)

        autocompleteAdapter = AutocompleteAdapter(emptyList()) { selectedPrediction ->
            // Handle item click
            saveRecentSearch(selectedPrediction.getPrimaryText(null).toString())

            val intent = Intent(this, PlacesActivity::class.java).apply {
                putExtra("PLACE_ID", selectedPrediction.placeId)
            }
            startActivity(intent)
            finish()
        }
        binding.autocompleteRecyclerView.adapter = autocompleteAdapter

        setupAutocomplete()
        searchViewAppearance()
        setupCategoryButtonListeners()


        recentSearchAdapter = RecentSearchAdapter(recentSearches) { recentSearch ->
            // Get the place ID using the place name
            fetchPlaceId(recentSearch.location) { placeId ->
                placeId?.let {
                    val intent = Intent(this, PlacesActivity::class.java).apply {
                        putExtra("PLACE_ID", it)
                    }
                    startActivity(intent)
                    finish()
                }
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
    }

    private fun fetchPlaceId(placeName: String, callback: (String?) -> Unit) {
        val request = FindAutocompletePredictionsRequest.builder()
            .setQuery(placeName)
            .build()

        placesClient.findAutocompletePredictions(request)
            .addOnSuccessListener { response ->
                val placeId = response.autocompletePredictions.firstOrNull()?.placeId
                callback(placeId)
            }
            .addOnFailureListener { exception ->
                Log.e("SearchActivity", "Error fetching place ID", exception)
                callback(null)
            }
    }

    private fun setupCategoryButtonListeners() {
        binding.btnHotels.setOnClickListener { launchCategoryIntent("hotel") }
        binding.btnBars.setOnClickListener { launchCategoryIntent("bar") }
        binding.btnRestaurants.setOnClickListener { launchCategoryIntent("restaurant") }
        binding.btnCoffee.setOnClickListener { launchCategoryIntent("coffee") }
        binding.btnGroceries.setOnClickListener { launchCategoryIntent("grocery") }
        binding.btnGas.setOnClickListener { launchCategoryIntent("gas") }
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

    private fun saveRecentSearch(query: String) {
        val timestamp = SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a", Locale.getDefault()).format(Date())

        // Create a new Search object with the updated timestamp
        val search = Search(query, timestamp)

        // Query the database to check if the search already exists
        database.orderByChild("location").equalTo(query).get().addOnSuccessListener { dataSnapshot ->
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
        if (recentSearches.isEmpty() && autocompleteAdapter.itemCount == 0) {
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
        val debounceDelay: Long = 300 // Adjust this value as needed
        var searchJob: Job? = null

        binding.searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener,
            androidx.appcompat.widget.SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                // Handle text submission if needed
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                searchJob?.cancel()
                searchJob = CoroutineScope(Dispatchers.Main).launch {
                    delay(debounceDelay) // Wait for debounce delay
                    val query = newText.orEmpty()
                    if (query.isNotEmpty()) {
                        // Show autocomplete RecyclerView and hide recent searches
                        binding.autocompleteRecyclerView.visibility = View.VISIBLE
                        binding.recentSearchRecyclerView.visibility = View.GONE

                        val requestBuilder = FindAutocompletePredictionsRequest.builder()
                            .setQuery(query)

                        locationBias?.let {
                            requestBuilder.setLocationBias(it)
                        }

                        val request = requestBuilder.build()

                        placesClient.findAutocompletePredictions(request)
                            .addOnSuccessListener { response ->
                                val predictions: List<AutocompletePrediction> = response.autocompletePredictions
                                autocompleteAdapter.updatePredictions(predictions)
                                binding.recentSearchesLabel.visibility = View.GONE
                                binding.recentSearchRecyclerView.visibility = View.GONE
                                updateViews()
                            }
                            .addOnFailureListener { exception ->
                                Log.e("SearchActivity", "Error retrieving predictions", exception)
                            }
                    } else {
                        // No query text, show recent searches and hide autocomplete RecyclerView
                        binding.autocompleteRecyclerView.visibility = View.GONE
                        binding.recentSearchRecyclerView.visibility = View.VISIBLE
                        binding.recentSearchesLabel.visibility = View.VISIBLE

                        // Reload recent searches to ensure they are visible
                        loadRecentSearches()
                        autocompleteAdapter.updatePredictions(emptyList())
                        updateViews() // Update view visibility when no query text
                    }
                }
                return true
            }
        })

        binding.searchView.setOnQueryTextFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                // When search view loses focus, show recent searches
                if (binding.searchView.query.isEmpty()) {
                    binding.autocompleteRecyclerView.visibility = View.GONE
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
