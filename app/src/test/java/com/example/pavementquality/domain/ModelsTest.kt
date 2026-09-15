package com.example.pavementquality.domain

import org.junit.Assert.*
import org.junit.Test

class ModelsTest {

    @Test
    fun `KinematicFix rejects negative speed`() {
        assertThrows(DomainConstraintException::class.java) {
            KinematicFix(0.0, 0.0, -5f, 1f, 1000L)
        }
    }

    @Test
    fun `KinematicFix rejects zero or negative accuracy`() {
        assertThrows(DomainConstraintException::class.java) {
            KinematicFix(0.0, 0.0, 10f, 0f, 1000L)
        }
    }

    @Test
    fun `TrackRecord maintains constant memory`() {
        val track = TrackRecord(3)
        track.addSample(VibrationSample(1f, 1, 100))
        track.addSample(VibrationSample(2f, 1, 200))
        track.addSample(VibrationSample(3f, 1, 300))
        track.addSample(VibrationSample(4f, 1, 400))
        
        assertEquals(3, track.size())
        
        val window = track.getSamplesInWindow(200, 400)
        assertEquals(3, window.size)
        assertEquals(2f, window[0].zAcceleration)
    }

    @Test
    fun `TrackRecord rejects backwards timestamps`() {
        val track = TrackRecord(5)
        track.addSample(VibrationSample(1f, 1, 200))
        assertThrows(DomainConstraintException::class.java) {
            track.addSample(VibrationSample(2f, 1, 100))
        }
    }
}
