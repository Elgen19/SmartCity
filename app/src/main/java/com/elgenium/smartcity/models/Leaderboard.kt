package com.elgenium.smartcity.models

data class Leaderboard(
    val userId: String,
    val name: String,
    val profileImageUrl: String?,
    val points: Int,
)
