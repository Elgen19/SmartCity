package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.databinding.ItemNotificationBinding
import com.elgenium.smartcity.models.Notification

class NotificationAdapter(private val notifications: List<Notification>) :
    RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        val binding = ItemNotificationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return NotificationViewHolder(binding)
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        val notification = notifications[position]
        holder.bind(notification)
    }

    override fun getItemCount(): Int {
        return notifications.size
    }

    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(notification: Notification) {
            binding.notificationTitle.text = notification.title
            binding.notificationMessage.text = notification.message
            binding.notificationTimestamp.text = formatTimestamp(notification.timestamp)
        }

        private fun formatTimestamp(timestamp: Long?): String {
            // Convert timestamp to readable date/time format if needed
            return timestamp?.let { java.text.SimpleDateFormat("MMM dd, yyyy HH:mm:ss").format(java.util.Date(it)) } ?: "N/A"
        }
    }
}


