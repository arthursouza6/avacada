package com.example.pavementquality.domain

import org.junit.Assert.*
import org.junit.Test

class ReconcilerTest {

    private val reconciler = Reconciler(k = 0.5f, chiSquareThreshold = 3.84f)

    @Test
    fun `Reconciler uses sensor accuracy and strictly enforces physical constraint`() {
        // Cenário: Asfalto perfeitamente normal a 20 m/s (72 km/h).
        // Esperado az = k * v = 0.5 * 20 = 10.0
        val vib = VibrationSample(zAcceleration = 10.2f, accuracy = 3, timestampMs = 100)
        val fix = KinematicFix(0.0, 0.0, speedMetersPerSecond = 20f, speedAccuracyMetersPerSecond = 1.0f, timestampMs = 100)
        
        val result = reconciler.reconcile(vib, fix)
        
        // O resíduo (0.2) é muito pequeno comparado à covariância dos sensores. Não há erro grosseiro.
        assertFalse("Asfalto normal nao deve acusar erro grosseiro", result.isGrossError)
        
        // Prova matemática do MQP: Após reconciliar, as leituras devem obedecer estritamente f(y) = 0
        // a_hat - k * v_hat deve ser extremamente próximo de 0
        val reconciledResidual = result.reconciledAz - (0.5f * result.reconciledV)
        assertEquals(0.0f, reconciledResidual, 0.001f)
    }

    @Test
    fun `Gross error injected (Pothole at high speed) is mathematically detected`() {
        // Cenário: O veículo está a 20 m/s. Esperado az = 10.0.
        // O acelerômetro sofre um impacto gigantesco de 25.0 m/s^2 (Um buraco grande!)
        val vib = VibrationSample(zAcceleration = 25.0f, accuracy = 3, timestampMs = 100)
        val fix = KinematicFix(0.0, 0.0, speedMetersPerSecond = 20f, speedAccuracyMetersPerSecond = 1.0f, timestampMs = 100)
        
        val result = reconciler.reconcile(vib, fix)
        
        // O resíduo explode. O MQP identifica que isso não é ruído de medição, mas um evento anômalo (Erro Grosseiro).
        assertTrue("Buraco (erro grosseiro) deve ser detectado pela violacao do modelo fisico", result.isGrossError)
        assertTrue("Residuo normalizado deve ser maior que o limite chi-quadrado", result.normalizedResidual > 3.84f)
    }

    @Test
    fun `Manual tablet handling at zero speed causes model violation`() {
        // Cenário: Veículo parado (v=0). Esperado az = 0.
        // O usuário pega o tablet na mão e chacoalha, gerando az = 8.0.
        // Isso é uma violação física do modelo automotivo, e DEVE ser flagrado matematicamente como erro grosseiro.
        // (A lógica de classificar se foi 'buraco' ou 'manuseio' ficará para o Agente na Camada 3).
        val vib = VibrationSample(zAcceleration = 8.0f, accuracy = 3, timestampMs = 100)
        val fix = KinematicFix(0.0, 0.0, speedMetersPerSecond = 0f, speedAccuracyMetersPerSecond = 0.2f, timestampMs = 100)
        
        val result = reconciler.reconcile(vib, fix)
        
        assertTrue("Ruido na mao (com carro parado) gera violacao fisica e causa erro grosseiro", result.isGrossError)
    }
}
