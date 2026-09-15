package com.example.pavementquality.domain

import com.example.pavementquality.data.HardwareSensorManager
import com.example.pavementquality.data.LogRecorder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull

class Engine(
    private val hardwareManager: HardwareSensorManager,
    private val trackRecord: TrackRecord,
    private val reconciler: Reconciler,
    private val logRecorder: LogRecorder
) {
    private var job: Job? = null
    
    private val _latestReconciliation = MutableStateFlow<ReconciledSample?>(null)
    val latestReconciliation: StateFlow<ReconciledSample?> = _latestReconciliation

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _lastLogMessage = MutableStateFlow<String?>("Nenhum log gravado ainda.")
    val lastLogMessage: StateFlow<String?> = _lastLogMessage

    fun start(scope: CoroutineScope) {
        hardwareManager.startAcquisition(scope)
        
        job = scope.launch(Dispatchers.Default) {
            hardwareManager.latestVibration
                .filterNotNull()
                .collect { vib ->
                    // Exige que o GPS tenha fixado para rodar o MQP (Capacidade 2 requer Z + Velocidade)
                    val fix = hardwareManager.latestFix.value
                    
                    if (fix != null) {
                        trackRecord.addSample(vib)
                        val result = reconciler.reconcile(vib, fix)
                        _latestReconciliation.value = result

                        if (_isRecording.value) {
                            logRecorder.appendData(vib, fix)
                        }
                    }
                }
        }
    }
    
    fun stop() {
        hardwareManager.stopAcquisition()
        if (_isRecording.value) toggleRecording()
        job?.cancel()
    }

    fun toggleRecording() {
        if (_isRecording.value) {
            _lastLogMessage.value = logRecorder.stopRecording()
            _isRecording.value = false
        } else {
            _lastLogMessage.value = logRecorder.startRecording()
            _isRecording.value = true
        }
    }
}
