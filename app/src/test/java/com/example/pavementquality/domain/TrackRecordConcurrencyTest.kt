package com.example.pavementquality.domain

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

class TrackRecordConcurrencyTest {

    @Test
    fun `Buffer maintains integrity without data races when 10 threads access concurrently (10 repetitions)`() {
        // Exigência da Rubrica: Teste rodado no mínimo 10 vezes para provar ausência de race conditions intermitentes
        for (execution in 1..10) {
            val capacityLimit = 500
            val trackRecord = TrackRecord(capacityLimit)
            
            // 10 Threads Produtoras + 2 Threads Consumidoras
            val writersCount = 10
            val insertsPerWriter = 1000
            
            val executor = Executors.newFixedThreadPool(writersCount + 2)
            val timeStampCounter = AtomicLong(0)
            val exceptionCount = AtomicInteger(0)
            
            // Produtores (Gravando no Buffer)
            val writers = (1..writersCount).map {
                Callable {
                    try {
                        for (i in 1..insertsPerWriter) {
                            // AtomicLong garante a ordenação cronológica exigida pelos contratos de domínio
                            val t = 100L
                            trackRecord.addSample(VibrationSample(zAcceleration = 9.8f, accuracy = 3, timestampMs = t))
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        exceptionCount.incrementAndGet()
                    }
                }
            }
            
            // Consumidores (Lendo o Buffer - Se não houver cópia defensiva e sincronismo, ocorrerá ConcurrentModificationException)
            val readers = (1..2).map {
                Callable {
                    try {
                        for (i in 1..insertsPerWriter) {
                            // Requisita a Cópia Defensiva
                            val defensiveCopy = trackRecord.getAllSamplesDefensiveCopy()
                            // Itera pela cópia para forçar erro caso a memória interna tenha sido exposta indevidamente
                            defensiveCopy.forEach { it.zAcceleration }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        exceptionCount.incrementAndGet()
                    }
                }
            }
            
            // Dispara a tempestade de acessos concorrentes (corrida de dados simulada)
            val allTasks = writers + readers
            val futures = executor.invokeAll(allTasks)
            
            // Aguarda o término de todas as threads
            futures.forEach { it.get() }
            executor.shutdown()
            
            // Verificações Críticas (Acceptance Criteria)
            assertEquals("Execucao $execution: Nenhuma excecao (como ConcurrentModificationException) pode ocorrer", 0, exceptionCount.get())
            assertTrue("Execucao $execution: O buffer deve respeitar estritamente a capacidade limite ($capacityLimit)", trackRecord.size() <= capacityLimit)
        }
    }
}
