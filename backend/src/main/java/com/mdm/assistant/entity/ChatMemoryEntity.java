package com.mdm.assistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "chat_memory")
public class ChatMemoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "memory_id", nullable = false, length = 100)
    private String memoryId;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Column(name = "messages_json", nullable = false, columnDefinition = "TEXT")
    private String messagesJson;

    @Column(name = "update_time")
    private LocalDateTime updateTime;

    public ChatMemoryEntity() {
    }

    public ChatMemoryEntity(String memoryId, String sessionId, String messagesJson) {
        this.memoryId = memoryId;
        this.sessionId = sessionId;
        this.messagesJson = messagesJson;
        this.updateTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getMemoryId() {
        return memoryId;
    }

    public void setMemoryId(String memoryId) {
        this.memoryId = memoryId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessagesJson() {
        return messagesJson;
    }

    public void setMessagesJson(String messagesJson) {
        this.messagesJson = messagesJson;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}
