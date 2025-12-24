package com.etfmonitor.core.network.ai

/**
 * AI API 제공자 (Claude, Gemini 등)
 */
enum class AIProvider {
    CLAUDE,
    GEMINI;

    fun toDisplayName(): String = when (this) {
        CLAUDE -> "Claude (Anthropic)"
        GEMINI -> "Gemini (Google)"
    }

    companion object {
        fun fromString(value: String): AIProvider {
            return when (value.uppercase()) {
                "CLAUDE" -> CLAUDE
                "GEMINI" -> GEMINI
                else -> CLAUDE // 기본값
            }
        }
    }
}
