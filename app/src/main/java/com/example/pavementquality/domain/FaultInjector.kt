package com.example.pavementquality.domain

import java.util.Random

/**
 * Capacidade 5: Ensaio Reprodutível e Injeção de Falhas.
 *
 * Implementa a injeção determinística de anomalias (degradação controlada) com semente fixa,
 * permitindo avaliar a sensibilidade do detector de erro grosseiro do [Reconciler].
 *
 * Modelos de degradação suportados:
 * 1. Salto de Aceleração (Pulso mecânico / Buraco): adiciona amplitude deltaAz;
 * 2. Ruído Gaussiano: adiciona perturbação estocástica com variância sigma;
 * 3. Perda de Amostra (Drop): simula descarte temporário de medição.
 */
class FaultInjector(private val seed: Long = 42L) {

    private val random = Random(seed)

    /**
     * Injeta um pulso calibrado de impacto mecânico (buraco) em uma amostra.
     */
    fun injectPulse(sample: VibrationSample, deltaAz: Float): VibrationSample {
        return sample.copy(zAcceleration = sample.zAcceleration + deltaAz)
    }

    /**
     * Injeta ruído gaussiano controlado em uma amostra usando a semente fixa.
     */
    fun injectGaussianNoise(sample: VibrationSample, sigma: Float): VibrationSample {
        val noise = (random.nextGaussian() * sigma).toFloat()
        return sample.copy(zAcceleration = sample.zAcceleration + noise)
    }

    /**
     * Reseta a semente pseudo-aleatória para garantir reprodutibilidade idêntica entre ensaios.
     */
    fun reset() {
        random.setSeed(seed)
    }
}
