package com.elgenium.smartcity

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import com.elgenium.smartcity.databinding.ActivityNotificationHistoryBinding
import com.elgenium.smartcity.models.Notification
import com.elgenium.smartcity.recyclerview_adapter.NotificationAdapter
import com.elgenium.smartcity.singletons.ActivityNavigationUtils
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class NotificationHistoryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityNotificationHistoryBinding
    private lateinit var adapter: NotificationAdapter
    private val notifications = mutableListOf<Notification>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityNotificationHistoryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize RecyclerView
        setupRecyclerView()
        loadNotifications()

        // Set up back button
        binding.backButton.setOnClickListener {
            ActivityNavigationUtils.navigateToActivity(this, DashboardActivity::class.java, true)
        }

        // Set up clear all button
        binding.btnClearAll.setOnClickListener {
            clearAllNotifications()
        }
    }

    private fun setupRecyclerView() {
        adapter = NotificationAdapter(notifications)
        binding.notificationRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@NotificationHistoryActivity)
            adapter = this@NotificationHistoryActivity.adapter

            // Add custom divider
            val dividerDrawable = ContextCompat.getDrawable(context, R.drawable.divider_drawable)
            val itemDecoration = DividerItemDecoration(context, LinearLayoutManager.VERTICAL)
            dividerDrawable?.let { itemDecoration.setDrawable(it) }
            addItemDecoration(itemDecoration)
        }
    }

    private fun loadNotifications() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val notificationsRef = FirebaseDatabase.getInstance().getReference("Users/$userId/Notifications")
        notificationsRef.orderByChild("timestamp").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                notifications.clear()
                for (dataSnapshot in snapshot.children) {
                    val notification = dataSnapshot.getValue(Notification::class.java)
                    notification?.let { notifications.add(it) }
                }
                adapter.notifyDataSetChanged()
                binding.lotifyAnimation.visibility = if (notifications.isEmpty()) View.VISIBLE else View.GONE
                binding.notificationRecyclerView.visibility = if (notifications.isNotEmpty()) View.VISIBLE else View.GONE
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle possible errors
                Log.e("NotificationHistoryActivity", "Error loading notifications: ${error.message}")
            }
        })
    }

    private fun clearAllNotifications() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val notificationsRef = FirebaseDatabase.getInstance().getReference("Users/$userId/Notifications")
        notificationsRef.removeValue().addOnSuccessListener {
            notifications.clear()
            adapter.notifyDataSetChanged()
            binding.lotifyAnimation.visibility = View.VISIBLE
            binding.notificationRecyclerView.visibility = View.GONE
        }.addOnFailureListener { e ->
            Log.e("NotificationHistoryActivity", "Error loading notifications: $e")
        }
    }
}