package com.example.pavementquality.domain

/**
 * Resultado da reconciliação de dados.
 */
data class ReconciledSample(
    val reconciledAz: Float,
    val reconciledV: Float,
    val residual: Float,
    val normalizedResidual: Float,
    val isGrossError: Boolean
)

/**
 * Reconciliador Matemático usando Mínimos Quadrados Ponderados (MQP).
 * Restrição Física do Domínio: O impacto vertical na suspensão (az) é proporcional à velocidade (v).
 * Equação do modelo f(y) = 0: a_z - k * v = 0
 */
class Reconciler(
    private val k: Float = 0.5f, 
    private val chiSquareThreshold: Float = 3.84f // Limiar para 95% de confiança (1 grau de liberdade)
) {
    
    // Mapeia o "status de acurácia" nativo do Android para uma variância (sigma^2) em m/s^2.
    private fun getAccelVariance(accuracy: Int): Float {
        return when (accuracy) {
            3 -> 0.5f * 0.5f   // HIGH
            2 -> 1.0f * 1.0f   // MEDIUM
            1 -> 2.0f * 2.0f   // LOW
            else -> 5.0f * 5.0f // UNRELIABLE
        }
    }
    
    @Synchronized
    fun reconcile(vibration: VibrationSample, fix: KinematicFix): ReconciledSample {
        val yAz = vibration.zAcceleration
        val yV = fix.speedMetersPerSecond
        
        // 1. Matriz de Covariância informada estritamente pelos sensores
        val varAz = getAccelVariance(vibration.accuracy)
        // A acurácia da velocidade do GPS no Android já é o desvio padrão em m/s
        val varV = fix.speedAccuracyMetersPerSecond * fix.speedAccuracyMetersPerSecond
        
        // 2. Cálculo do Resíduo (Restrição: az - kv = 0)
        val r = yAz - (k * yV)
        
        // Variância do Resíduo: V_r = A * V * A^T
        val varR = varAz + (k * k * varV)
        
        // 3. Teste Global (Detecção de Erro Grosseiro)
        val zScore = (r * r) / varR
        val isGrossError = zScore > chiSquareThreshold
        
        // 4. Mínimos Quadrados Ponderados (Correção das leituras)
        val correctionFactor = r / varR
        
        val reconciledAz = yAz - (varAz * 1f * correctionFactor)
        val reconciledV = yV - (varV * (-k) * correctionFactor)
        
        return ReconciledSample(
            reconciledAz = reconciledAz,
            reconciledV = reconciledV,
            residual = r,
            normalizedResidual = zScore,
            isGrossError = isGrossError
        )
    }
}
