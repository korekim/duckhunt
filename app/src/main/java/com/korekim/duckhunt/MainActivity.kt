package com.korekim.duckhunt

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.animation.Animation
import android.view.animation.RotateAnimation
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.korekim.duckhunt.data.PrefsManager
import com.korekim.duckhunt.service.ProximityService
import com.korekim.duckhunt.ui.log.LogActivity
import com.korekim.duckhunt.ui.settings.SettingsActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var btnPower: android.widget.ImageButton
    private lateinit var btnPowerMain: android.widget.ImageButton
    private lateinit var tvNearest: TextView
    private lateinit var tvDistance: TextView
    private lateinit var tvTags: TextView
    private lateinit var ivArrow: ImageView
    private lateinit var prefsManager: PrefsManager

    private var currentArrowRotation = 0f
    private var uiJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                window.decorView.post {
                    AlertDialog.Builder(this)
                        .setTitle("Location Permission Required")
                        .setMessage("DuckHunt needs location permission to detect nearby surveillance devices. Please grant location access in Settings.")
                        .setPositiveButton("Open Settings") { _, _ ->
                            startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = android.net.Uri.fromParts("package", packageName, null)
                            })
                        }
                        .setNegativeButton("Dismiss", null)
                        .show()
                }
            }
        }

    private val requestNotificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val requestBackgroundLocation =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<androidx.appcompat.widget.Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        btnPower     = findViewById(R.id.btnPower)
        btnPowerMain = findViewById(R.id.btnPowerMain)
        tvNearest    = findViewById(R.id.tvNearest)
        tvDistance   = findViewById(R.id.tvDistance)
        tvTags       = findViewById(R.id.tvTags)
        ivArrow      = findViewById(R.id.ivArrow)

        ivArrow.setColorFilter(
            android.graphics.Color.parseColor("#4CAF50"),
            android.graphics.PorterDuff.Mode.SRC_IN
        )

        prefsManager = PrefsManager(this)

        createNotificationChannel()
        updatePowerState()

        btnPower.setOnClickListener { toggleService() }
        btnPowerMain.setOnClickListener { toggleService() }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
                requestBackgroundLocation.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        } else {
            requestPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                requestNotificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updatePowerState()
        uiJob = scope.launch {
            launch {
                ProximityService.nearestUpdate.collect { data ->
                    data ?: return@collect
                    val distanceFt = data["distanceFt"] as Double
                    val bearing    = data["bearing"] as Double
                    val cardinal   = data["cardinal"] as String
                    val heading    = (data["heading"] as? Double) ?: 0.0
                    val relativeBearing = ((bearing - heading) + 360) % 360
                    tvDistance.text = "Distance: ${"%.0f".format(distanceFt)}ft, ${"%.0f".format(bearing)}° ($cardinal)"
                    setArrowColor(distanceFt)
                    updateArrow(relativeBearing)
                }
            }
            launch {
                ProximityService.labelUpdate.collect { data ->
                    data ?: return@collect
                    tvNearest.text = "Nearest: ${data["label"]}"
                    tvTags.text    = data["tags"] as? String ?: ""
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()
        uiJob?.cancel()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: android.view.MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_settings -> {
                startActivity(Intent(this, SettingsActivity::class.java))
                true
            }
            R.id.action_map -> {
                val uri = android.net.Uri.parse("https://sunders.uber.space/")
                startActivity(Intent(Intent.ACTION_VIEW, uri))
                true
            }
            R.id.action_log -> {
                startActivity(Intent(this, LogActivity::class.java))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun toggleService() {
        if (ProximityService.isRunning) {
            stopService(Intent(this, ProximityService::class.java))
            ProximityService.isRunning = false
            getSystemService(android.app.NotificationManager::class.java).cancelAll()
        } else {
            ProximityService.isRunning = true
            ContextCompat.startForegroundService(this, Intent(this, ProximityService::class.java))
        }
        updatePowerState()
    }

    private fun updatePowerState() {
        val running = ProximityService.isRunning
        val color = if (running)
            android.graphics.Color.parseColor("#4CAF50")
        else
            android.graphics.Color.parseColor("#F44336")

        btnPower.setColorFilter(color)
        btnPowerMain.visibility  = if (running) android.view.View.GONE else android.view.View.VISIBLE
        btnPowerMain.setColorFilter(android.graphics.Color.parseColor("#F44336"))
        tvNearest.visibility     = if (running) android.view.View.VISIBLE else android.view.View.GONE
        tvDistance.visibility    = if (running) android.view.View.VISIBLE else android.view.View.GONE
        tvTags.visibility        = if (running) android.view.View.VISIBLE else android.view.View.GONE

        if (!running) ivArrow.clearAnimation()
        ivArrow.visibility = if (running) android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun updateArrow(bearing: Double) {
        val targetRotation = bearing.toFloat()
        val animation = RotateAnimation(
            currentArrowRotation, targetRotation,
            Animation.RELATIVE_TO_SELF, 0.5f,
            Animation.RELATIVE_TO_SELF, 0.5f
        ).apply {
            duration = 200
            fillAfter = true
        }
        ivArrow.startAnimation(animation)
        currentArrowRotation = targetRotation
    }

    private fun setArrowColor(distanceFt: Double) {
        val color = when {
            distanceFt <= prefsManager.alertCritical -> android.graphics.Color.parseColor("#F44336")
            distanceFt <= prefsManager.alertWarning  -> android.graphics.Color.parseColor("#FFC107")
            else -> android.graphics.Color.parseColor("#4CAF50")
        }
        ivArrow.setColorFilter(color, android.graphics.PorterDuff.Mode.SRC_IN)
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(android.app.NotificationManager::class.java)
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                "DuckHunt", "DuckHunt",
                android.app.NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Nearby surveillance device alerts"
                enableVibration(true)
            }
        )
        manager.createNotificationChannel(
            android.app.NotificationChannel(
                "alpr_alert", "Proximity Alert",
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Fired when within range of a surveillance device"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 300, 100, 300, 100, 300)
            }
        )
    }
}