package com.elgenium.smartcity.models

import android.net.Uri

data class ReportImages(
    val uri: Uri? = null, // Nullable Uri for locally added images
    val url: String? = null // Nullable String for Firebase URL images
)
