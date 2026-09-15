package com.example.pavementquality.domain

/**
 * Mantém um histórico temporal limitado em memória constante, 
 * servindo como o "Buffer Circular" exigido na Capacidade 3.
 */
class TrackRecord(private val capacity: Int) {
    init {
        if (capacity <= 0) throw DomainConstraintException("Capacidade deve ser maior que zero")
    }
    
    // Lista mutável interna (protegida e privada)
    private val samples = ArrayDeque<VibrationSample>(capacity)

    @Synchronized
    fun addSample(sample: VibrationSample) {
        if (samples.isNotEmpty() && sample.timestampMs < samples.last().timestampMs) {
            throw DomainConstraintException("Timestamps nao podem retroceder")
        }
        if (samples.size >= capacity) {
            samples.removeFirst()
        }
        samples.addLast(sample)
    }

    /**
     * Retorna amostras dentro de uma janela de tempo.
     * CÓPIA DEFENSIVA: Em Kotlin, o método .filter() já gera e retorna
     * uma nova coleção (ArrayList) na memória. Assim, a thread que 
     * consome os dados não afetará a thread que os insere.
     */
    @Synchronized
    fun getSamplesInWindow(startTimestampMs: Long, endTimestampMs: Long): List<VibrationSample> {
        if (startTimestampMs > endTimestampMs) throw DomainConstraintException("Janela de tempo invalida")
        return samples.filter { it.timestampMs in startTimestampMs..endTimestampMs }
    }
    
    /**
     * Retorna todo o conteúdo do buffer como uma CÓPIA DEFENSIVA explícita.
     */
    @Synchronized
    fun getAllSamplesDefensiveCopy(): List<VibrationSample> {
        // .toList() gera uma cópia defensiva imutável do ArrayDeque original
        return samples.toList()
    }

    @Synchronized
    fun size(): Int = samples.size
}
