package com.korekim.duckhunt.ui.log

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.korekim.duckhunt.R
import com.korekim.duckhunt.data.PrefsManager
import com.korekim.duckhunt.service.ProximityService

class LogActivity : AppCompatActivity() {

    private lateinit var prefsManager: PrefsManager
    private lateinit var rvLog: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_log)

        val toolbar = findViewById<Toolbar>(R.id.toolbarLog)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        prefsManager = PrefsManager(this)

        rvLog = findViewById(R.id.rvLog)
        rvLog.layoutManager = LinearLayoutManager(this)

        loadLog()

        findViewById<Button>(R.id.btnClearLog).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Clear Log")
                .setMessage("Are you sure you want to clear all log entries?")
                .setPositiveButton("Clear") { _, _ ->
                    prefsManager.clearCameraLog()
                    ProximityService.lastLoggedNodeId = -1L
                    rvLog.adapter = LogAdapter(emptyList())
                }
                .setNegativeButton("Cancel", null)
                .show()
        }
    }

    private fun loadLog() {
        val raw = prefsManager.getCameraLog()
        val entries = if (raw.isEmpty()) emptyList() else {
            raw.split("\n").mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 4) {
                    try {
                        LogEntry(
                            timestamp = parts[0],
                            label = parts[1],
                            lat = parts[2].toDouble(),
                            lon = parts[3].toDouble(),
                            tags = if (parts.size >= 5) parts[4] else ""
                        )
                    } catch (e: Exception) { null }
                } else null
            }
        }
        rvLog.adapter = LogAdapter(entries)
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}