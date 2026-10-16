package com.example.pavementquality.data

import android.content.Context
import com.example.pavementquality.domain.KinematicFix
import com.example.pavementquality.domain.VibrationSample
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogRecorder(private val context: Context) {
    
    private var fileWriter: OutputStreamWriter? = null
    private var currentFile: File? = null

    fun startRecording(): String {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "pavement_log_$timestamp.csv"
            val file = File(context.getExternalFilesDir(null), filename)
            
            val fos = FileOutputStream(file, true)
            fileWriter = OutputStreamWriter(fos)
            
            // Escreve o cabeçalho
            fileWriter?.write("timestamp,z_accel,accuracy,lat,lon,speed,speed_acc\n")
            currentFile = file
            
            "Gravação iniciada: ${file.name}"
        } catch (e: Exception) {
            e.printStackTrace()
            "Erro ao iniciar gravação: ${e.message}"
        }
    }

    fun stopRecording(): String {
        return try {
            fileWriter?.flush()
            fileWriter?.close()
            fileWriter = null
            
            val name = currentFile?.name ?: "desconhecido"
            "Gravação finalizada: $name"
        } catch (e: Exception) {
            e.printStackTrace()
            "Erro ao parar gravação: ${e.message}"
        }
    }

    fun appendData(vib: VibrationSample, fix: KinematicFix) {
        try {
            if (fileWriter != null) {
                val line = "${vib.timestampMs},${vib.zAcceleration},${vib.accuracy},${fix.latitude},${fix.longitude},${fix.speedMetersPerSecond},${fix.speedAccuracyMetersPerSecond}\n"
                fileWriter?.write(line)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
