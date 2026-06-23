package com.mdm.assistant.repository;

import com.mdm.assistant.entity.ChatMessageEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 聊天消息 Repository - 提供对话历史的各种查询方法
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, Long> {

    /**
     * 查询指定用户和会话的所有消息（按时间升序）
     */
    List<ChatMessageEntity> findByUserIdAndSessionIdOrderByCreatedAtAsc(Long userId, String sessionId);

    /**
     * 查询指定用户和会话的所有消息（按时间降序）
     */
    List<ChatMessageEntity> findByUserIdAndSessionIdOrderByCreatedAtDesc(Long userId, String sessionId);

    /**
     * 查询指定会话的所有消息（按时间升序）
     */
    List<ChatMessageEntity> findBySessionIdOrderByCreatedAtAsc(String sessionId);

    /**
     * 查询指定用户的所有会话ID（按最新消息时间降序）
     */
    @Query("SELECT DISTINCT m.sessionId FROM ChatMessageEntity m WHERE m.userId = :userId ORDER BY m.createdAt DESC")
    List<String> findDistinctSessionIdsByUserIdOrderByCreatedAtDesc(@Param("userId") Long userId);

    /**
     * 查询指定用户最近N条消息（使用分页）
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.userId = :userId ORDER BY m.createdAt DESC")
    List<ChatMessageEntity> findRecentMessagesByUserId(@Param("userId") Long userId, Pageable pageable);

    /**
     * 删除指定用户和会话的所有消息
     */
    void deleteByUserIdAndSessionId(Long userId, String sessionId);

    /**
     * 删除指定用户的所有消息
     */
    void deleteByUserId(Long userId);

    /**
     * 查询指定会话的消息数量
     */
    long countBySessionId(String sessionId);

    /**
     * 查询指定用户和会话的最新消息时间
     */
    @Query("SELECT MAX(m.createdAt) FROM ChatMessageEntity m WHERE m.userId = :userId AND m.sessionId = :sessionId")
    LocalDateTime findLatestMessageTime(@Param("userId") Long userId, @Param("sessionId") String sessionId);

    /**
     * 查询指定用户的所有会话概览（会话ID、消息数量、最新消息时间）
     */
    @Query("SELECT m.sessionId, COUNT(m), MAX(m.createdAt) FROM ChatMessageEntity m WHERE m.userId = :userId GROUP BY m.sessionId ORDER BY MAX(m.createdAt) DESC")
    List<Object[]> findSessionSummariesByUserId(@Param("userId") Long userId);

    /**
     * 查询指定会话中用户的消息（用于提取用户询问的设备）
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.sessionId = :sessionId AND m.role = 'user' ORDER BY m.createdAt DESC")
    List<ChatMessageEntity> findUserMessagesBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询指定会话中助手推荐的消息
     */
    @Query("SELECT m FROM ChatMessageEntity m WHERE m.sessionId = :sessionId AND m.role = 'assistant' AND m.isRecommendation = true ORDER BY m.createdAt DESC")
    List<ChatMessageEntity> findRecommendationMessagesBySessionId(@Param("sessionId") String sessionId);

    /**
     * 查询指定会话的最新一条消息
     */
    Optional<ChatMessageEntity> findFirstBySessionIdOrderByCreatedAtDesc(String sessionId);

    /**
     * 查询指定用户和会话的最新一条消息
     */
    Optional<ChatMessageEntity> findFirstByUserIdAndSessionIdOrderByCreatedAtDesc(Long userId, String sessionId);
}