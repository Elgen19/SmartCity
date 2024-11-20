package com.elgenium.smartcity.models

data class MyActivityContainer(
    var containerId: String = "",
    val name: String = "",
    val dateCreated: Long = System.currentTimeMillis(), // default to current time
)

