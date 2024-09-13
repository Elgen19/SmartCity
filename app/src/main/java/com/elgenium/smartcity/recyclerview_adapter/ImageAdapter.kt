package com.elgenium.smartcity.recyclerview_adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.elgenium.smartcity.R
import com.elgenium.smartcity.models.ReportImages

class ImageAdapter(
    private val imageList: MutableList<ReportImages>,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imgAttachment)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageItem = imageList[position]
        holder.imageView.setImageURI(imageItem.uri)

        // Set long-click listener
        holder.imageView.setOnLongClickListener {
            onLongClick(position)  // Trigger the lambda with the item's position
            true  // Return true to indicate the long-click is consumed
        }
    }

    override fun getItemCount(): Int = imageList.size

    // Method to remove image
    fun removeImage(position: Int) {
        imageList.removeAt(position)
        notifyItemRemoved(position)
        notifyItemRangeChanged(position, imageList.size)
    }
}
