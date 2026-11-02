package com.example.pavementquality.domain

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

/**
 * Capacidade 5: Leitor e Reprodutor de Telemetria para Ensaios Reprodutíveis.
 * 
 * Lê arquivos de log CSV gravados pelo LogRecorder e os transforma de volta
 * em sequências ordenadas de VibrationSample e KinematicFix para testes determinísticos.
 */
class TelemetryReplay {

    data class TelemetryFrame(
        val vibration: VibrationSample,
        val fix: KinematicFix
    )

    fun parseCsv(inputStream: InputStream): List<TelemetryFrame> {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val frames = mutableListOf<TelemetryFrame>()
        
        var isHeader = true
        reader.forEachLine { line ->
            if (isHeader) {
                isHeader = false
                return@forEachLine
            }
            if (line.isBlank()) return@forEachLine
            
            val tokens = line.split(",")
            if (tokens.size >= 7) {
                try {
                    val timestamp = tokens[0].trim().toLong()
                    val zAccel = tokens[1].trim().toFloat()
                    val accuracy = tokens[2].trim().toInt()
                    val lat = tokens[3].trim().toDouble()
                    val lon = tokens[4].trim().toDouble()
                    val speed = tokens[5].trim().toFloat()
                    val speedAcc = tokens[6].trim().toFloat()

                    val validLat = if (lat in -90.0..90.0) lat else 0.0
                    val validLon = if (lon in -180.0..180.0) lon else 0.0
                    val validSpeed = if (speed >= 0f) speed else 0f
                    val validSpeedAcc = if (speedAcc > 0f) speedAcc else 1.0f

                    val vib = VibrationSample(zAcceleration = zAccel, accuracy = accuracy, timestampMs = timestamp)
                    val fix = KinematicFix(
                        latitude = validLat,
                        longitude = validLon,
                        speedMetersPerSecond = validSpeed,
                        speedAccuracyMetersPerSecond = validSpeedAcc,
                        timestampMs = timestamp
                    )
                    frames.add(TelemetryFrame(vib, fix))
                } catch (e: Exception) {
                    // Ignora linhas com formatação corrompida
                }
            }
        }
        return frames
    }
}
