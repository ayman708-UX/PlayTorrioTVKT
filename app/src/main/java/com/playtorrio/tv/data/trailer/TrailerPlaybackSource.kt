package com.playtorrio.tv.data.trailer

data class TrailerPlaybackSource(
    val videoUrl: String,
    val audioUrl: String? = null,
    val clientUserAgent: String? = null
)
