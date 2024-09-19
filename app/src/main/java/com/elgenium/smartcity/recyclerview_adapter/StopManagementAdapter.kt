package com.elgenium.smartcity.recyclerview_adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.ItemOriginDestinationBinding
import com.elgenium.smartcity.models.OriginDestinationStops


class StopManagementAdapter(
    private val context: Context,
    var stopsList: MutableList<OriginDestinationStops>) :
    RecyclerView.Adapter<StopManagementAdapter.ViewHolder>() {

    // Track which item has delete/cancel buttons visible
    private var currentlyVisibleItem: Int = -1

    inner class ViewHolder(val binding: ItemOriginDestinationBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(stop: OriginDestinationStops, position: Int) {
            binding.placeName.text = stop.name
            binding.placeAddress.text = stop.address
            binding.placeIcon.setImageDrawable(null)
            updateIcon(binding.placeIcon, position)

            // Ensure buttons are hidden if this item is not swiped
            if (currentlyVisibleItem == position) {
                showConfirmationAndCancelButtons()
            } else {
                hideConfirmationAndCancelButtons()
            }
        }

        private fun updateIcon(imageView: ImageView, position: Int) {
            val iconRes = when (position) {
                0 -> R.drawable.origin
                stopsList.size - 1 -> R.drawable.end
                else -> R.drawable.stops
            }
            imageView.setImageResource(iconRes)
        }

        // Show delete and cancel buttons
        private fun showConfirmationAndCancelButtons() {
            binding.confirmDeleteButton.visibility = View.VISIBLE
            binding.cancelButton.visibility = View.VISIBLE
            binding.dragHandleButton.visibility = View.GONE
        }

        // Hide delete and cancel buttons
        fun hideConfirmationAndCancelButtons() {
            binding.confirmDeleteButton.visibility = View.GONE
            binding.cancelButton.visibility = View.GONE
            binding.dragHandleButton.visibility = View.VISIBLE
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOriginDestinationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stop = stopsList[position]
        holder.bind(stop, position)

        // Set up delete confirmation button click listener
        holder.binding.confirmDeleteButton.setOnClickListener {
            removeItem(position)
        }

        // Set up cancel button click listener
        holder.binding.cancelButton.setOnClickListener {
            // Hide buttons and reset the swipe action
            holder.hideConfirmationAndCancelButtons()
            currentlyVisibleItem = -1 // Reset visible item tracker
            notifyItemChanged(position) // Reset the item appearance
        }
    }

    override fun getItemCount(): Int {
        return stopsList.size
    }

    private fun removeItem(position: Int) {
        // Check if there are more than 2 items before proceeding with deletion
        if (stopsList.size > 2) {
            stopsList.removeAt(position)
            currentlyVisibleItem = -1 // Reset the visible item tracker after deletion
            notifyItemRemoved(position)
            refreshIcons()
        } else {
            // Show a toast message if there are only 2 or fewer items
            Toast.makeText(
                context,
                "Cannot delete. At least 3 items are required.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    fun updateStopTypes() {
        // Check if there are any items in the list
        if (stopsList.isNotEmpty()) {
            // Clear all types initially
            stopsList.forEach { it.type = "Stop" }

            // Set the type of the first item as Origin
            stopsList[0].type = "Origin"

            // Set the type of the last item as Destination
            if (stopsList.size > 1) {
                stopsList[stopsList.size - 1].type = "Destination"
            }
        }
    }


    // Method to show delete confirmation and cancel buttons for a specific item
    fun showDeleteConfirmation(position: Int) {
        // Only one item can show the delete/cancel buttons at a time
        if (currentlyVisibleItem != -1) {
            notifyItemChanged(currentlyVisibleItem) // Reset previous visible item
        }
        currentlyVisibleItem = position
        notifyItemChanged(position) // Show buttons for the swiped item
    }

    fun refreshIcons() {
        notifyDataSetChanged() // Refresh all items to update icons
    }
}
