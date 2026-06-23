package com.mdm.assistant.repository;

import com.mdm.assistant.entity.ChatHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatHistoryRepository extends JpaRepository<ChatHistoryEntity, Long> {
    List<ChatHistoryEntity> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<ChatHistoryEntity> findByUserIdAndSessionId(Long userId, String sessionId);
    void deleteByUserIdAndSessionId(Long userId, String sessionId);
    void deleteByUserId(Long userId);
}