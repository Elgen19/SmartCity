package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.ItemOriginDestinationBinding
import com.elgenium.smartcity.models.OriginDestinationStops

class OriginDestinationAdapter(
    private val items: MutableList<OriginDestinationStops>
) : RecyclerView.Adapter<OriginDestinationAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemOriginDestinationBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemOriginDestinationBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val stop = items[position]
        holder.binding.placeName.text = stop.name
        holder.binding.placeAddress.text = stop.address
        holder.binding.dragHandleButton.visibility = View.GONE

        // Update the icon based on the type
        holder.binding.placeIcon.setImageResource(
            when (stop.type) {
                "Origin" -> R.drawable.origin
                "Destination" -> R.drawable.end
                else -> R.drawable.stops
            }
        )
    }

    override fun getItemCount(): Int = items.size


    fun updateStops(newStops: List<OriginDestinationStops>) {
        items.clear()
        items.addAll(newStops)
        notifyDataSetChanged()
    }
}
