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
import android.os.Looper
import android.util.Log
import com.example.pavementquality.domain.KinematicFix
import com.example.pavementquality.domain.VibrationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Capacidade 4: Gerenciador de Aquisição de Hardware real.
 * Usa Acelerômetro e GPS Real para enviar dados ao Motor.
 */
class HardwareSensorManager(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _latestVibration = MutableStateFlow<VibrationSample?>(null)
    val latestVibration: StateFlow<VibrationSample?> = _latestVibration

    private val _latestFix = MutableStateFlow<KinematicFix?>(null)
    val latestFix: StateFlow<KinematicFix?> = _latestFix

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent?) {
            try {
                if (event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
                    val zAccel = event.values[2]
                    val mappedAccuracy = if (event.accuracy <= 0) 1 else event.accuracy
                    
                    _latestVibration.value = VibrationSample(
                        zAcceleration = zAccel,
                        accuracy = mappedAccuracy,
                        timestampMs = System.currentTimeMillis()
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    private var lastLocation: Location? = null

    private val locationListener = LocationListener { location ->
        try {
            Log.d("HWManager", "Location update from ${location.provider}: lat=${location.latitude}, lon=${location.longitude}, hasSpeed=${location.hasSpeed()}, speed=${location.speed}")
            
            var speed = 0f
            var speedAcc = 2.0f

            // Tentar velocidade nativa do chip GPS primeiro
            if (location.hasSpeed()) {
                val locSpeed = location.speed
                if (!locSpeed.isNaN() && locSpeed >= 0f) {
                    speed = locSpeed
                }
                
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                    val locSpeedAcc = location.speedAccuracyMetersPerSecond
                    if (!locSpeedAcc.isNaN() && locSpeedAcc > 0f) {
                        speedAcc = locSpeedAcc
                    }
                }
            }
            
            // Se velocidade nativa não disponível ou zero, calcular manualmente como fallback
            if (speed == 0f) {
                val prev = lastLocation
                if (prev != null) {
                    val dtSec = (location.time - prev.time) / 1000f
                    if (dtSec > 0.1f) { // Pelo menos 100ms entre leituras
                        val dist = location.distanceTo(prev)
                        val calcSpeed = dist / dtSec
                        if (!calcSpeed.isNaN() && !calcSpeed.isInfinite() && calcSpeed >= 0f) {
                            speed = calcSpeed
                        }
                    }
                }
            }
            
            lastLocation = location

            Log.d("HWManager", "Emitting KinematicFix: speed=$speed m/s (${speed * 3.6f} km/h)")

            _latestFix.value = KinematicFix(
                latitude = location.latitude,
                longitude = location.longitude,
                speedMetersPerSecond = speed,
                speedAccuracyMetersPerSecond = speedAcc,
                timestampMs = System.currentTimeMillis()
            )
        } catch (e: Exception) {
            Log.e("HWManager", "Error processing location", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun startAcquisition(scope: CoroutineScope) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_NORMAL)

        // CRITICO: Usar Looper.getMainLooper() para garantir que os callbacks sejam entregues.
        // Sem Looper explícito, o LocationManager pode não entregar callbacks em threads de background.
        val mainLooper = Looper.getMainLooper()

        // Tentar GPS_PROVIDER (mais preciso, tem velocidade nativa)
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER, 
                    1000L, 
                    0f, 
                    locationListener,
                    mainLooper
                )
                Log.d("HWManager", "GPS_PROVIDER ativado com sucesso")
            }
        } catch (e: Exception) {
            Log.e("HWManager", "Erro ao ativar GPS_PROVIDER", e)
        }

        // TAMBÉM registrar no NETWORK_PROVIDER como complemento (fix mais rápido em ambientes fechados)
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER, 
                    2000L, 
                    0f, 
                    locationListener,
                    mainLooper
                )
                Log.d("HWManager", "NETWORK_PROVIDER ativado com sucesso")
            }
        } catch (e: Exception) {
            Log.e("HWManager", "Erro ao ativar NETWORK_PROVIDER", e)
        }

        // Tentar pegar última localização conhecida para ter um fix imediato na tela
        try {
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            
            if (lastKnown != null) {
                Log.d("HWManager", "Usando última localização conhecida: lat=${lastKnown.latitude}, lon=${lastKnown.longitude}")
                lastLocation = lastKnown
                _latestFix.value = KinematicFix(
                    latitude = lastKnown.latitude,
                    longitude = lastKnown.longitude,
                    speedMetersPerSecond = if (lastKnown.hasSpeed()) lastKnown.speed else 0f,
                    speedAccuracyMetersPerSecond = 2.0f,
                    timestampMs = System.currentTimeMillis()
                )
            }
        } catch (e: Exception) {
            Log.e("HWManager", "Erro ao obter última localização", e)
        }
    }

    fun stopAcquisition() {
        sensorManager.unregisterListener(sensorListener)
        locationManager.removeUpdates(locationListener)
    }
}
