package com.elgenium.smartcity.info_window_adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.elgenium.smartcity.R
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker

class CustomInfoWindowAdapter(context: Context) : GoogleMap.InfoWindowAdapter {
    private val contentView: View = LayoutInflater.from(context).inflate(R.layout.custom_info_window, null)

    override fun getInfoWindow(marker: Marker): View? {
        // You can return null here to use the default frame
        return null
    }

    override fun getInfoContents(marker: Marker): View {
        // Set custom view contents here based on marker data
        val titleTextView = contentView.findViewById<TextView>(R.id.info_window_title)
        val snippetTextView = contentView.findViewById<TextView>(R.id.info_window_snippet)

        titleTextView.text = marker.title
        snippetTextView.text = marker.snippet

        return contentView
    }
}
