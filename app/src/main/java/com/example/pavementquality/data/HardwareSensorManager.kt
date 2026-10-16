package com.example.pavementquality.data

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.example.pavementquality.domain.KinematicFix
import com.example.pavementquality.domain.VibrationSample
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class GpsStatusInfo(
    val isGpsEnabled: Boolean = false,
    val totalSatellites: Int = 0,
    val satellitesUsedInFix: Int = 0,
    val statusMessage: String = "Iniciando GPS..."
)

/**
 * Capacidade 4: Gerenciador de Aquisição de Hardware real otimizado para operação Offline.
 * Usa Acelerômetro e chip GNSS nativo (GPS puro) para enviar dados ao Motor.
 */
class HardwareSensorManager(private val context: Context) {
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _latestVibration = MutableStateFlow<VibrationSample?>(null)
    val latestVibration: StateFlow<VibrationSample?> = _latestVibration

    private val _latestFix = MutableStateFlow<KinematicFix?>(null)
    val latestFix: StateFlow<KinematicFix?> = _latestFix

    private val _gpsStatus = MutableStateFlow(GpsStatusInfo())
    val gpsStatus: StateFlow<GpsStatusInfo> = _gpsStatus

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

    private var lastGpsLocation: Location? = null
    private var gnssStatusCallback: GnssStatus.Callback? = null

    private val locationListener = LocationListener { location ->
        try {
            // No modo offline, apenas eventos do provedor GPS puro contêm velocidade real por Doppler
            val provider = location.provider ?: ""
            Log.d("HWManager", "Location update de $provider: lat=${location.latitude}, lon=${location.longitude}, hasSpeed=${location.hasSpeed()}, speed=${location.speed}")

            var speed = 0f
            var speedAcc = if (location.hasAccuracy()) (location.accuracy * 0.1f).coerceAtLeast(0.5f) else 1.5f

            // 1. Tentar velocidade Doppler nativa do chip GPS
            if (location.hasSpeed()) {
                val locSpeed = location.speed
                if (!locSpeed.isNaN() && locSpeed >= 0f) {
                    speed = locSpeed
                }
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && location.hasSpeedAccuracy()) {
                    val locSpeedAcc = location.speedAccuracyMetersPerSecond
                    if (!locSpeedAcc.isNaN() && locSpeedAcc > 0f) {
                        speedAcc = locSpeedAcc
                    }
                }
            } else {
                // 2. Fallback de velocidade por distância SOMENTE se o hardware NÃO tiver velocidade nativa
                val prev = lastGpsLocation
                if (prev != null) {
                    val dtSec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
                        (location.elapsedRealtimeNanos - prev.elapsedRealtimeNanos) / 1_000_000_000f
                    } else {
                        (location.time - prev.time) / 1000f
                    }

                    if (dtSec in 0.5f..10.0f) {
                        val dist = location.distanceTo(prev)
                        // Zona morta (deadband): ignorar flutuações menores que a incerteza do GPS (evita velocidade fantasma quando parado)
                        val noiseMargin = if (location.hasAccuracy()) location.accuracy.coerceAtLeast(2.0f) else 3.0f
                        if (dist > noiseMargin) {
                            val calcSpeed = dist / dtSec
                            if (!calcSpeed.isNaN() && !calcSpeed.isInfinite() && calcSpeed >= 0f) {
                                speed = calcSpeed
                            }
                        } else {
                            speed = 0f
                        }
                    }
                }
            }

            lastGpsLocation = location

            Log.d("HWManager", "Emitting KinematicFix: speed=$speed m/s (${speed * 3.6f} km/h)")

            _latestFix.value = KinematicFix(
                latitude = location.latitude,
                longitude = location.longitude,
                speedMetersPerSecond = speed,
                speedAccuracyMetersPerSecond = speedAcc,
                timestampMs = System.currentTimeMillis()
            )

