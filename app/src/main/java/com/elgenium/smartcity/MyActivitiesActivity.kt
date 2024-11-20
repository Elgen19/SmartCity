package com.elgenium.smartcity

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivityMyActivitiesBinding
import com.elgenium.smartcity.databinding.BottomSheetAddContainerBinding
import com.elgenium.smartcity.databinding.BottomSheetOptionsBinding
import com.elgenium.smartcity.models.MyActivityContainer
import com.elgenium.smartcity.recyclerview_adapter.MyActivitiesAdapter
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.elgenium.smartcity.singletons.NavigationBarColorCustomizerHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class MyActivitiesActivity : AppCompatActivity() {

    private lateinit var myActivitiesAdapter: MyActivitiesAdapter
    private lateinit var binding: ActivityMyActivitiesBinding
    private val activityContainers = mutableListOf<MyActivityContainer>()
    private var userId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMyActivitiesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Set up RecyclerView
        binding.recyclerView.layoutManager = LinearLayoutManager(this)

        userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        NavigationBarColorCustomizerHelper.setNavigationBarColor(this, R.color.primary_color)

        // Fetch the list of activity containers from Firebase
        fetchActivityContainers()

        // Set up FAB to show BottomSheetDialog
        binding.fabAdd.setOnClickListener {
            showAddContainerBottomSheet(null)
        }

        onBackPressedDispatcher.addCallback(this) {
           ActivityNavigationUtils.navigateToActivity(this@MyActivitiesActivity, PlacesActivity::class.java, true)
        }
    }

    private fun fetchActivityContainers() {

        userId?.let {
            FirebaseDatabase.getInstance().reference
                .child("Users")
                .child(it)
                .child("MyActivities")
        }?.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                activityContainers.clear() // Clear existing data

                for (containerSnapshot in snapshot.children) {
                    val container = containerSnapshot.getValue(MyActivityContainer::class.java)
                    if (container != null) {
                        // You now have the container with its containerId already populated
                        activityContainers.add(container)
                    }
                }

                // Set the adapter with the fetched data
                myActivitiesAdapter = MyActivitiesAdapter(
                    activityContainers,
                    onItemClick = { clickedContainer ->
                        // Handle container click, e.g., open activities for the container
                        showActivitiesForContainer(clickedContainer)
                    },
                    onItemLongClick = { longClickedContainer ->
                        // Handle long click, e.g., show the bottom sheet dialog
                        showOptionsBottomSheet(longClickedContainer)
                    }
                )

                binding.recyclerView.adapter = myActivitiesAdapter
                checkIfRecyclerViewIsEmpty()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@MyActivitiesActivity,
                    "Failed to load activity containers",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun showOptionsBottomSheet(container: MyActivityContainer) {
        val bottomSheetDialog = BottomSheetDialog(this)
        val binding = BottomSheetOptionsBinding.inflate(layoutInflater) // Use binding


        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }
        // Handle Edit option
        binding.optionEdit.setOnClickListener {
            bottomSheetDialog.dismiss()
            Toast.makeText(this, "Edit ${container.name}", Toast.LENGTH_SHORT).show()
            showAddContainerBottomSheet(container)
        }

        // Handle Delete option
        binding.optionDelete.setOnClickListener {
            bottomSheetDialog.dismiss()

            val databaseReference = FirebaseDatabase.getInstance().reference
                .child("Users")
                .child(userId!!)
                .child("MyActivities")

            // Delete the container from Firebase using its ID
            databaseReference.child(container.containerId).removeValue()
                .addOnSuccessListener {
                    Toast.makeText(this, "${container.name} deleted successfully.", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "Failed to delete ${container.name}: ${error.message}", Toast.LENGTH_SHORT).show()
                }
            fetchActivityContainers()
            checkIfRecyclerViewIsEmpty()
        }


        bottomSheetDialog.setContentView(binding.root) // Set the root view from binding
        bottomSheetDialog.show()
    }



    private fun showActivitiesForContainer(container: MyActivityContainer) {
        // Open a new screen to display activities for the selected container
        // Pass container name as an extra
        val intent = Intent(this, ActiveActivitiesActivity::class.java)
        intent.putExtra("containerId", container.containerId)
        startActivity(intent)
    }

    // Function to show the BottomSheetDialog for adding a new container
    private fun showAddContainerBottomSheet(container: MyActivityContainer?) {
        // Inflate the layout using the binding class
        val bottomSheetBinding = BottomSheetAddContainerBinding.inflate(LayoutInflater.from(this))

        val bottomSheetDialog = BottomSheetDialog(this)
        bottomSheetDialog.setContentView(bottomSheetBinding.root)

        // If the container is not null, populate the EditText with the current name
        container?.let {
            bottomSheetBinding.etActivity.setText(it.name)
        }

        bottomSheetDialog.show()

        // Set listeners for the buttons using binding
        bottomSheetBinding.btnSet.setOnClickListener {
            val updatedName = bottomSheetBinding.etActivity.text.toString()
            if (updatedName.isNotEmpty()) {
                container?.let {
                    // Update only if the container is not null
                    updateContainerInFirebase(it.containerId, updatedName)
                } ?: run {
                    addContainerToFirebase(bottomSheetBinding.etActivity.text.toString())
                }
                bottomSheetDialog.dismiss() // Close bottom sheet after saving
            } else {
                Toast.makeText(this, "Container name cannot be empty", Toast.LENGTH_SHORT).show()
            }
        }

        bottomSheetBinding.btnCancel.setOnClickListener {
            bottomSheetDialog.dismiss() // Close bottom sheet if cancel is pressed
        }
    }

    private fun addContainerToFirebase(containerName: String) {
        if (userId == null) {
            Toast.makeText(this, "User not logged in.", Toast.LENGTH_SHORT).show()
            return
        }

        val newContainer = MyActivityContainer(
            containerId = "", // Will be generated by Firebase
            name = containerName
        )

        val databaseReference = FirebaseDatabase.getInstance().reference
            .child("Users")
            .child(userId!!)
            .child("MyActivities")

        // Generate a unique key for the container
        val containerId = databaseReference.push().key
        if (containerId != null) {
            newContainer.containerId = containerId

            databaseReference.child(containerId).setValue(newContainer)
                .addOnSuccessListener {
                    Toast.makeText(this, "Activity container added successfully", Toast.LENGTH_SHORT).show()
                    fetchActivityContainers() // Refresh the list after adding
                }
                .addOnFailureListener { error ->
                    Toast.makeText(this, "Failed to add activity container: ${error.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }



    private fun updateContainerInFirebase(containerId: String, updatedName: String) {
        // Reference to the Firebase database
        val databaseReference = userId?.let {
            FirebaseDatabase.getInstance().reference
                .child("Users")
                .child(it)  // Make sure to use the correct user ID here
                .child("MyActivities")
        }

        // Update the container name in Firebase using the container's ID
        databaseReference?.child(containerId)?.child("name")?.setValue(updatedName)
            ?.addOnSuccessListener {
                // Success: Show a toast or update UI accordingly
                Toast.makeText(this, "Container updated successfully", Toast.LENGTH_SHORT).show()
                fetchActivityContainers()
            }?.addOnFailureListener { error ->
            // Failure: Show an error message
            Toast.makeText(this, "Failed to update container: ${error.message}", Toast.LENGTH_SHORT).show()
        }
    }



    private fun checkIfRecyclerViewIsEmpty() {
        if (activityContainers.isEmpty()) {
            // Show Lottie animation and loading text
            binding.lottieAnimation.visibility = View.VISIBLE
            binding.loadingText.visibility = View.VISIBLE
            binding.recyclerView.visibility = View.GONE
        } else {
            // Hide Lottie animation and loading text, show RecyclerView
            binding.lottieAnimation.visibility = View.GONE
            binding.loadingText.visibility = View.GONE
            binding.recyclerView.visibility = View.VISIBLE
        }
    }

}
