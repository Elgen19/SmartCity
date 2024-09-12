package com.elgenium.smartcity

import android.app.ActivityOptions
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.elgenium.smartcity.databinding.ActivityFavoritesBinding
import com.elgenium.smartcity.models.SavedPlace
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.elgenium.smartcity.viewpager_adapter.FavoritesViewPagerAdapter
import com.elgenium.smartcity.viewpager_adapter.PhotoPagerAdapter
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.PhotoMetadata
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPhotoRequest
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class FavoritesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityFavoritesBinding
    private lateinit var database: DatabaseReference
    private lateinit var savedPlaces: MutableList<SavedPlace>
    private lateinit var placesClient: PlacesClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Inflate the layout using view binding
        binding = ActivityFavoritesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d("FavoritesActivity", "Activity created")

        // Set the color of the navigation bar making it more personalized
        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.secondary_color)

        // Get Google Maps API key in the secrets.properties
        val apiKey = BuildConfig.MAPS_API_KEY

        if (!Places.isInitialized()) {
            Log.d("FavoritesActivity", "Initializing Places API")
            Places.initializeWithNewPlacesApiEnabled(applicationContext, apiKey)
        }
        placesClient = Places.createClient(this)

        savedPlaces = mutableListOf()

        // Initialize Firebase Database reference
        database = FirebaseDatabase.getInstance().getReference("Users")
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            // Retrieve saved places from Firebase
            database.child(userId).child("saved_places").addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    savedPlaces.clear()
                    for (placeSnapshot in snapshot.children) {
                        val place = placeSnapshot.getValue(SavedPlace::class.java)
                        if (place != null) {
                            Log.d("FavoritesActivity", "Saved place loaded: ${place.name}")
                            savedPlaces.add(place)
                        }
                    }
                    // Update adapter with new data
                    (binding.viewPager.adapter as FavoritesViewPagerAdapter).updateSavedPlaces(savedPlaces)
                    Log.d("FavoritesActivity", "Saved places updated. Total count: ${savedPlaces.size}")
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FavoritesActivity", "Failed to load saved places", error.toException())
                    Toast.makeText(this@FavoritesActivity, "Failed to load saved places", Toast.LENGTH_SHORT).show()
                }
            })
        } else {
            Log.e("FavoritesActivity", "User ID is null")
        }

        // Set up the ViewPager with the sections adapter
        val adapter = FavoritesViewPagerAdapter(savedPlaces,
            onItemClick = { place ->
                Log.d("FavoritesActivity", "Place clicked: ${place.name}")
                showPlaceDetails(place)
            },
            onItemLongClick = { place ->
                Log.d("FavoritesActivity", "Place long-clicked: ${place.name}")
                showBottomSheetForActions(place)
            }
        )


        binding.viewPager.adapter = adapter

        // Link the TabLayout with the ViewPager2
        TabLayoutMediator(binding.tabLayout, binding.viewPager) { tab, _ ->
            tab.text = "Saved Places" // Only one tab for now
        }.attach()

        setupActivityNavigation()
    }

    private fun getPhotoMetadatas(placeId: String, bottomSheetView: View) {
        Log.d("FavoritesActivity", "Fetching photo metadata for place ID: $placeId")

        val placePhotoMetadatas = listOf(
            Place.Field.PHOTO_METADATAS
        )

        // Build the request to fetch the place details
        val request = FetchPlaceRequest.builder(placeId, placePhotoMetadatas).build()

        // Fetch the place details using the Places API
        placesClient.fetchPlace(request)
            .addOnSuccessListener { response ->
                Log.d("FavoritesActivity", "Place details fetched successfully")
                val placeDetails = response.place
                getAndLoadPhotoMetadatasFromPlace(placeDetails, bottomSheetView)
            }
            .addOnFailureListener { exception ->
                Log.e("FavoritesActivity", "Error fetching place details for bottom sheet", exception)
            }
    }

    private fun setupActivityNavigation() {
        binding.bottomNavigation.selectedItemId = R.id.navigation_favorites
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    val intent = Intent(this, DashboardActivity::class.java)
                    val options = ActivityOptions.makeCustomAnimation(
                        this,
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    startActivity(intent, options.toBundle())
                    finish()
                    true
                }
                R.id.navigation_places -> {
                    val intent = Intent(this, PlacesActivity::class.java)
                    val options = ActivityOptions.makeCustomAnimation(
                        this,
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    startActivity(intent, options.toBundle())
                    finish()
                    true
                }
                R.id.navigation_favorites -> {
                    true
                }
                R.id.navigation_events -> {
                    true
                }
                R.id.navigation_settings -> {
                    val intent = Intent(this, SettingsActivity::class.java)
                    val options = ActivityOptions.makeCustomAnimation(
                        this,
                        R.anim.fade_in,
                        R.anim.fade_out
                    )
                    startActivity(intent, options.toBundle())
                    finish()
                    true
                }
                else -> false
            }
        }
    }

    private fun showBottomSheetForActions(place: SavedPlace) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_place_actions, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        val addAPlaceOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.addAPlaceOptionLayout)
        val shareOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.shareOptionLayout)
        val removeOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.removeOptionLayout)
        val reportOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.reportOptionLayout)


        addAPlaceOptionLayout.setOnClickListener {
            // Navigate to search activity
            val intent = Intent(this, SearchActivity::class.java)
            val options = ActivityOptions.makeCustomAnimation(
                this,
                R.anim.fade_in,
                R.anim.fade_out
            )
            startActivity(intent, options.toBundle())
            finish()
            bottomSheetDialog.dismiss()
        }

        shareOptionLayout.setOnClickListener {
            val shareText = """
        📍 Check out this place:
        
        Name: ${place.name}
        Address: ${place.address}
        Phone: ${place.phoneNumber ?: "No phone number available"}
        Rating: ${place.rating ?: "No rating available."}
    """.trimIndent()

            val shareIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, shareText)
                type = "text/plain"
            }
            startActivity(Intent.createChooser(shareIntent, "Share via"))
        }

        removeOptionLayout.setOnClickListener {
            // Handle deleting the place
            deleteSavedPlace(place)
            bottomSheetDialog.dismiss()
        }

        reportOptionLayout.setOnClickListener{
            // navigate to ReportEventActivity
            ActivityNavigationUtils.navigateToActivity(this, ReportEventActivity::class.java, false)
            bottomSheetDialog.dismiss()
        }

        bottomSheetDialog.show()
    }

    private fun deleteSavedPlace(place: SavedPlace) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId != null) {
            // Find the correct key in the Firebase database
            database.child(userId).child("saved_places")
                .orderByChild("id")
                .equalTo(place.id)
                .addListenerForSingleValueEvent(object : ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        for (placeSnapshot in snapshot.children) {
                            // Remove the matching place
                            placeSnapshot.ref.removeValue()
                                .addOnSuccessListener {
                                    Toast.makeText(this@FavoritesActivity, "Place deleted", Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this@FavoritesActivity, "Failed to delete place", Toast.LENGTH_SHORT).show()
                                }
                        }
                    }

                    override fun onCancelled(error: DatabaseError) {
                        Toast.makeText(this@FavoritesActivity, "Failed to delete place", Toast.LENGTH_SHORT).show()
                    }
                })
        } else {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showPlaceDetails(place: SavedPlace) {
        Log.d("FavoritesActivity", "Showing details for place: $place")
        val bottomSheetDialog = BottomSheetDialog(this)
        val bottomSheetView = layoutInflater.inflate(R.layout.bottom_sheet_favorite_place, null)
        bottomSheetDialog.setContentView(bottomSheetView)

        val placeName: TextView = bottomSheetView.findViewById(R.id.placeName)
        val placeAddress: TextView = bottomSheetView.findViewById(R.id.placeAddress)
        val openStatus: TextView = bottomSheetView.findViewById(R.id.openStatus)
        val placeDistance: TextView = bottomSheetView.findViewById(R.id.placeDistance)
        val placePhone: TextView = bottomSheetView.findViewById(R.id.placePhone)
        val placeWebsite: TextView = bottomSheetView.findViewById(R.id.placeWebsite)
        val placeRating: TextView = bottomSheetView.findViewById(R.id.placeRating)
        val placeHoursDays: TextView = bottomSheetView.findViewById(R.id.placeHoursDays)
        val placeHoursTime: TextView = bottomSheetView.findViewById(R.id.placeHoursTime)
        val btnClose: ImageButton = bottomSheetView.findViewById(R.id.closeButton)


        placeName.text = place.name
        placeAddress.text = place.address
        openStatus.text = place.openingStatus
        placeDistance.text = place.distance
        placePhone.text = place.phoneNumber ?: "No phone number available"
        placeWebsite.text = place.websiteUri ?: "No website available"
        placeRating.text = place.rating ?: "N/A"


        // gets the photo metadata for display in viewpager
        place.id?.let { getPhotoMetadatas(it, bottomSheetView) }

        // close button listener
        btnClose.setOnClickListener {
            // Dismiss the bottom sheet dialog
            bottomSheetDialog.dismiss()
        }

        // Update the UI based on the open/closed status.
        if (openStatus.equals("Open")) {
            openStatus.text = getString(R.string.open_status)
            openStatus.setBackgroundResource(R.drawable.open_pill_background)
        } else {
            openStatus.text = getString(R.string.closed)
            openStatus.setBackgroundResource(R.drawable.closed_pill_background)
        }

        // Check opening hours availability and determine if the indicator be visible or not
        if (placeHoursDays.text.isNullOrEmpty() || placeHoursTime.text.isNullOrEmpty()){
            // Hide the open status if no opening hours information is available.
            openStatus.visibility = View.GONE
            // Adjust margin for placeDistance TextView.
            val layoutParams = placeDistance.layoutParams as ViewGroup.MarginLayoutParams
            layoutParams.marginStart = 0
            placeDistance.layoutParams = layoutParams
        } else {
            openStatus.visibility = View.VISIBLE
        }

        bottomSheetDialog.show()
    }

    private fun loadPhotosIntoViewPager(photoMetadatas: List<PhotoMetadata>, viewPager: ViewPager2) {
        Log.d("FavoritesActivity", "Loading ${photoMetadatas.size} photos into ViewPager")
        val photoBitmaps = mutableListOf<Bitmap>()

        // Fetch each photo
        photoMetadatas.forEach { photoMetadata ->
            Log.d("FavoritesActivity", "Fetching photo for metadata: $photoMetadata")
            val photoRequest = FetchPhotoRequest.builder(photoMetadata)
                .setMaxWidth(400)
                .setMaxHeight(400)
                .build()
            placesClient.fetchPhoto(photoRequest)
                .addOnSuccessListener { response ->
                    Log.d("FavoritesActivity", "Photo fetched successfully")
                    val photoBitmap = response.bitmap
                    photoBitmap.let {
                        photoBitmaps.add(it)
                        if (photoBitmaps.size == photoMetadatas.size) {
                            Log.d("FavoritesActivity", "All photos fetched, setting adapter")
                            viewPager.adapter = PhotoPagerAdapter(photoBitmaps)
                        }
                    }
                }
                .addOnFailureListener { exception ->
                    Log.e("FavoritesActivity", "Error fetching photo", exception)
                }
        }
    }

    private fun getAndLoadPhotoMetadatasFromPlace(placeDetails: Place, bottomSheetView: View) {
        Log.d("FavoritesActivity", "Extracting photo metadata from place details")
        val photoMetadatas = placeDetails.photoMetadatas
        if (photoMetadatas != null && photoMetadatas.isNotEmpty()) {
            val viewPager = bottomSheetView.findViewById<ViewPager2>(R.id.viewPager)
            viewPager.visibility = View.VISIBLE
            loadPhotosIntoViewPager(photoMetadatas, viewPager)
        } else {
            Log.d("FavoritesActivity", "No photo metadata available")
            val viewPager = bottomSheetView.findViewById<ViewPager2>(R.id.viewPager)
            viewPager.visibility = View.GONE
        }
    }
}
