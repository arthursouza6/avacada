package com.example.pavementquality

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.example.pavementquality.data.HardwareSensorManager
import com.example.pavementquality.data.LogRecorder
import com.example.pavementquality.domain.Engine
import com.example.pavementquality.domain.Reconciler
import com.example.pavementquality.domain.TrackRecord

class PavementApp : Application() {

    lateinit var engine: Engine
    lateinit var hardwareManager: HardwareSensorManager

    override fun onCreate() {
        super.onCreate()
        
        hardwareManager = HardwareSensorManager(this)
        val trackRecord = TrackRecord(1000) 
        val reconciler = Reconciler(chiSquareThreshold = 5.99f) 
        val logRecorder = LogRecorder(this)
        
        engine = Engine(hardwareManager, trackRecord, reconciler, logRecorder)
        
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "acquisition_channel",
                "Aquisição de Sensores",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }
}
