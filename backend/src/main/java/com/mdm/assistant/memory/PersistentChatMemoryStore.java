package com.mdm.assistant.memory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.assistant.entity.ChatMemoryEntity;
import com.mdm.assistant.repository.ChatMemoryRepository;
import com.mdm.assistant.service.ChatService.ChatMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class PersistentChatMemoryStore {

    private static final Logger log = LoggerFactory.getLogger(PersistentChatMemoryStore.class);

    private final ChatMemoryRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public PersistentChatMemoryStore(ChatMemoryRepository repository) {
        this.repository = repository;
    }

    public List<ChatMessage> getMessages(String memoryId) {
        return getMessages(null, memoryId);
    }

    public List<ChatMessage> getMessages(Long userId, String memoryId) {
        try {
            var entityOpt = userId != null
                    ? repository.findByUserIdAndMemoryId(userId, memoryId)
                    : repository.findByMemoryId(memoryId);
            return entityOpt.map(entity -> {
                        try {
                            return objectMapper.readValue(entity.getMessagesJson(),
                                    new TypeReference<List<ChatMessage>>() {});
                        } catch (JsonProcessingException e) {
                            log.error("Failed to deserialize messages for memoryId: {}", memoryId, e);
                            return new ArrayList<ChatMessage>();
                        }
                    })
                    .orElse(new ArrayList<>());
        } catch (Exception e) {
            log.error("Error getting messages for memoryId: {}", memoryId, e);
            return new ArrayList<>();
        }
    }

    public void updateMessages(String memoryId, List<ChatMessage> messages) {
        updateMessages(null, memoryId, messages);
    }

    public void updateMessages(Long userId, String memoryId, List<ChatMessage> messages) {
        try {
            List<ChatMessage> messageList = new ArrayList<>(messages);
            if (messageList.size() > 10) {
                messageList = messageList.subList(messageList.size() - 10, messageList.size());
            }

            String json = objectMapper.writeValueAsString(messageList);
            ChatMemoryEntity entity = userId != null
                    ? repository.findByUserIdAndMemoryId(userId, memoryId).orElse(null)
                    : repository.findByMemoryId(memoryId).orElse(null);
            if (entity == null) {
                entity = new ChatMemoryEntity(memoryId, memoryId, json);
                if (userId != null) {
                    entity.setUserId(userId);
                }
            }
            entity.setMessagesJson(json);
            entity.setUpdateTime(LocalDateTime.now());
            repository.save(entity);

            log.debug("Updated {} messages for memoryId: {}", messageList.size(), memoryId);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize messages for memoryId: {}", memoryId, e);
        }
    }

    public void deleteMessages(String memoryId) {
        repository.findByMemoryId(memoryId).ifPresent(repository::delete);
        log.debug("Deleted memory for memoryId: {}", memoryId);
    }
}
