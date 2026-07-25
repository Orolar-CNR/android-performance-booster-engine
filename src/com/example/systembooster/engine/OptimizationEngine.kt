package com.example.systembooster.engine

import com.example.systembooster.model.EngineCapability
import com.example.systembooster.model.EngineResult
import com.example.systembooster.model.PrivilegeLevel
import com.example.systembooster.model.RetryPolicy

interface OptimizationEngine {
    val id: String
    val version: String
    val displayName: String
    val capability: EngineCapability
    val requiredPrivilege: PrivilegeLevel
    val retryPolicy: RetryPolicy
    var executionId: String?

    suspend fun prepare()
    suspend fun execute(targetPackage: String): EngineResult
    suspend fun cleanup()
}

interface EngineRegistry {
    fun registeredEngines(): List<OptimizationEngine>
    fun getEnginesByCapability(capability: EngineCapability): List<OptimizationEngine>
}

class SimpleEngineRegistry(private val engines: List<OptimizationEngine>) : EngineRegistry {
    override fun registeredEngines(): List<OptimizationEngine> = engines

    override fun getEnginesByCapability(capability: EngineCapability): List<OptimizationEngine> {
        return engines.filter { it.capability == capability }
    }
}
