package com.etfmonitor.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.etfmonitor.database.entities.AIChatMessage
import com.etfmonitor.database.entities.AIChatSession
import kotlinx.coroutines.flow.Flow

/**
 * AI 채팅 DAO
 */
@Dao
interface AIChatDao {

    // ========== 세션 관련 ==========

    /**
     * 모든 세션 조회 (최신순)
     */
    @Query("SELECT * FROM ai_chat_session ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<AIChatSession>>

    /**
     * 세션 ID로 조회
     */
    @Query("SELECT * FROM ai_chat_session WHERE id = :sessionId")
    suspend fun getSessionById(sessionId: String): AIChatSession?

    /**
     * 특정 시장 관련 세션 조회
     */
    @Query("SELECT * FROM ai_chat_session WHERE market = :market ORDER BY updatedAt DESC")
    fun getSessionsByMarket(market: String): Flow<List<AIChatSession>>

    /**
     * 최근 N개 세션 조회
     */
    @Query("SELECT * FROM ai_chat_session ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentSessions(limit: Int): Flow<List<AIChatSession>>

    /**
     * 세션 생성
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: AIChatSession)

    /**
     * 세션 업데이트
     */
    @Update
    suspend fun updateSession(session: AIChatSession)

    /**
     * 세션 삭제
     */
    @Query("DELETE FROM ai_chat_session WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: String)

    /**
     * 모든 세션 삭제
     */
    @Query("DELETE FROM ai_chat_session")
    suspend fun deleteAllSessions()

    /**
     * 세션 수
     */
    @Query("SELECT COUNT(*) FROM ai_chat_session")
    suspend fun getSessionCount(): Int

    // ========== 메시지 관련 ==========

    /**
     * 특정 세션의 모든 메시지 조회 (시간순)
     */
    @Query("SELECT * FROM ai_chat_message WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesBySession(sessionId: String): Flow<List<AIChatMessage>>

    /**
     * 특정 세션의 메시지 목록 조회 (suspend)
     */
    @Query("SELECT * FROM ai_chat_message WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getMessagesBySessionSuspend(sessionId: String): List<AIChatMessage>

    /**
     * 특정 세션의 최근 N개 메시지 조회 (컨텍스트용)
     */
    @Query("SELECT * FROM ai_chat_message WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(sessionId: String, limit: Int): List<AIChatMessage>

    /**
     * 메시지 ID로 조회
     */
    @Query("SELECT * FROM ai_chat_message WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): AIChatMessage?

    /**
     * 특정 분석 결과와 연결된 메시지 조회
     */
    @Query("SELECT * FROM ai_chat_message WHERE analysisResultId = :analysisId ORDER BY timestamp ASC")
    suspend fun getMessagesByAnalysisId(analysisId: String): List<AIChatMessage>

    /**
     * 메시지 생성
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: AIChatMessage)

    /**
     * 여러 메시지 생성
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<AIChatMessage>)

    /**
     * 메시지 삭제
     */
    @Query("DELETE FROM ai_chat_message WHERE id = :messageId")
    suspend fun deleteMessage(messageId: String)

    /**
     * 특정 세션의 모든 메시지 삭제
     */
    @Query("DELETE FROM ai_chat_message WHERE sessionId = :sessionId")
    suspend fun deleteMessagesBySession(sessionId: String)

    /**
     * 모든 메시지 삭제
     */
    @Query("DELETE FROM ai_chat_message")
    suspend fun deleteAllMessages()

    /**
     * 특정 세션의 메시지 수
     */
    @Query("SELECT COUNT(*) FROM ai_chat_message WHERE sessionId = :sessionId")
    suspend fun getMessageCount(sessionId: String): Int

    /**
     * 특정 세션의 마지막 메시지 조회
     */
    @Query("SELECT * FROM ai_chat_message WHERE sessionId = :sessionId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessage(sessionId: String): AIChatMessage?

    /**
     * 세션 제목 업데이트
     */
    @Query("UPDATE ai_chat_session SET title = :title, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionTitle(sessionId: String, title: String, updatedAt: Long = System.currentTimeMillis())

    /**
     * 세션 메시지 수 업데이트
     */
    @Query("UPDATE ai_chat_session SET messageCount = :count, updatedAt = :updatedAt WHERE id = :sessionId")
    suspend fun updateSessionMessageCount(sessionId: String, count: Int, updatedAt: Long = System.currentTimeMillis())
}
