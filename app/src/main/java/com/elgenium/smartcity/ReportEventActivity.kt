package com.elgenium.smartcity

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.GridLayoutManager
import com.bumptech.glide.Glide
import com.elgenium.smartcity.databinding.ActivityReportEventBinding
import com.elgenium.smartcity.models.Event
import com.elgenium.smartcity.models.ReportImages
import com.elgenium.smartcity.recyclerview_adapter.ImageAdapter
import com.elgenium.smartcity.singletons.LayoutStateManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

class ReportEventActivity : AppCompatActivity() {

    private lateinit var binding: ActivityReportEventBinding
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var placeName: String
    private lateinit var placeAddress: String
    private var currentPhotoPath: String? = null
    private val imageList = mutableListOf<ReportImages>()
    private lateinit var placeLatLng: String
    private lateinit var placeId: String
    private var fromEventsActivityChecker: String = "NO CHECKER FOUND"
    private var selectedButtonInfo: String = ""
    private  var isAdding = true

    private val searchActivityLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data = result.data
                val placeName = data?.getStringExtra("PLACE_NAME")
                val placeAddress = data?.getStringExtra("PLACE_ADDRESS")
                placeLatLng = data?.getStringExtra("PLACE_LATLNG").toString()
                placeId = data?.getStringExtra("PLACE_ID").toString()

                binding.tvLocation.visibility = View.VISIBLE
                binding.tvAdditionalInfo.visibility = View.VISIBLE
                Log.d(
                    "ReportEventActivity",
                    "Place name from intent: $placeName and $placeAddress and $placeLatLng"
                )
                binding.tvLocation.text = placeName
                binding.tvAdditionalInfo.text = placeAddress

