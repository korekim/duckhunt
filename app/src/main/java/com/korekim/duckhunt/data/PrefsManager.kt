package com.korekim.duckhunt.data

import android.content.Context
import android.content.SharedPreferences

class PrefsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("duckhunt", Context.MODE_PRIVATE)

    var alertOuter: Int
        get() = prefs.getInt("alert_outer", 1000)
        set(value) = prefs.edit().putInt("alert_outer", value).apply()

    var alertWarning: Int
        get() = prefs.getInt("alert_warning", 300)
        set(value) = prefs.edit().putInt("alert_warning", value).apply()

    var alertCritical: Int
        get() = prefs.getInt("alert_critical", 150)
        set(value) = prefs.edit().putInt("alert_critical", value).apply()

    val queryCount: Int
        get() = prefs.getInt("query_count", 0)

    fun incrementQueryCount() {
        val today = java.time.LocalDate.now().toString()
        val savedDate = prefs.getString("query_date", null)
        val current = if (savedDate == today) prefs.getInt("query_count", 0) else 0
        prefs.edit()
            .putInt("query_count", current + 1)
            .putString("query_date", today)
            .apply()
    }

    fun getQueryCountForToday(): Int {
        val today = java.time.LocalDate.now().toString()
        val savedDate = prefs.getString("query_date", null)
        return if (savedDate == today) prefs.getInt("query_count", 0) else 0
    }

    fun getCameraLog(): String = prefs.getString("camera_log", "") ?: ""

    fun appendCameraLog(entry: String) {
        val existing = getCameraLog()
        val updated = if (existing.isEmpty()) entry else "$entry\n$existing"
        prefs.edit().putString("camera_log", updated).apply()
    }

    fun clearCameraLog() {
        prefs.edit().remove("camera_log").apply()
    }
}