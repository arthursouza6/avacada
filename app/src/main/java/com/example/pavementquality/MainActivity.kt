package com.example.pavementquality

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.pavementquality.data.HardwareSensorManager
import com.example.pavementquality.domain.Engine
import com.example.pavementquality.service.AcquisitionService
import com.example.pavementquality.theme.PavementQualityTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as PavementApp
        
        setContent {
            PavementQualityTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    DashboardScreen(
                        app.hardwareManager, 
                        app.engine,
                        onStartService = { startAcquisitionService() },
                        onStopService = { stopAcquisitionService() }
                    )
                }
            }
        }
    }

    private fun startAcquisitionService() {
        val intent = Intent(this, AcquisitionService::class.java)
        startForegroundService(intent)
    }

    private fun stopAcquisitionService() {
        val intent = Intent(this, AcquisitionService::class.java).apply {
            action = "STOP"
        }
        startService(intent)
    }
}

@Composable
fun DashboardScreen(
    hardwareManager: HardwareSensorManager, 
    engine: Engine,
    onStartService: () -> Unit,
    onStopService: () -> Unit
) {
    val vib by hardwareManager.latestVibration.collectAsState(initial = null)
    val recon by engine.latestReconciliation.collectAsState(initial = null)
    
    val isRecording by engine.isRecording.collectAsState()
    val logMessage by engine.lastLogMessage.collectAsState()

    var isEngineRunning by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.padding(16.dp).fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Qualidade de Pavimento", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = {
                    if (isEngineRunning) {
                        onStopService()
                    } else {
                        onStartService()
                    }
                    isEngineRunning = !isEngineRunning
                },
                modifier = Modifier.weight(1f).height(60.dp),
                colors = ButtonDefaults.buttonColors(containerColor = if (isEngineRunning) Color(0xFFD32F2F) else Color(0xFF388E3C))
            ) {
                Text(text = if (isEngineRunning) "PARAR" else "LIGAR", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { engine.toggleRecording() },
                modifier = Modifier.weight(1f).height(60.dp),
                enabled = isEngineRunning, // Só pode gravar se o motor estiver ligado
                colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color(0xFFB71C1C) else Color(0xFF1976D2))
            ) {
                Text(text = if (isRecording) "🔴 GRAVANDO..." else "GRAVAR TRAJETO", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = logMessage ?: "", fontSize = 12.sp, color = Color.Gray, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(16.dp))

        SensorCard(
            title = "Acelerômetro (Eixo Z Linear)",
            value = String.format("%.2f m/s²", vib?.zAcceleration ?: 0f),
            subValue = "Qualidade do Sensor: ${vib?.accuracy ?: 0}"
        )

        Spacer(modifier = Modifier.height(16.dp))
        
        val isPothole = recon?.isGrossError == true
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = if (isPothole) Color(0xFFFFCDD2) else Color(0xFFC8E6C9))
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Status da Via", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (isPothole) "🚨 BURACO DETECTADO!" else "✅ ASFALTO NORMAL",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isPothole) Color(0xFFB71C1C) else Color(0xFF1B5E20)
                )
                Text("Resíduo Global Janela (χ²): ${String.format("%.2f", recon?.normalizedResidual ?: 0f)}", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun SensorCard(title: String, value: String, subValue: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = subValue, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
