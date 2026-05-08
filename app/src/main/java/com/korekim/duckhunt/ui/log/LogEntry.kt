package com.korekim.duckhunt.ui.log

data class LogEntry(
    val timestamp: String,
    val label: String,
    val lat: Double,
    val lon: Double,
    val tags: String
)