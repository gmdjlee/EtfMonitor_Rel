package com.etfmonitor.core.network.ai

import kotlinx.serialization.Serializable

/**
 * AI 모델 정보
 */
@Serializable
data class AIModel(
    val id: String,
    val name: String,
    val provider: AIProvider,
    val description: String? = null,
    val contextWindow: Int? = null,
    val maxOutputTokens: Int? = null
) {
    /**
     * 표시용 이름
     */
    fun displayName(): String {
        return name.ifBlank { id }
    }
}

/**
 * 모델 목록 조회 결과
 */
data class ModelsListResult(
    val models: List<AIModel>,
    val error: String? = null
)
