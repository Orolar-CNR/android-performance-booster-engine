package com.example.systembooster.telemetry

import com.example.systembooster.model.EngineMetrics

interface TelemetryService {
    fun record(engineId: String, executionId: String, metrics: EngineMetrics)
}

class ConsoleTelemetryService : TelemetryService {
    private val records = mutableListOf<TelemetryRecord>()

    data class TelemetryRecord(
        val engineId: String,
        val executionId: String,
        val metrics: EngineMetrics
    )

    override fun record(engineId: String, executionId: String, metrics: EngineMetrics) {
        records.add(TelemetryRecord(engineId, executionId, metrics))
        println("[Telemetry] Engine '$engineId' (Execution ID: $executionId) recorded metrics: $metrics")
    }

    fun getRecords(): List<TelemetryRecord> = records.toList()
}
