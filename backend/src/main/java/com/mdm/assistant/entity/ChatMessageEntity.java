package com.mdm.assistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * 聊天消息实体类 - 存储每条单独的对话消息
 * 每个对话框（session）的消息独立存储，便于检索和管理
 */
@Entity
@Table(name = "chat_message", indexes = {
    @Index(name = "idx_user_session", columnList = "user_id,session_id"),
    @Index(name = "idx_session_time", columnList = "session_id,created_at")
})
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "role", nullable = false, length = 20)
    private String role;  // "user" 或 "assistant"

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "intent", length = 50)
    private String intent;  // 消息意图类型

    @Column(name = "mentioned_devices", length = 500)
    private String mentionedDevices;  // 提到的设备名（逗号分隔）

    @Column(name = "is_recommendation")
    private Boolean isRecommendation;  // 是否是推荐消息

    @Column(name = "recommendation_devices", length = 500)
    private String recommendationDevices;  // 推荐的设备名（逗号分隔）

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public ChatMessageEntity() {
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }

    public String getMentionedDevices() { return mentionedDevices; }
    public void setMentionedDevices(String mentionedDevices) { this.mentionedDevices = mentionedDevices; }

    public Boolean getIsRecommendation() { return isRecommendation; }
    public void setIsRecommendation(Boolean isRecommendation) { this.isRecommendation = isRecommendation; }

    public String getRecommendationDevices() { return recommendationDevices; }
    public void setRecommendationDevices(String recommendationDevices) { this.recommendationDevices = recommendationDevices; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}