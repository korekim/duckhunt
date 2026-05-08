package com.korekim.duckhunt.data

data class SurveillanceNode(
    val lat: Double,
    val lon: Double,
    val id: Long,
    val typeLabel: String,
    val manufacturerLabel: String,
    val tags: String
)