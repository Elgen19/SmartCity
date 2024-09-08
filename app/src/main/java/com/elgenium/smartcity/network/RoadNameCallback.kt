package com.elgenium.smartcity.network

interface RoadNameCallback {
    fun onSuccess(roadName: String)
    fun onFailure(error: String)
}
