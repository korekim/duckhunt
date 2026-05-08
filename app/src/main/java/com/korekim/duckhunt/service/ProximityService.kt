package com.korekim.duckhunt.service

import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.korekim.duckhunt.MainActivity
import com.korekim.duckhunt.R
import com.korekim.duckhunt.data.PrefsManager
import com.korekim.duckhunt.data.SurveillanceNode
import com.korekim.duckhunt.util.GeoUtils
import com.korekim.duckhunt.util.NotificationHelper
import kotlinx.coroutines.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class ProximityService : Service() {

    companion object {
        val nearestUpdate = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Any>?>(null)
        val labelUpdate = kotlinx.coroutines.flow.MutableStateFlow<Map<String, Any>?>(null)
        var lastLoggedNodeId = -1L
        var isRunning = false
    }

    private lateinit var locationManager: LocationManager
    private lateinit var sensorManager: SensorManager
    private lateinit var prefsManager: PrefsManager
    private lateinit var notificationHelper: NotificationHelper

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var lastLocation: Location? = null
    private var lastQueriedLocation: Location? = null
    private var lastFetchTime = 0L
    private var isFetching = false

    private var cachedElements: List<SurveillanceNode> = emptyList()
    private var lastTypeLabel = "unknown type"
    private var lastManufacturerLabel = "unknown manufacturer"
    private var lastNotifiedNodeId = -1L

    private var alertedAt950 = false
    private var alertedAt300 = false
    private var alertedAt150 = false

    private var currentHeading = 0f
    private val gravity = FloatArray(3)
    private val geomagnetic = FloatArray(3)

    private lateinit var wakeLock: android.os.PowerManager.WakeLock

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER ->
                    System.arraycopy(event.values, 0, gravity, 0, event.values.size)
                Sensor.TYPE_MAGNETIC_FIELD ->
                    System.arraycopy(event.values, 0, geomagnetic, 0, event.values.size)
            }
            val r = FloatArray(9)
            val i = FloatArray(9)
            if (SensorManager.getRotationMatrix(r, i, gravity, geomagnetic)) {
                val orientation = FloatArray(3)
                SensorManager.getOrientation(r, orientation)
                val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
                currentHeading = (azimuth + 360) % 360
                lastLocation?.let { updateNearest(it) }
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val locationListener = LocationListener { location ->
        lastLocation = location
        updateNearest(location)

        val now = System.currentTimeMillis()
        val last = lastQueriedLocation
        val movedEnough = last == null || last.distanceTo(location) >= 20f

        if (movedEnough && !isFetching) {
            val speed = location.speed
            val minInterval = if (speed > 11.2f) 3_000L else 10_000L
            if (now - lastFetchTime >= minInterval) {
                lastFetchTime = now
                scope.launch { fetchNearest(location) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefsManager = PrefsManager(this)
        notificationHelper = NotificationHelper(this)
        locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true

        val powerManager = getSystemService(android.os.PowerManager::class.java)
        wakeLock = powerManager.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK,
            "DuckHunt::ProximityWakeLock"
        )
        wakeLock.acquire()

        val notification = NotificationCompat.Builder(this, "DuckHunt")
            .setSmallIcon(R.drawable.ic_laucher_foreground)
            .setContentTitle("DuckHunt")
            .setContentText("Scanning for surveillance devices...")
            .setOngoing(true)
            .setSilent(true)
            .build()
        startForeground(1, notification)

        startLocationUpdates()
        startCompass()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) return

        locationManager.requestLocationUpdates(
            LocationManager.GPS_PROVIDER,
            500L,
            0f,
            locationListener
        )
    }

    private fun startCompass() {
        sensorManager.registerListener(
            sensorListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
            SensorManager.SENSOR_DELAY_UI
        )
        sensorManager.registerListener(
            sensorListener,
            sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD),
            SensorManager.SENSOR_DELAY_UI
        )
    }

    private fun updateNearest(myLoc: Location) {
        if (cachedElements.isEmpty()) return

        var nearest: SurveillanceNode? = null
        var minDist = Double.MAX_VALUE

        for (element in cachedElements) {
            val dist = GeoUtils.haversineMeters(myLoc.latitude, myLoc.longitude, element.lat, element.lon)
            if (dist < minDist) {
                minDist = dist
                nearest = element
            }
        }

        nearest ?: return
        val distanceFt = GeoUtils.metersToFeet(minDist)
        val bearing = GeoUtils.bearingDegrees(myLoc.latitude, myLoc.longitude, nearest.lat, nearest.lon)
        val cardinal = GeoUtils.bearingToCardinal(bearing)

        nearestUpdate.value = mapOf(
            "distanceFt" to distanceFt,
            "bearing" to bearing,
            "cardinal" to cardinal,
            "label" to "${nearest.manufacturerLabel} ${nearest.typeLabel}",
            "tags" to nearest.tags,
            "heading" to currentHeading.toDouble()
        )

        lastTypeLabel = nearest.typeLabel
        lastManufacturerLabel = nearest.manufacturerLabel

        val isNewNearest = nearest.id != lastNotifiedNodeId
        if (isNewNearest) {
            lastNotifiedNodeId = nearest.id
            alertedAt950 = false
            alertedAt300 = false
            alertedAt150 = false
        }

        val outerAlert = prefsManager.alertOuter.toFloat()
        val warningAlert = prefsManager.alertWarning.toFloat()
        val criticalAlert = prefsManager.alertCritical.toFloat()

        if (distanceFt <= criticalAlert) {
            if (!alertedAt150) {
                alertedAt150 = true
                if (nearest.id != lastLoggedNodeId) {
                    lastLoggedNodeId = nearest.id
                    logCameraEncounter("$lastManufacturerLabel $lastTypeLabel", nearest.lat, nearest.lon, nearest.tags)
                }
                notificationHelper.postProximityAlert(
                    distanceFt, bearing, cardinal,
                    "$lastManufacturerLabel $lastTypeLabel",
                    "🔴 License plate most likely read",
                    "alpr_alert"
                )
            }
        } else if (distanceFt <= warningAlert) {
            if (!alertedAt300) {
                alertedAt300 = true
                notificationHelper.postProximityAlert(
                    distanceFt, bearing, cardinal,
                    "$lastManufacturerLabel $lastTypeLabel",
                    "🟡 Approaching — ${warningAlert.toInt()}ft",
                    "alpr_alert"
                )
            }
        } else if (distanceFt <= outerAlert) {
            if (!alertedAt950) {
                alertedAt950 = true
                notificationHelper.postProximityAlert(
                    distanceFt, bearing, cardinal,
                    "$lastManufacturerLabel $lastTypeLabel",
                    "⚠️ Surveillance device nearby",
                    "alpr_alert"
                )
            }
        } else {
            alertedAt950 = false
            alertedAt300 = false
            alertedAt150 = false
        }

        notificationHelper.postPersistentNotification(
            distanceFt, bearing, cardinal,
            "$lastManufacturerLabel $lastTypeLabel",
            currentHeading,
            lastLocation
        )
    }

    private suspend fun fetchNearest(myLoc: Location) {
        lastQueriedLocation = myLoc
        isFetching = true
        try {
            val delta = 0.009
            val s = myLoc.latitude - delta
            val n = myLoc.latitude + delta
            val w = myLoc.longitude - delta
            val e = myLoc.longitude + delta

            val query = "[out:json][timeout:25];node[\"man_made\"=\"surveillance\"]($s,$w,$n,$e);out body;"

            val json = withContext(Dispatchers.IO) {
                try {
                    val client = OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()
                    val encodedQuery = java.net.URLEncoder.encode(query, "UTF-8").replace("+", "%20")
                    val body = "data=$encodedQuery".toRequestBody("application/x-www-form-urlencoded".toMediaType())
                    val request = Request.Builder()
                        .url("https://overpass-api.de/api/interpreter")
                        .header("User-Agent", "DuckHunt/1.0 (private app; youremail@example.com)")
                        .post(body)
                        .build()

                    prefsManager.incrementQueryCount()

                    client.newCall(request).execute().use { it.body?.string() }
                } catch (e: java.net.UnknownHostException) {
                    Log.e("DuckHunt", "DNS failed: ${e.message}")
                    null
                } catch (e: Exception) {
                    Log.e("DuckHunt", "Network error: ${e::class.simpleName}: ${e.message}")
                    null
                }
            } ?: return

            val elements = JSONObject(json).getJSONArray("elements")
            if (elements.length() == 0) {
                cachedElements = emptyList()
                return
            }

            cachedElements = (0 until elements.length()).map { i ->
                val el = elements.getJSONObject(i)
                val t = el.optJSONObject("tags")
                val label = t?.optString("surveillance:type", null)
                    ?: t?.optString("surveillance", null)
                    ?: "unknown type"
                val manufacturer = t?.optString("manufacturer", null) ?: "unknown manufacturer"
                val tagOutput = StringBuilder()
                if (t != null) {
                    val keys = t.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        tagOutput.append("$key: ${t.getString(key)}~")
                    }
                }
                SurveillanceNode(
                    lat = el.getDouble("lat"),
                    lon = el.getDouble("lon"),
                    id = el.getLong("id"),
                    typeLabel = label,
                    manufacturerLabel = manufacturer,
                    tags = tagOutput.toString()
                )
            }

            var nearest: JSONObject? = null
            var minDist = Double.MAX_VALUE
            for (i in 0 until elements.length()) {
                val el = elements.getJSONObject(i)
                val dist = GeoUtils.haversineMeters(
                    myLoc.latitude, myLoc.longitude,
                    el.getDouble("lat"), el.getDouble("lon")
                )
                if (dist < minDist) { minDist = dist; nearest = el }
            }

            nearest?.let {
                val tags = it.optJSONObject("tags")
                val manufacturer = tags?.optString("manufacturer", null) ?: "unknown manufacturer"
                val type = tags?.optString("surveillance:type", null)
                    ?: tags?.optString("surveillance", null) ?: "unknown type"
                val tagOutput = StringBuilder()
                if (tags != null) {
                    val keys = tags.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        tagOutput.appendLine("$key: ${tags.getString(key)}")
                    }
                }
                labelUpdate.value = mapOf(
                    "label" to "$manufacturer $type",
                    "tags" to tagOutput.toString()
                )
            }

            lastLocation?.let { updateNearest(it) }

        } finally {
            isFetching = false
        }
    }

    private fun logCameraEncounter(label: String, lat: Double, lon: Double, tags: String) {
        val timestamp = java.time.LocalDateTime.now()
            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        val tagsOneLine = tags.replace("\n", "~")
        val entry = "$timestamp|$label|$lat|$lon|$tagsOneLine"
        prefsManager.appendCameraLog(entry)
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
        locationManager.removeUpdates(locationListener)
        sensorManager.unregisterListener(sensorListener)
        scope.cancel()
    }
}