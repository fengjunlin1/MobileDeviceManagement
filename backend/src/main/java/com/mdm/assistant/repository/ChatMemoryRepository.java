package com.mdm.assistant.repository;

import com.mdm.assistant.entity.ChatMemoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatMemoryRepository extends JpaRepository<ChatMemoryEntity, Long> {
    Optional<ChatMemoryEntity> findByMemoryId(String memoryId);
    Optional<ChatMemoryEntity> findByUserIdAndMemoryId(Long userId, String memoryId);
}
