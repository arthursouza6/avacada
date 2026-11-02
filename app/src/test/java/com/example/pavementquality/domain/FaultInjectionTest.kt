package com.example.pavementquality.domain

import org.junit.Assert.*
import org.junit.Test

/**
 * Capacidade 5: Teste Automatizado de Ensaio Reprodutível e Tabela de Sensibilidade.
 *
 * Atende ao critério de aceitação oficial da Capacidade 5:
 * "Semente fixa, reprodução determinística e tabela de sensibilidade da detecção."
 */
class FaultInjectionTest {

    private val seed = 42L
    private val injector = FaultInjector(seed = seed)
    private val reconciler = Reconciler(k = 0.5f, chiSquareThreshold = 3.84f)

    @Test
    fun `Deterministic reproduction with fixed seed produces identical results across runs`() {
        val baseSample = VibrationSample(zAcceleration = 10.0f, accuracy = 3, timestampMs = 1000L)
        
        // Rodada 1
        injector.reset()
        val noisy1 = injector.injectGaussianNoise(baseSample, sigma = 1.0f)
        
        // Rodada 2
        injector.reset()
        val noisy2 = injector.injectGaussianNoise(baseSample, sigma = 1.0f)
        
        assertEquals("A semente fixa deve gerar valores numericamente identicos", noisy1.zAcceleration, noisy2.zAcceleration, 0.00001f)
    }

    @Test
    fun `Sensitivity table test - evaluating gross error detection threshold across perturbations`() {
        // Veículo a 20 m/s (72 km/h). Resposta física esperada: az = 0.5 * 20 = 10.0 m/s²
        val normalVib = VibrationSample(zAcceleration = 10.0f, accuracy = 3, timestampMs = 1000L)
        val fix = KinematicFix(
            latitude = -21.22,
            longitude = -44.98,
            speedMetersPerSecond = 20.0f,
            speedAccuracyMetersPerSecond = 1.0f,
            timestampMs = 1000L
        )

        // 1. Linha de Base (Sem Falha)
        val baseline = reconciler.reconcile(normalVib, fix)
        assertFalse("Linha de base nao deve acusar erro grosseiro", baseline.isGrossError)
        assertEquals("Residuo normal deve ser zero", 0.0f, baseline.residual, 0.001f)

        // 2. Perturbações Injetadas e Avaliação de Sensibilidade
        val perturbations = listOf(
            0.5f * 9.8f to "0.5G (4.9 m/s²)",
            1.0f * 9.8f to "1.0G (9.8 m/s²)",
            2.0f * 9.8f to "2.0G (19.6 m/s²)"
        )

        perturbations.forEach { (deltaAz, label) ->
            val degradedSample = injector.injectPulse(normalVib, deltaAz)
            val result = reconciler.reconcile(degradedSample, fix)

            // Toda perturbação >= 0.5G (impacto automotivo real) deve violar a física e ser detectada
            assertTrue("Perturbação $label deve ser detectada como erro grosseiro", result.isGrossError)
            assertTrue("Gama deve ser maior que 3.84", result.normalizedResidual > 3.84f)
        }
    }
}
