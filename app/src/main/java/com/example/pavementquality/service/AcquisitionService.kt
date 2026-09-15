package com.example.pavementquality.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.example.pavementquality.PavementApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AcquisitionService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        // Criar notificação para Foreground Service
        val notification: Notification = NotificationCompat.Builder(this, "acquisition_channel")
            .setContentTitle("Monitoramento de Pavimento")
            .setContentText("Adquirindo dados do acelerômetro...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        startForeground(1, notification)

        // Adquirir WakeLock Parcial para garantir processamento contínuo (Capacidade 4)
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PavementQuality::SensorWakeLock").apply {
            acquire(35 * 60 * 1000L /* 35 minutos de limite máximo */)
        }

        // Ligar a Engine
        val app = application as PavementApp
        app.engine.start(serviceScope)

        return START_STICKY
    }

    override fun onDestroy() {
        val app = application as PavementApp
        app.engine.stop()
        serviceScope.cancel()
        
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        
        super.onDestroy()
    }
}
