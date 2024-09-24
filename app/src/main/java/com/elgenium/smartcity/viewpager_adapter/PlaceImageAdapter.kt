package com.elgenium.smartcity.viewpager_adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.elgenium.smartcity.R
import com.elgenium.smartcity.databinding.ViewpagerPhotoItemBinding

class PlaceImageAdapter(private val imageUrls: List<String>) :
    RecyclerView.Adapter<PlaceImageAdapter.ImageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val binding = ViewpagerPhotoItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ImageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imageUrl = imageUrls[position]
        holder.bind(imageUrl)
    }

    override fun getItemCount(): Int = imageUrls.size

    inner class ImageViewHolder(private val binding: ViewpagerPhotoItemBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(imageUrl: String) {
            Glide.with(binding.imageView.context)
                .load(imageUrl)
                .placeholder(R.drawable.placeholder_viewpager_photos)
                .into(binding.imageView)
        }
    }
}