            // Atualiza status indicando recebimento de fix
            val current = _gpsStatus.value
            _gpsStatus.value = current.copy(
                isGpsEnabled = true,
                statusMessage = if (current.satellitesUsedInFix > 0) {
                    "🛰️ Fix Ativo: ${current.satellitesUsedInFix}/${current.totalSatellites} satélites"
                } else {
                    "🛰️ Fix Ativo (GPS Travado)"
                }
            )
        } catch (e: Exception) {
            Log.e("HWManager", "Error processing location", e)
        }
    }

    fun isGpsProviderEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    fun startAcquisition(scope: CoroutineScope) {
        val accel = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)
        sensorManager.registerListener(sensorListener, accel, SensorManager.SENSOR_DELAY_NORMAL)

        val mainLooper = Looper.getMainLooper()
        val gpsEnabled = isGpsProviderEnabled()

        _gpsStatus.value = GpsStatusInfo(
            isGpsEnabled = gpsEnabled,
            statusMessage = if (!gpsEnabled) "⚠️ GPS desativado nas configurações do tablet" else "⏳ Buscando satélites (necessário céu aberto offline)..."
        )

        // Registrar monitoramento de satélites GNSS (API 24+) para visibilidade offline
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                gnssStatusCallback = object : GnssStatus.Callback() {
                    override fun onSatelliteStatusChanged(status: GnssStatus) {
                        val total = status.satelliteCount
                        var used = 0
                        for (i in 0 until total) {
                            if (status.usedInFix(i)) used++
                        }
                        val isEnabled = isGpsProviderEnabled()
                        val msg = when {
                            !isEnabled -> "⚠️ GPS desativado nas configurações do tablet"
                            used >= 4 -> "🛰️ Fix Ativo: $used/$total satélites"
                            total > 0 -> "🛰️ $total satélites visíveis ($used no fix) - Aguardando travamento..."
                            else -> "⏳ Buscando satélites (necessário céu aberto)..."
                        }
                        _gpsStatus.value = GpsStatusInfo(
                            isGpsEnabled = isEnabled,
                            totalSatellites = total,
                            satellitesUsedInFix = used,
                            statusMessage = msg
                        )
                    }
                }
                locationManager.registerGnssStatusCallback(gnssStatusCallback!!, Handler(mainLooper))
                Log.d("HWManager", "GnssStatusCallback registrado com sucesso")
            } catch (e: Exception) {
                Log.e("HWManager", "Erro ao registrar GnssStatusCallback", e)
            }
        }

        // Tentar GPS_PROVIDER nativo (satélites diretos, essencial para offline)
        try {
            if (gpsEnabled) {
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

        // Tentar última localização conhecida APENAS se for fresca (< 30 segundos)
        try {
            val lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (lastKnown != null) {
                val ageMs = System.currentTimeMillis() - lastKnown.time
                if (ageMs in 0..30_000L) {
                    Log.d("HWManager", "Usando localização recente conhecida (${ageMs / 1000}s de idade)")
                    lastGpsLocation = lastKnown
                    val s = if (lastKnown.hasSpeed()) lastKnown.speed.coerceAtLeast(0f) else 0f
                    _latestFix.value = KinematicFix(
                        latitude = lastKnown.latitude,
                        longitude = lastKnown.longitude,
                        speedMetersPerSecond = s,
                        speedAccuracyMetersPerSecond = 2.0f,
                        timestampMs = System.currentTimeMillis()
                    )
                } else {
                    Log.d("HWManager", "lastKnownLocation descartada por ter ${ageMs / 1000}s de idade (desatualizada)")
                }
            }
        } catch (e: Exception) {
            Log.e("HWManager", "Erro ao obter última localização", e)
        }
    }

    fun stopAcquisition() {
        sensorManager.unregisterListener(sensorListener)
        locationManager.removeUpdates(locationListener)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && gnssStatusCallback != null) {
            try {
                locationManager.unregisterGnssStatusCallback(gnssStatusCallback!!)
            } catch (e: Exception) {
                Log.e("HWManager", "Erro ao desregistrar GnssStatusCallback", e)
            }
            gnssStatusCallback = null
        }
        lastGpsLocation = null
    }
}
