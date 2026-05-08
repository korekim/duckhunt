package com.korekim.duckhunt.util

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.net.Uri
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.korekim.duckhunt.MainActivity
import com.korekim.duckhunt.R

class NotificationHelper(private val context: Context) {

    private val notificationManager =
        androidx.core.app.NotificationManagerCompat.from(context)

    fun postPersistentNotification(
        distanceFt: Double,
        bearing: Double,
        cardinal: String,
        typeLabel: String,
        currentHeading: Float,
        lastLocation: android.location.Location?
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        val arrowColor = arrowColor(distanceFt)
        val arrowDrawable = ContextCompat.getDrawable(context, R.drawable.ic_arrow) ?: return
        arrowDrawable.setColorFilter(arrowColor, android.graphics.PorterDuff.Mode.SRC_IN)

        val size = 128
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val relativeBearing = ((bearing - currentHeading) + 360) % 360
        val matrix = Matrix().apply {
            postTranslate(-size / 2f, -size / 2f)
            postRotate(relativeBearing.toFloat())
            postTranslate(size / 2f, size / 2f)
        }
        canvas.concat(matrix)
        arrowDrawable.setBounds(0, 0, size, size)
        arrowDrawable.draw(canvas)

        val appIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, appIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mapUri = if (lastLocation != null)
            Uri.parse("https://sunders.uber.space/?zoom=17&lat=${lastLocation.latitude}&lon=${lastLocation.longitude}")
        else
            Uri.parse("https://sunders.uber.space/")
        val mapPendingIntent = PendingIntent.getActivity(
            context, 1,
            Intent(Intent.ACTION_VIEW, mapUri),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, "DuckHunt")
            .setSmallIcon(R.drawable.ic_laucher_foreground)
            .setLargeIcon(bitmap)
            .setContentTitle(typeLabel)
            .setContentText("Distance: ${"%.0f".format(distanceFt)}ft, ${"%.0f".format(bearing)}° ($cardinal)")
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)
            .addAction(0, "View Map", mapPendingIntent)
            .build()

        notificationManager.notify(1, notification)
    }

    fun postProximityAlert(
        distanceFt: Double,
        bearing: Double,
        cardinal: String,
        label: String,
        title: String,
        channelId: String
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) return

        try {
            val vibrator = context.getSystemService(Vibrator::class.java)
            vibrator.vibrate(VibrationEffect.createWaveform(
                longArrayOf(0, 300, 100, 300, 100, 300), -1))
        } catch (e: SecurityException) {
            Log.e("DuckHunt", "Vibration failed: ${e.message}")
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_arrow)
            .setContentTitle(title)
            .setContentText("$label — ${"%.0f".format(distanceFt)}ft ${"%.0f".format(bearing)}° ($cardinal)")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(2, notification)
    }

    fun cancelAll() = notificationManager.cancelAll()

    private fun arrowColor(distanceFt: Double) = when {
        distanceFt <= 150f -> android.graphics.Color.parseColor("#F44336")
        distanceFt <= 300f -> android.graphics.Color.parseColor("#FFC107")
        else -> android.graphics.Color.parseColor("#4CAF50")
    }
}