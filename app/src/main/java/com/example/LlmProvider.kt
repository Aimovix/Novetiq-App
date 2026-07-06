package com.example

import kotlinx.coroutines.flow.Flow

interface LlmProvider {
    val name: String
    val isLocal: Boolean
    
    suspend fun generateCommand(prompt: String): Flow<String>
}

// Dummy implementation for the Setup Wizard visualization
data class LocalModelInfo(
    val id: String,
    val name: String,
    val description: String,
    val ramRequirement: String,
    val parameters: String
)

object LocalModels {
    val AVAILABLE_MODELS = listOf(
        LocalModelInfo(
            id = "gemma_2_2b",
            name = "Gemma 2 (2B)",
            description = "Unsere Empfehlung für Android. Sehr gutes Verhältnis aus Leistung und Verbrauch.",
            ramRequirement = "Mind. 4GB RAM",
            parameters = "2B"
        ),
        LocalModelInfo(
            id = "qwen_2_5_1_5b",
            name = "Qwen 2.5 (1.5B)",
            description = "Sehr schnell und leichtgewichtig.",
            ramRequirement = "Mind. 4GB RAM",
            parameters = "1.5B"
        ),
        LocalModelInfo(
            id = "phi_3_mini",
            name = "Phi-3 Mini (3.8B)",
            description = "Starke Performance, benötigt aber neuere Hardware.",
            ramRequirement = "Mind. 6GB RAM (Snapdragon 8 Gen 1+)",
            parameters = "3.8B"
        ),
        LocalModelInfo(
            id = "llama_3_1_8b",
            name = "Llama 3.1 (8B)",
            description = "High-End Warnung! Benötigt sehr viel Speicher.",
            ramRequirement = "Mind. 8-12GB RAM",
            parameters = "8B"
        )
    )
}
