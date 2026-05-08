package com.korekim.duckhunt.ui.settings

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.korekim.duckhunt.R
import com.korekim.duckhunt.data.PrefsManager

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.toolbarLog)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val prefsManager = PrefsManager(this)

        val etOuter    = findViewById<EditText>(R.id.etOuter)
        val etWarning  = findViewById<EditText>(R.id.etWarning)
        val etCritical = findViewById<EditText>(R.id.etCritical)
        val btnSave    = findViewById<Button>(R.id.btnSave)
        val tvQueryCount = findViewById<TextView>(R.id.tvQueryCountSettings)

        etOuter.setText(prefsManager.alertOuter.toString())
        etWarning.setText(prefsManager.alertWarning.toString())
        etCritical.setText(prefsManager.alertCritical.toString())

        tvQueryCount.text = "Overpass API Queries today: ${prefsManager.getQueryCountForToday()} / 10000"

        btnSave.setOnClickListener {
            prefsManager.alertOuter   = etOuter.text.toString().toIntOrNull() ?: 950
            prefsManager.alertWarning = etWarning.text.toString().toIntOrNull() ?: 300
            prefsManager.alertCritical = etCritical.text.toString().toIntOrNull() ?: 150
            finish()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}