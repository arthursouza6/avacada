package com.example.pavementquality.data

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.example.pavementquality.domain.KinematicFix
import com.example.pavementquality.domain.LogEntry
import com.example.pavementquality.domain.VibrationSample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LogRecorder(private val context: Context) {
    
    private val prefs = context.getSharedPreferences("pavement_log_history", Context.MODE_PRIVATE)
    private val _logHistory = MutableStateFlow<List<LogEntry>>(loadHistory())
    val logHistory: StateFlow<List<LogEntry>> = _logHistory

    private var fileWriter: OutputStreamWriter? = null
    private var currentFileName: String? = null
    private var currentUri: Uri? = null
    private var sampleCount = 0
    private var startTimeMs = 0L

    private fun loadHistory(): List<LogEntry> {
        val jsonString = prefs.getString("history_entries", null) ?: return emptyList()
        return try {
            val array = JSONArray(jsonString)
            val list = mutableListOf<LogEntry>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    LogEntry(
                        filename = obj.optString("filename", "log.csv"),
                        savedAt = obj.optString("savedAt", ""),
                        sampleCount = obj.optInt("sampleCount", 0),
                        durationFormatted = obj.optString("durationFormatted", "")
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveEntry(entry: LogEntry) {
        val current = _logHistory.value.toMutableList()
        current.add(0, entry)
        if (current.size > 20) {
            current.removeAt(current.lastIndex)
        }
        _logHistory.value = current

        try {
            val array = JSONArray()
            for (item in current) {
                val obj = JSONObject().apply {
                    put("filename", item.filename)
                    put("savedAt", item.savedAt)
                    put("sampleCount", item.sampleCount)
                    put("durationFormatted", item.durationFormatted)
                }
                array.put(obj)
            }
            prefs.edit().putString("history_entries", array.toString()).apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun startRecording(): String {
        return try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val filename = "pavement_log_$timestamp.csv"
            currentFileName = filename
            sampleCount = 0
            startTimeMs = System.currentTimeMillis()

            val outputStream: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/csv")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    ?: throw IllegalStateException("Falha ao criar entrada no MediaStore")
                currentUri = uri
                context.contentResolver.openOutputStream(uri, "w")
            } else {
                @Suppress("DEPRECATION")
                val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, filename)
                FileOutputStream(file, false)
            }

            if (outputStream == null) {
                throw IllegalStateException("Não foi possível abrir o stream de gravação")
            }

            fileWriter = OutputStreamWriter(outputStream)
            // Escreve o cabeçalho
            fileWriter?.write("timestamp,z_accel,accuracy,lat,lon,speed,speed_acc\n")
            fileWriter?.flush()
            
            "Gravação iniciada em Downloads: $filename"
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
            
            val name = currentFileName ?: "desconhecido"
            val durationSec = ((System.currentTimeMillis() - startTimeMs) / 1000L).coerceAtLeast(0L)
            val durationStr = if (durationSec < 60) "${durationSec}s" else "${durationSec / 60}m ${durationSec % 60}s"
            val timeFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())

            val entry = LogEntry(
                filename = name,
                savedAt = timeFormat.format(Date()),
                sampleCount = sampleCount,
                durationFormatted = durationStr
            )
            saveEntry(entry)

            val count = sampleCount
            currentFileName = null
            currentUri = null
            sampleCount = 0
            "Gravação finalizada em Downloads: $name ($count amostras)"
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
                sampleCount++
                if (sampleCount % 50 == 0) {
                    fileWriter?.flush()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

