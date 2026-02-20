package com.etfmonitor.core.database.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * AI 채팅 메시지 엔티티
 * 사용자와 AI 간의 대화 이력 저장
 */
@Entity(
    tableName = "ai_chat_message",
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class AIChatMessage(
    @PrimaryKey
    val id: String, // UUID

    val sessionId: String, // 대화 세션 ID (UUID)

    val role: String, // "user" 또는 "assistant"

    val content: String, // 메시지 내용

    // 연결된 분석 결과 (optional)
    val analysisResultId: String?, // AI 분석 결과 참조

    // 메타데이터
    val aiProvider: String?, // "CLAUDE" 또는 "GEMINI" (assistant 메시지만)
    val aiModel: String?, // 사용된 모델 ID (assistant 메시지만)
    val tokenCount: Int?, // 토큰 수 (assistant 메시지만)

    val timestamp: Long = System.currentTimeMillis()
)

/**
 * AI 채팅 세션 엔티티
 * 대화 세션 관리
 */
@Entity(
    tableName = "ai_chat_session",
    indices = [Index(value = ["market"])]
)
data class AIChatSession(
    @PrimaryKey
    val id: String, // UUID

    val title: String, // 세션 제목 (첫 질문 요약 또는 자동 생성)

    val market: String?, // 관련 시장 "KOSPI" 또는 "KOSDAQ"

    val analysisDate: String?, // 관련 분석 날짜

    // 세션에 포함된 분석 컨텍스트 (JSON)
    val contextData: String?, // 분석 데이터 요약

    val messageCount: Int = 0, // 메시지 수

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
