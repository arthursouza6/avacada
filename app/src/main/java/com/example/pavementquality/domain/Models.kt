package com.example.pavementquality.domain

data class KinematicFix(
    val latitude: Double,
    val longitude: Double,
    val speedMetersPerSecond: Float,
    val speedAccuracyMetersPerSecond: Float,
    val timestampMs: Long
) {
    init {
        if (speedMetersPerSecond < 0f) throw DomainConstraintException("Velocidade negativa: $speedMetersPerSecond")
        if (speedAccuracyMetersPerSecond <= 0f) throw DomainConstraintException("Acuracia de velocidade invalida: $speedAccuracyMetersPerSecond")
        if (latitude < -90.0 || latitude > 90.0) throw DomainConstraintException("Latitude invalida: $latitude")
        if (longitude < -180.0 || longitude > 180.0) throw DomainConstraintException("Longitude invalida: $longitude")
    }
}

data class VibrationSample(
    val zAcceleration: Float,
    val accuracy: Int,
    val timestampMs: Long
) {
    init {
        if (accuracy < 0) throw DomainConstraintException("Acuracia do sensor invalida: $accuracy")
    }
}