                Log.e("ReportEventActivity", "PLACE ADDRESS: $placeAddress")
                Log.e("ReportEventActivity", "PLACE NAME: $placeName")
                Log.e("ReportEventActivity", "PLACE LATLNG: $placeLatLng")
                Log.e("ReportEventActivity", "PLACE id: $placeId")


            } else {
                Log.d("ReportEventActivity", "SearchActivity result not OK: ${result.resultCode}")
            }
        }

    private val pickPhotosFromGallery =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri>? ->
            Log.d("ReportEventActivity", "Photos picked from gallery: $uris")
            uris?.let {
                imageList.addAll(it.map { uri -> ReportImages(uri) })
                imageAdapter.notifyDataSetChanged()
            }
        }

    private val takePhotoFromCamera =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                Log.d("ReportEventActivity", "Photo taken successfully: $currentPhotoPath")
                currentPhotoPath?.let {
                    val uri = Uri.fromFile(File(it))
                    imageList.add(ReportImages(uri))
                    imageAdapter.notifyDataSetChanged()
                }
            } else {
                Log.d("ReportEventActivity", "Photo taking failed")
            }
        }

    companion object {
        private const val REQUEST_CAMERA_PERMISSION = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReportEventBinding.inflate(layoutInflater)
        setContentView(binding.root)

        placeName = intent.getStringExtra("PLACE_NAME") ?: ""
        placeAddress = intent.getStringExtra("PLACE_ADDRESS") ?: ""
        placeLatLng = intent.getStringExtra("PLACE_LATLNG") ?: ""
        placeId = intent.getStringExtra("PLACE_ID") ?: "NO PLACE ID"


        Log.d("ReportEventActivity", "place name: $placeName")
        Log.d("ReportEventActivity", "place address: $placeAddress")
        Log.d("ReportEventActivity", "place latlng: $placeLatLng")
        Log.d("ReportEventActivity", "place id: $placeId")

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        if (intent.hasExtra("FROM_EVENTS_ACTIVITY")) {
            val checkerValue = intent.getStringExtra("FROM_EVENTS_ACTIVITY")
            fromEventsActivityChecker = checkerValue ?: "NO CHECKER FOUND"
            isAdding = false
        } else {
            isAdding = true
        }


        Log.e("EventDetails", "Checker is null")
        imageAdapter = ImageAdapter(imageList) { position ->
            // Show bottom sheet dialog for the image at this position
            showBottomSheetImageOptionsDialog(position)
        }
        binding.recyclerViewImages.adapter = imageAdapter
        initListeners()


        Log.e("ReportEventActivity", "IS ADDING: $isAdding")


        if (!isAdding) {
            // Load event by checker
            loadEventByChecker(fromEventsActivityChecker) { event ->
                if (event != null) {
                    // Populate the fields with event data
                    populateEventFields(event, fromEventsActivityChecker)
                } else {
                    // Handle the case where the event is not found
                    Log.e(
                        "EventDetails",
                        "Event not found with checker: $fromEventsActivityChecker"
                    )
                }
            }
        }

    }



    private fun populateEventFields(event: Event, checker: String) {
        // Find your views here
        placeName = event.location.toString()
        placeAddress = event.additionalInfo.toString()
        placeId = event.placeId.toString()
        placeLatLng = event.placeLatLng.toString()
        binding.tvLocation.text = event.location.toString()
        Log.e("PopulateEventFields", "Location: ${event.location}") // Log location

        binding.tvAdditionalInfo.visibility = View.VISIBLE
        binding.tvAdditionalInfo.text = event.additionalInfo.toString()
        Log.e(
            "PopulateEventFields",
            "Additional Info: ${event.additionalInfo}"
        ) // Log additional info

        binding.etEventName.setText(event.eventName)
        Log.e("PopulateEventFields", "Event Name: ${event.eventName}") // Log event name

        event.eventCategory?.let {
            setActiveButtonBasedOnCategory(it)
            Log.e("PopulateEventFields", "Event Category: $it") // Log event category
        }

        binding.btnDateTimeStarted.text = event.startedDateTime
        Log.e(
            "PopulateEventFields",
            "Started DateTime: ${event.startedDateTime}"
        ) // Log started date time

        binding.btnDateTimeEnded.text = event.endedDateTime
        Log.e(
            "PopulateEventFields",
            "Ended DateTime: ${event.endedDateTime}"
        ) // Log ended date time

        binding.etEventDescription.setText(event.eventDescription)
        Log.e(
            "PopulateEventFields",
            "Event Description: ${event.eventDescription}"
        ) // Log event description

        imageAdapter = ImageAdapter(imageList) { position ->
            // Handle long click (if needed)
            showBottomSheetImageOptionsDialog(position)
        }
        binding.recyclerViewImages.adapter = imageAdapter
        binding.recyclerViewImages.layoutManager =
            GridLayoutManager(this, 3) // Set layout manager, adjust spanCount as needed

        // Load images from Firebase
        loadImagesSavedFromEvent(checker)
    }


    private fun showBottomSheetImageOptionsDialog(position: Int) {
        // Create the bottom sheet dialog
        val bottomSheetDialog = BottomSheetDialog(this)

        // Inflate the layout for the bottom sheet dialog
        val bottomSheetView = LayoutInflater.from(this).inflate(
            R.layout.bottom_sheet_image_options,  // Your layout file
            binding.root,
            false
        )

        // Set the content of the dialog to the inflated layout
        bottomSheetDialog.setContentView(bottomSheetView)

        // Find the views for the View and Delete options
        val viewOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.viewOptionLayout)
        val deleteOptionLayout: LinearLayout = bottomSheetView.findViewById(R.id.deleteOptionLayout)

        // Set click listener for the "View Image" option
        viewOptionLayout.setOnClickListener {
            // Action to view the image (you can open a new activity or dialog to show the image)
            viewImageFromRecyclerViewAlertDialog(position)
            bottomSheetDialog.dismiss()
        }

        // Set click listener for the "Delete Image" option
        deleteOptionLayout.setOnClickListener {
            // Action to delete the image (e.g., remove it from the list and notify the adapter)
            // Notify the adapter about the item removal
            imageAdapter.removeImage(position)

            bottomSheetDialog.dismiss()
        }

        // Show the bottom sheet dialog
        bottomSheetDialog.show()
    }

    private fun loadImagesSavedFromEvent(checker: String) {
        val databaseReference =
            FirebaseDatabase.getInstance().getReference("Events").orderByChild("checker")
                .equalTo(checker)

        databaseReference.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Clear the existing list before adding new data (optional)
                imageList.clear()

                for (eventSnapshot in snapshot.children) {
                    val imagesSnapshot =
                        eventSnapshot.child("images") // Access the 'images' child node
                    for (imageSnapshot in imagesSnapshot.children) {
                        val imageUrl =
                            imageSnapshot.getValue(String::class.java) // Assuming the value is a String
                        if (imageUrl != null) {
                            // Add each URL to the image list as a ReportImages instance
                            imageList.add(ReportImages(url = imageUrl)) // Use url property
                            Log.e(
                                "PopulateEventFields",
                                "Fetched Image URL: $imageUrl"
                            ) // Log each fetched URL
                        } else {
                            Log.e(
                                "PopulateEventFields",
                                "Fetched image URL is null for key: ${imageSnapshot.key}"
                            ) // Log if URL is null
                        }
                    }
                }

                // Notify the adapter that the data has changed
                imageAdapter.notifyDataSetChanged()
                Log.e(
                    "PopulateEventFields",
                    "Total images loaded: ${imageList.size}"
                ) // Log the total number of images loaded
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle possible errors.
                Log.e(
                    "PopulateEventFields",
                    "Error fetching images: ${error.message}"
                ) // Log the error message
            }
        })
    }

    private fun showOthersAlertDialog(): String {
        // Inflate the dialog layout
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_others, null)

        // Create the AlertDialog
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Access the views in the dialog layout
        val etEventDescription = dialogView.findViewById<EditText>(R.id.etEventDescription)
        val btnCancel =
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnCancel)
        val btnConfirm =
            dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnConfirm)

        // Handle the Cancel button click
        btnCancel.setOnClickListener {
            alertDialog.dismiss()  // Close the dialog
        }

        // Handle the Confirm button click
        btnConfirm.setOnClickListener {
            val description = etEventDescription.text.toString().trim()
            Log.d("ReportEventActivity", "Description Value: $description")
            if (description.isNotEmpty()) {
                binding.tvOthers.visibility = View.VISIBLE
                binding.tvOthers.text = getString(R.string.others, description)
                alertDialog.dismiss()
            } else {
                etEventDescription.error = "Please enter the event description"
            }
        }

        // Show the dialog
        alertDialog.show()

        return binding.tvOthers.text.toString()
    }

    private fun loadEventByChecker(checker: String, onEventLoaded: (Event?) -> Unit) {
        val eventsRef = FirebaseDatabase.getInstance().getReference("Events")
        eventsRef.orderByChild("checker").equalTo(checker).addListenerForSingleValueEvent(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    for (eventSnapshot in snapshot.children) {
                        val event = eventSnapshot.getValue(Event::class.java)
                        onEventLoaded(event) // Pass the loaded event to the callback
                        break // Exit after finding the first match
                    }
                } else {
                    onEventLoaded(null) // Event not found
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("LoadEvent", "Failed to load event: ${error.message}")
                onEventLoaded(null) // Handle the failure case by passing null
            }
        })
    }

    private fun viewImageFromRecyclerViewAlertDialog(position: Int) {
        val imageUri = imageList[position].uri
        val imageUrl = imageList[position].url

        // Inflate the custom dialog layout
        val dialogView =
            LayoutInflater.from(this).inflate(R.layout.dialog_view_image, binding.root, false)

        // Find views in the inflated layout
        val imageView = dialogView.findViewById<ImageView>(R.id.imageDialogView)
        val closeButton = dialogView.findViewById<Button>(R.id.closeButton)

        // Load the image based on whether it's a URI or a URL
        if (imageUri != null) {
            // If URI is available (local image), load it using setImageURI
            imageView.setImageURI(imageUri)
        } else if (imageUrl != null) {
            // If URL is available (remote image), load it using Glide
            Glide.with(this)
                .load(imageUrl)
                .placeholder(R.drawable.placeholder_viewpager_photos) // Optional placeholder while loading
                .into(imageView)
        } else {
            // If both are null, show a fallback image or message
            imageView.setImageResource(R.drawable.placeholder_viewpager_photos) // Fallback image
        }

        // Create and show the AlertDialog
        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        // Set the click listener for the close button
        closeButton.setOnClickListener {
            alertDialog.dismiss()  // Close the dialog
        }

        // Show the dialog
        alertDialog.show()
    }


    private fun showCustomPhotoPickerDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_photo_picker_launcher, null)

        val alertDialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        val galleryOptionLayout: LinearLayout = dialogView.findViewById(R.id.galleryOptionLayout)
        val cameraOptionLayout: LinearLayout = dialogView.findViewById(R.id.cameraOptionLayout)
        val closeButton: ImageButton = dialogView.findViewById(R.id.closeButton)

        galleryOptionLayout.setOnClickListener {
            alertDialog.dismiss()
            pickPhotosFromGallery.launch("image/*")
        }

        cameraOptionLayout.setOnClickListener {
            alertDialog.dismiss()
            dispatchTakePictureIntent()
        }

        closeButton.setOnClickListener {
            alertDialog.dismiss()
        }

        alertDialog.show()
    }

    private fun createImageFile(): File {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val storageDir: File = getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        val file = File.createTempFile(
            "JPEG_${timeStamp}_", /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
        currentPhotoPath = file.absolutePath
        Log.d("ReportEventActivity", "Image file created: $currentPhotoPath")
        return file
    }

    private fun dispatchTakePictureIntent() {
        Log.d("ReportEventActivity", "Dispatching take picture intent")
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED
        ) {

            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CAMERA_PERMISSION
            )
            Log.d(
                "ReportEventActivity",
                "Camera or storage permission not granted, requesting permissions"
            )
        }

        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Log.e("ReportEventActivity", "Error creating image file", ex)
            null
        }

        photoFile?.let {
            val photoURI: Uri = FileProvider.getUriForFile(
                this,
                "com.elgenium.smartcity.fileprovider",
                it
            )
            Log.d("ReportEventActivity", "Launching camera with URI: $photoURI")
            takePhotoFromCamera.launch(photoURI)
        }
    }

    private fun setActiveButtonBasedOnCategory(eventCategory: String) {
        val buttons = listOf(
            binding.btnFestival, binding.btnOutdoor, binding.btnSales, binding.btnWorkShop,
            binding.btnConcert, binding.btnOthers
        )

        // Create a map of button texts to user preferences
        val buttonToPreferenceMap = mapOf(
            "Festival" to "Festivals & Celebrations",
            "Outdoor Events" to "Outdoor & Adventure Events",
            "Sales" to "Sales & Promotions",
            "Workshop" to "Workshops & Seminars",
            "Concert" to "Concerts & Live Performances",
            "Others" to "Others"
        )

        // Loop through buttons and find the one that matches the eventCategory
        buttons.forEach { button ->
            val buttonCategory = buttonToPreferenceMap[button.text.toString()]

            // Reset all button styles before applying the active style
            button.setBackgroundColor(getColor(R.color.primary_color))  // Default background color
            button.setTextColor(getColor(R.color.secondary_color))  // Default text color
            button.iconTint =
                ColorStateList.valueOf(getColor(R.color.secondary_color))  // Default icon color
            button.strokeColor =
                ColorStateList.valueOf(getColor(R.color.secondary_color))  // Default stroke color

            // Apply active style to the matching button
            if (buttonCategory == eventCategory) {
                button.setBackgroundColor(getColor(R.color.brand_color))  // Active background color
                button.setTextColor(getColor(R.color.primary_color))  // Active text color
                button.iconTint =
                    ColorStateList.valueOf(getColor(R.color.primary_color))  // Active icon color
                button.strokeColor =
                    ColorStateList.valueOf(getColor(R.color.primary_color))  // Active stroke color

                selectedButtonInfo = buttonCategory
                // Log for debugging
                Log.d("ReportEventActivity", "Active button set for category: $eventCategory")
            }
        }

    }

    private fun initListeners() {
        // Determine if placeName and placeAddress from intent is not empty
        // this only means that the user presses an option report an event of a specific place
        Log.e("ReportEventActivity", "FROM EVENTS ACTIVITY = $fromEventsActivityChecker")
        Log.e("ReportEventActivity", "place name: $placeName")
        Log.e("ReportEventActivity", "place address: $placeAddress")

        Log.e("ReportEventActivity", "INIT LISTERNERS IS ADDING: $isAdding")


        if (isAdding) {
            if (!placeName.isNullOrEmpty() && !placeAddress.isNullOrEmpty()) {
                binding.tvLocation.text = placeName
                binding.tvAdditionalInfo.text = placeAddress
                binding.tvAdditionalInfo.visibility = View.VISIBLE
                binding.btnGetLocation.visibility = View.GONE
                Log.e("ReportEventActivity", "PLACE NAME IS NOT NULL")
                Log.e("ReportEventActivity", "place name: $placeName")
                Log.e("ReportEventActivity", "place address: $placeAddress")


            }
            // this only means that not a specific place is reported
            // this can happen in the Events activity
            else {
                Log.e("ReportEventActivity", "PLACE NAME IS NULL")
                binding.btnGetLocation.visibility = View.VISIBLE
                getCurrentLocation()
                binding.btnGetLocation.setOnClickListener {
                    Log.e("ReportEventActivity", "Get Location button clicked")
                    val intent = Intent(this, SearchActivity::class.java)
                    intent.putExtra("FROM_REPORT_EVENTS_ACTIVITY", true)
                    searchActivityLauncher.launch(intent)
                }
            }
        }

        binding.btnBack.setOnClickListener {
            finish()
        }

        val buttons = listOf(
            binding.btnFestival, binding.btnOutdoor, binding.btnSales, binding.btnWorkShop,
            binding.btnConcert, binding.btnOthers
        )

        // Create a map of button texts to user preferences
        val buttonToPreferenceMap = mapOf(
            "Festival" to "Festivals & Celebrations",
            "Outdoor Events" to "Outdoor & Adventure Events",
            "Sales" to "Sales & Promotions",
            "Workshop" to "Workshops & Seminars",
            "Concert" to "Concerts & Live Performances",
            "Others" to "Others"
        )


        buttons.forEach { button ->
            button.setOnClickListener {
                Log.d("ReportEventActivity", "Button clicked: ${button.text}")
                buttons.forEach {
                    it.setBackgroundColor(getColor(R.color.primary_color))
                    it.setTextColor(getColor(R.color.secondary_color))
                    it.iconTint = ColorStateList.valueOf(getColor(R.color.secondary_color))
                    it.strokeColor = ColorStateList.valueOf(getColor(R.color.secondary_color))
                }

                button.setBackgroundColor(getColor(R.color.brand_color))
                button.setTextColor(getColor(R.color.primary_color))
                button.iconTint = ColorStateList.valueOf(getColor(R.color.primary_color))
                button.strokeColor = ColorStateList.valueOf(getColor(R.color.primary_color))


                // Get the corresponding user preference for the selected button
                selectedButtonInfo = buttonToPreferenceMap[button.text.toString()] ?: ""
                Log.d("ReportEventActivity", "Selected Preference: $selectedButtonInfo")

                if (button == binding.btnOthers) {
                    showOthersAlertDialog()
                }

                // Log selected button info for debugging
                Log.d("ReportEventActivity", "Selected button info: $selectedButtonInfo")
            }
        }

        binding.btnDateTimeStarted.setOnClickListener {
            Log.d("ReportEventActivity", "DateTimeStarted button clicked")
            showDateTimePicker { dateTime ->
                binding.btnDateTimeStarted.text = dateTime
            }
        }

        binding.btnDateTimeEnded.setOnClickListener {
            Log.d("ReportEventActivity", "DateTimeEnded button clicked")
            showDateTimePicker { dateTime ->
                binding.btnDateTimeEnded.text = dateTime
            }
        }

        binding.btnAddAttachment.setOnClickListener {
            showCustomPhotoPickerDialog()
        }

        binding.btnSubmit.setOnClickListener {
            // Collect values
            val location = placeName
            val additionalInfo = placeAddress
            val othersDetail = binding.tvOthers.text.toString()
            val eventName = binding.etEventName.text.toString().trim()
            val startedDateTime = binding.btnDateTimeStarted.text.toString().trim()
            val endedDateTime = binding.btnDateTimeEnded.text.toString().trim()
            val eventDescription = binding.etEventDescription.text.toString().trim()
            val imageListSize = imageList.size

            if (selectedButtonInfo == "Others") {
                selectedButtonInfo = othersDetail
            }

            // Validate fields
            val isEventNameValid = eventName.isNotEmpty()
            val isDateTimeValid = startedDateTime != getString(R.string.date_amp_time_nstarted) &&
                    endedDateTime != getString(R.string.date_amp_time_nended)
            val isEventDescriptionValid = eventDescription.isNotEmpty()
            val isImageListValid = imageListSize > 0
            val isEventCategoryButtonTextValid = selectedButtonInfo.isNotEmpty()

            var isValid = true

            if (!isEventNameValid) {
                Toast.makeText(this, "Please add event name.", Toast.LENGTH_SHORT).show()
                isValid = false
            }

            if (!isEventCategoryButtonTextValid) {
                Toast.makeText(this, "Please add event category.", Toast.LENGTH_SHORT).show()
                isValid = false
            }

            if (!isDateTimeValid) {
                Toast.makeText(this, "Please set up date and time.", Toast.LENGTH_SHORT).show()
                isValid = false
            }
            if (!isEventDescriptionValid) {
                Toast.makeText(this, "Please add event description.", Toast.LENGTH_SHORT).show()
                isValid = false
            }
            if (!isImageListValid) {
                Toast.makeText(this, "Please add at least one image", Toast.LENGTH_SHORT).show()
                isValid = false
            }

            if (isValid) {
                val eventData = mapOf(
                    "eventName" to eventName,
                    "eventCategory" to selectedButtonInfo,
                    "startedDateTime" to startedDateTime,
                    "endedDateTime" to endedDateTime,
                    "eventDescription" to eventDescription,
                    "location" to location,
                    "additionalInfo" to additionalInfo,
                    "placeLatLng" to placeLatLng,
                    "checker" to "${eventName}_${location}",
                    "placeId" to placeId
                )

                Log.e("ReportEventActivity", "image list: $imageList")


                if (fromEventsActivityChecker != "NO CHECKER FOUND") {
                    LayoutStateManager.showLoadingLayout(
                        this,
                        "Please wait while we are updating your events"
                    )
                    updateEvent(eventData)
                } else {
                    LayoutStateManager.showLoadingLayout(
                        this,
                        "Please wait while we are saving your events"
                    )
                    uploadImagesAndSaveEvent(
                        eventName,
                        imageList.map { it.uri ?: Uri.parse("defaultUri") },
                        eventData
                    )
                }

            }
        }
    }

    private fun updateEvent(eventData: Map<String, Any?>) {
        val database = FirebaseDatabase.getInstance()
        val eventsRef = database.getReference("Events")
        val checker = fromEventsActivityChecker

        // Split images into existing URLs and new URIs
        val existingUrls = imageList.filter { it.url != null }.map { it.url!! }
        val newUris = imageList.filter { it.uri != null }.map { it.uri!! }

        // Upload new images (URIs)
        val uploadTasks = newUris.map { uri ->
            val promise = CompletableDeferred<String>()
            uploadImageToFirebaseStorage(eventData["eventName"].toString(), uri,
                onSuccess = { url -> promise.complete(url) },
                onFailure = { e -> promise.completeExceptionally(e) }
            )
            promise
        }

        // Wait for all new images to upload
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val newUrls = uploadTasks.awaitAll()

                // Combine existing and new URLs
                val allImageUrls = existingUrls + newUrls

                // Update eventData with the new image URLs
                val updatedEventData = eventData.toMutableMap().apply {
                    put("images", allImageUrls)
                }

                // Find the event by checker and update it
                eventsRef.orderByChild("checker").equalTo(checker).get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) {
                            for (eventSnapshot in snapshot.children) {
                                eventSnapshot.ref.updateChildren(updatedEventData)
                                    .addOnSuccessListener {
                                        LayoutStateManager.showSuccessLayout(
                                            this@ReportEventActivity,
                                            "Event updated successfully!",
                                            "",
                                            EventsActivity::class.java
                                        )
                                        Log.e("ReportEventActivity", "Event data updated successfully")
                                    }
                                    .addOnFailureListener { e ->
                                        Log.e("ReportEventActivity", "Failed to update event data", e)
                                    }
                            }
                        } else {
                            Log.e("ReportEventActivity", "Event not found with checker: $checker")
                        }
                    }
                    .addOnFailureListener { e ->
                        Log.e("ReportEventActivity", "Failed to retrieve event", e)
                    }

            } catch (e: Exception) {
                Log.e("ReportEventActivity", "Failed to upload new images", e)
            }
        }
    }



    private fun uploadImageToFirebaseStorage(
        eventName: String,
        uri: Uri,
        onSuccess: (String) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val storageRef = FirebaseStorage.getInstance().reference
        // Create a unique file name using the event name and UUID
        val imageRef =
            storageRef.child("event_images/${eventName}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")

        imageRef.putFile(uri)
            .addOnSuccessListener {
                imageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                    onSuccess(downloadUri.toString())
                }.addOnFailureListener { e ->
                    onFailure(e)
                }
            }
            .addOnFailureListener { e ->
                onFailure(e)
            }
    }

    private fun uploadImagesAndSaveEvent(
        eventName: String,
        images: List<Uri>,
        eventData: Map<String, Any?>
    ) {
        val uploadTasks = images.map { uri ->
            val promise = CompletableDeferred<String>()
            uploadImageToFirebaseStorage(eventName, uri,
                onSuccess = { url ->
                    promise.complete(url)
                    LayoutStateManager.showSuccessLayout(
                        this,
                        "Event saved successfully!",
                        "Thank you for your valuable contribution! You've earned 5 points for your efforts.",
                        EventsActivity::class.java
                    )
                },
                onFailure = { e ->
                    promise.completeExceptionally(e)
                    LayoutStateManager.showFailureLayout(
                        this,
                        "Something went wrong. Please check your connection or try again.",
                        "Return to Events",
                        EventsActivity::class.java
                    )
                }
            )
            promise
        }

        // Wait for all image uploads to complete
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val urls = uploadTasks.awaitAll()
                val updatedEventData = eventData.toMutableMap().apply {
                    put("images", urls)
                }
                saveEventToDatabase(updatedEventData)
            } catch (e: Exception) {
                Log.e("ReportEventActivity", "Image upload failed", e)
                Toast.makeText(
                    this@ReportEventActivity,
                    "Failed to upload images",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun saveEventToDatabase(eventData: Map<String, Any?>) {
        val database = FirebaseDatabase.getInstance()
        val eventsRef = database.getReference("Events")
        val usersRef = database.getReference("Users")

        // Get the current user's UID
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        // Fetch the current user's full name
        usersRef.child(userId).child("fullName").get()
            .addOnSuccessListener { snapshot ->
                val fullName = snapshot.getValue(String::class.java) ?: "Unknown User"

                // Get the current date and time for event submission
                val currentDateTime = SimpleDateFormat(
                    "yyyy-MM-dd HH:mm:ss",
                    Locale.getDefault()
                ).format(System.currentTimeMillis())

                // Add user information and submission time to the event data
                val updatedEventData = eventData.toMutableMap().apply {
                    put("submittedBy", fullName)
                    put("submittedAt", currentDateTime)
                    put("userId", userId)
                }

                // Generate a unique key for the new event
                val newEventRef = eventsRef.push()

                // Set the event data
                newEventRef.setValue(updatedEventData)
                    .addOnSuccessListener {
                        Log.d("ReportEventActivity", "Event data saved successfully")
                        // Update the user's points
                        updateUserPoints(usersRef, userId)
                    }
                    .addOnFailureListener { e ->
                        Log.e("ReportEventActivity", "Failed to save event data", e)
                        Toast.makeText(this, "Failed to save event", Toast.LENGTH_SHORT).show()
                    }
            }
            .addOnFailureListener { e ->
                Log.e("ReportEventActivity", "Failed to retrieve user information", e)
                Toast.makeText(this, "Failed to retrieve user information", Toast.LENGTH_SHORT)
                    .show()
            }
    }

    private fun updateUserPoints(usersRef: DatabaseReference, userId: String) {
        val userPointsRef = usersRef.child(userId).child("points")

        // Fetch the current points from the Users node
        userPointsRef.get().addOnSuccessListener { snapshot ->
            val currentPoints = snapshot.getValue(Int::class.java) ?: 0
            val newPoints = currentPoints + 5

            // Update the points in the Users node
            userPointsRef.setValue(newPoints)
                .addOnSuccessListener {
                    Log.d("ReportEventActivity", "User points updated successfully")
                    // Update the Leaderboard node after successfully updating user points
                    updateLeaderboard(userId, newPoints)
                    Toast.makeText(
                        this,
                        "You've earned a total of $newPoints points!",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .addOnFailureListener { e ->
                    Log.e("ReportEventActivity", "Failed to update user points", e)
                }
        }.addOnFailureListener { e ->
            Log.e("ReportEventActivity", "Failed to retrieve current points", e)
        }
    }

    private fun updateLeaderboard(userId: String, points: Int) {
        val leaderboardRef = FirebaseDatabase.getInstance().getReference("Leaderboard")
        val userRef = FirebaseDatabase.getInstance().getReference("Users").child(userId)

        // Fetch the full name from the Users node
        userRef.child("fullName").get().addOnSuccessListener { snapshot ->
            val fullName = snapshot.getValue(String::class.java) ?: "Unknown User"

            // Add or update the user's entry in the Leaderboard node
            leaderboardRef.child(userId).setValue(
                mapOf(
                    "fullName" to fullName,
                    "points" to points
                )
            ).addOnSuccessListener {
                Log.d("Leaderboard", "Leaderboard updated successfully for user: $fullName")
            }.addOnFailureListener { e ->
                Log.e("Leaderboard", "Failed to update leaderboard", e)
            }
        }.addOnFailureListener { e ->
            Log.e("Leaderboard", "Failed to fetch user full name", e)
        }
    }

    private fun getCurrentLocation() {
        Log.d("ReportEventActivity", "Getting current location")
        if (ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
            Log.d("ReportEventActivity", "Location permission not granted, requesting permissions")
            return
        }

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                Log.d("ReportEventActivity", "Location received: $location")
                if (location != null) {
                    val geocoder = Geocoder(this)
                    val addresses =
                        geocoder.getFromLocation(location.latitude, location.longitude, 1)
                    if (addresses != null) {
                        if (addresses.isNotEmpty()) {
                            val address = addresses[0].getAddressLine(0)  // Full address
                            Log.d("ReportEventActivity", "Address found: $address")
                            binding.tvAdditionalInfo.visibility = View.VISIBLE
                            binding.tvLocation.text = "My Location"
                            binding.tvAdditionalInfo.text = address
                        } else {
                            Log.d("ReportEventActivity", "No address found")
                            binding.tvLocation.text = getString(R.string.address_not_found)
                        }
                    }
                } else {
                    Log.d("ReportEventActivity", "Location is null")
                    binding.tvLocation.text = getString(R.string.address_not_found)
                }
            }
            .addOnFailureListener { e ->
                Log.e("ReportEventActivity", "Failed to get location", e)
            }
    }

    private fun showDateTimePicker(onDateTimeSelected: (String) -> Unit) {
        Log.d("ReportEventActivity", "Showing date time picker")
        val calendar = Calendar.getInstance()
        val currentDate = Calendar.getInstance() // Store the current date for validation

        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                // Set the selected date
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)

                // Check if the selected date is before today
                if (calendar.before(currentDate)) {
                    // Show an error message
                    Toast.makeText(
                        this,
                        "Please select a date from today or in the future.",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    // Proceed to show TimePickerDialog if the date is valid
                    TimePickerDialog(
                        this,
                        { _, hourOfDay, minute ->
                            calendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                            calendar.set(Calendar.MINUTE, minute)

                            val dateFormat =
                                SimpleDateFormat("dd/MM/yyyy h:mm a", Locale.getDefault())
                            val formattedDateTime = dateFormat.format(calendar.time)

                            Log.d("ReportEventActivity", "DateTime selected: $formattedDateTime")
                            onDateTimeSelected(formattedDateTime)

                        },
                        calendar.get(Calendar.HOUR_OF_DAY),
                        calendar.get(Calendar.MINUTE),
                        false
                    ).show()
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d("ReportEventActivity", "Request permissions result: $requestCode")
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Log.d("ReportEventActivity", "Location permission granted")
            getCurrentLocation()
        } else {
            Log.d("ReportEventActivity", "Location permission denied")
        }
    }
}
