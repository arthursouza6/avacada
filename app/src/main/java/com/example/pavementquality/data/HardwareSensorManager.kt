package com.example.pavementquality.data

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import com.example.pavementquality.domain.KinematicFix
import com.example.pavementquality.domain.VibrationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Capacidade 4: Gerenciador de Aquisição de Hardware real.
 * Usa Acelerômetro e GPS Real para enviar dados ao Motor.
 */
class HardwareSensorManager(context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _latestVibration = MutableStateFlow<VibrationSample?>(null)
    val latestVibration: StateFlow<VibrationSample?> = _latestVibration

    private val _latestFix = MutableStateFlow<KinematicFix?>(null)
    val latestFix: StateFlow<KinematicFix?> = _latestFix

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                val zAccel = event.values[2]
                val mappedAccuracy = if (event.accuracy <= 0) 1 else event.accuracy
                
                _latestVibration.value = VibrationSample(
                    zAcceleration = zAccel,
                    accuracy = mappedAccuracy,
                    timestampMs = System.currentTimeMillis()
                )
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private val locationListener = LocationListener { location ->
        _latestFix.value = KinematicFix(
            latitude = location.latitude,
            longitude = location.longitude,
            speedMetersPerSecond = if (location.hasSpeed()) location.speed else 0f,
            speedAccuracyMetersPerSecond = if (location.hasSpeedAccuracy()) location.speedAccuracyMetersPerSecond else 1.0f,
            timestampMs = System.currentTimeMillis()
        )
    }

    @SuppressLint("MissingPermission")
    fun startAcquisition(scope: CoroutineScope) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        // SENSOR_DELAY_NORMAL ou GAME
        sensorManager.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_NORMAL)

        // Tenta obter o GPS real a 1Hz (1000L)
        try {
            locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, locationListener)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback caso não consiga via GPS_PROVIDER, tenta NETWORK_PROVIDER para tablets sem chip
            try {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 1000L, 0f, locationListener)
            } catch (e2: Exception) {
                e2.printStackTrace()
            }
        }
    }

    fun stopAcquisition() {
        sensorManager.unregisterListener(sensorListener)
        locationManager.removeUpdates(locationListener)
    }
}
