package com.elgenium.smartcity.models

import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.model.PhotoMetadata

data class SavedPlace(
    val id: String? = "",
    val name: String? = "",
    val address: String? = "",
    val phoneNumber: String? = "",
    val latLng: LatLng? = null, // LatLng object
    val latLngString: String? = "", // LatLng String for firebase realtime db storage
    val openingDaysAndTime: String? = "",
    val rating: String? = "",
    val websiteUri: String? = "",
    val distance: String? = "",
    val openingStatus: String? = "",
    val photoMetadataList: List<PhotoMetadata> = emptyList(), // List of photo metadata
)
