package com.elgenium.smartcity.singletons

import com.google.android.libraries.places.api.model.Place

object PlaceCacheManager {
    val placeDetailsCache = mutableMapOf<String, Place>()
}
