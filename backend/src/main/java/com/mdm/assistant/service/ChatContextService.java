package com.mdm.assistant.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.assistant.entity.ChatMessageEntity;
import com.mdm.assistant.entity.ChatHistoryEntity;
import com.mdm.assistant.repository.ChatMessageRepository;
import com.mdm.assistant.repository.ChatHistoryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 聊天上下文服务 - 管理对话历史的持久化存储和检索
 * 
 * 功能：
 * 1. 保存每条用户消息和助手回复到数据库
 * 2. 检索指定会话的历史对话
 * 3. 提取会话中提到的设备和推荐设备
 * 4. 生成上下文摘要供 LLM 使用
 */
@Service
public class ChatContextService {

    private static final Logger log = LoggerFactory.getLogger(ChatContextService.class);
    private static final int MAX_CONTEXT_MESSAGES = 20;  // 最大上下文消息数

    private final ChatMessageRepository chatMessageRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatContextService(ChatMessageRepository chatMessageRepository, ChatHistoryRepository chatHistoryRepository) {
        this.chatMessageRepository = chatMessageRepository;
        this.chatHistoryRepository = chatHistoryRepository;
    }

    /**
     * 保存用户消息
     */
    @Transactional
    public ChatMessageEntity saveUserMessage(Long userId, String sessionId, String content, String intent, Set<String> mentionedDevices) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setRole("user");
        entity.setContent(content);
        entity.setIntent(intent);
        entity.setIsRecommendation(false);
        
        if (mentionedDevices != null && !mentionedDevices.isEmpty()) {
            entity.setMentionedDevices(String.join(",", mentionedDevices));
        }
        
        ChatMessageEntity saved = chatMessageRepository.save(entity);
        log.info("保存用户消息: userId={}, sessionId={}, intent={}, devices={}", 
                userId, sessionId, intent, mentionedDevices);
        return saved;
    }

    /**
     * 保存助手回复
     */
    @Transactional
    public ChatMessageEntity saveAssistantMessage(Long userId, String sessionId, String content, 
            String intent, boolean isRecommendation, List<String> recommendationDevices) {
        ChatMessageEntity entity = new ChatMessageEntity();
        entity.setUserId(userId);
        entity.setSessionId(sessionId);
        entity.setRole("assistant");
        entity.setContent(content);
        entity.setIntent(intent);
        entity.setIsRecommendation(isRecommendation);
        
        if (isRecommendation && recommendationDevices != null && !recommendationDevices.isEmpty()) {
            entity.setRecommendationDevices(String.join(",", recommendationDevices));
            // 推荐设备也记录到 mentionedDevices
            entity.setMentionedDevices(String.join(",", recommendationDevices));
        }
        
        ChatMessageEntity saved = chatMessageRepository.save(entity);
        log.info("保存助手回复: userId={}, sessionId={}, isRecommendation={}, devices={}", 
                userId, sessionId, isRecommendation, recommendationDevices);
        return saved;
    }

    /**
     * 获取指定会话的对话历史（格式化为上下文文本）
     */
    public String getConversationContext(Long userId, String sessionId) {
        List<ChatMessageEntity> messages = chatMessageRepository
                .findByUserIdAndSessionIdOrderByCreatedAtAsc(userId, sessionId);
        
        if (messages.isEmpty()) {
            log.debug("会话 {} 没有历史消息", sessionId);
            return "";
        }

        // 只取最近的消息
        int startIndex = Math.max(0, messages.size() - MAX_CONTEXT_MESSAGES);
        List<ChatMessageEntity> recentMessages = messages.subList(startIndex, messages.size());

        StringBuilder context = new StringBuilder();
        context.append("【对话历史上下文】\n");
        for (ChatMessageEntity msg : recentMessages) {
            String role = "user".equals(msg.getRole()) ? "用户" : "助手";
            context.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        // 添加设备追踪信息
        String deviceContext = getDeviceTrackingContext(sessionId);
        if (!deviceContext.isEmpty()) {
            context.append("\n").append(deviceContext);
        }

        log.debug("会话 {} 上下文: {} 条消息", sessionId, recentMessages.size());
        return context.toString();
    }

    /**
     * 获取会话中提到的所有设备
     */
    public Set<String> getMentionedDevices(String sessionId) {
        List<ChatMessageEntity> messages = chatMessageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        
        Set<String> devices = new LinkedHashSet<>();
        for (ChatMessageEntity msg : messages) {
            if (msg.getMentionedDevices() != null && !msg.getMentionedDevices().isEmpty()) {
                String[] deviceArr = msg.getMentionedDevices().split(",");
                for (String device : deviceArr) {
                    if (!device.trim().isEmpty()) {
                        devices.add(device.trim());
                    }
                }
            }
        }
        
        log.debug("会话 {} 提到的设备: {}", sessionId, devices);
        return devices;
    }

    /**
     * 获取会话中推荐的所有设备（按推荐顺序）
     */
    public List<String> getRecommendedDevices(String sessionId) {
        List<ChatMessageEntity> messages = chatMessageRepository
                .findRecommendationMessagesBySessionId(sessionId);
        
        List<String> devices = new ArrayList<>();
        for (ChatMessageEntity msg : messages) {
            if (msg.getRecommendationDevices() != null && !msg.getRecommendationDevices().isEmpty()) {
                String[] deviceArr = msg.getRecommendationDevices().split(",");
                for (String device : deviceArr) {
                    if (!device.trim().isEmpty() && !devices.contains(device.trim())) {
                        devices.add(device.trim());
                    }
                }
            }
        }
        
        // 反转顺序，保持推荐的时间顺序
        Collections.reverse(devices);
        log.debug("会话 {} 推荐的设备: {}", sessionId, devices);
        return devices;
    }

    /**
     * 获取设备追踪上下文（用于 LLM prompt）
     */
    public String getDeviceTrackingContext(String sessionId) {
        Set<String> mentionedDevices = getMentionedDevices(sessionId);
        List<String> recommendedDevices = getRecommendedDevices(sessionId);
        
        if (mentionedDevices.isEmpty() && recommendedDevices.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        
        if (!mentionedDevices.isEmpty()) {
            sb.append("\n【本次对话中提到的设备】\n");
            int count = 1;
            for (String device : mentionedDevices) {
                sb.append(count).append(". ").append(device).append("\n");
                count++;
            }
        }
        
        if (!recommendedDevices.isEmpty()) {
            sb.append("\n【本次对话中推荐过的设备】\n");
            int count = 1;
            for (String device : recommendedDevices) {
                sb.append(count).append(". ").append(device).append("\n");
                count++;
            }
            sb.append("如果用户询问推荐了哪些设备，请严格基于此列表回答，不要编造。\n");
        }
        
        return sb.toString();
    }

    /**
     * 获取用户的所有会话列表
     */
    public List<Map<String, Object>> getUserSessions(Long userId) {
        List<Object[]> summaries = chatMessageRepository.findSessionSummariesByUserId(userId);
        
        List<Map<String, Object>> sessions = new ArrayList<>();
        for (Object[] summary : summaries) {
            Map<String, Object> session = new HashMap<>();
            session.put("sessionId", summary[0]);
            session.put("messageCount", summary[1]);
            session.put("lastMessageTime", summary[2]);
            sessions.add(session);
        }
        
        log.info("用户 {} 有 {} 个会话", userId, sessions.size());
        return sessions;
    }

    /**
     * 删除指定用户和会话的最近N条消息（用于撤回）
     */
    @Transactional
    public void deleteRecentMessages(Long userId, String sessionId, int count) {
        List<ChatMessageEntity> messages = chatMessageRepository
                .findByUserIdAndSessionIdOrderByCreatedAtDesc(userId, sessionId);
        
        if (messages.isEmpty()) {
            log.debug("会话 {} 没有消息可删除", sessionId);
            return;
        }
        
        int deleteCount = Math.min(count, messages.size());
        for (int i = 0; i < deleteCount; i++) {
            chatMessageRepository.delete(messages.get(i));
        }
        
        log.info("删除会话 {} 的 {} 条消息", sessionId, deleteCount);
        
        // 更新 ChatHistoryEntity 中的 messagesJson
        updateChatHistoryMessagesJson(userId, sessionId);
    }

    /**
     * 删除指定用户和会话的所有消息
     */
    @Transactional
    public void deleteSession(Long userId, String sessionId) {
        chatMessageRepository.deleteByUserIdAndSessionId(userId, sessionId);
        log.info("删除会话: userId={}, sessionId={}", userId, sessionId);
    }

    /**
     * 获取指定会话的消息列表（用于前端展示）
     */
    public List<Map<String, Object>> getSessionMessages(Long userId, String sessionId) {
        List<ChatMessageEntity> messages = chatMessageRepository
                .findByUserIdAndSessionIdOrderByCreatedAtAsc(userId, sessionId);
        
        return messages.stream().map(msg -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", msg.getId());
            map.put("role", msg.getRole());
            map.put("content", msg.getContent());
            map.put("intent", msg.getIntent());
            map.put("createdAt", msg.getCreatedAt());
            map.put("isRecommendation", msg.getIsRecommendation());
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 检查会话是否存在
     */
    public boolean sessionExists(Long userId, String sessionId) {
        long count = chatMessageRepository.countBySessionId(sessionId);
        return count > 0;
    }

    /**
     * 获取会话中最近的消息（用于生成历史回复）
     */
    public List<ChatMessageEntity> getRecentMessages(Long userId, String sessionId, int limit) {
        List<ChatMessageEntity> allMessages = chatMessageRepository
                .findByUserIdAndSessionIdOrderByCreatedAtAsc(userId, sessionId);
        
        if (allMessages.isEmpty()) {
            return Collections.emptyList();
        }
        
        int startIndex = Math.max(0, allMessages.size() - limit);
        return allMessages.subList(startIndex, allMessages.size());
    }

    /**
     * 获取用户最近的消息（使用分页）
     */
    public List<ChatMessageEntity> getRecentMessagesByUserId(Long userId, int limit) {
        return chatMessageRepository.findRecentMessagesByUserId(userId, PageRequest.of(0, limit));
    }

    /**
     * 更新 ChatHistoryEntity 中的 messagesJson（同步 chat_message 表的最新消息）
     */
    @Transactional
    public void updateChatHistoryMessagesJson(Long userId, String sessionId) {
        try {
            Optional<ChatHistoryEntity> historyOpt = chatHistoryRepository.findByUserIdAndSessionId(userId, sessionId);
            if (historyOpt.isPresent()) {
                ChatHistoryEntity history = historyOpt.get();
                
                // 获取最新的消息列表
                List<ChatMessageEntity> messages = chatMessageRepository
                        .findByUserIdAndSessionIdOrderByCreatedAtAsc(userId, sessionId);
                
                // 转换为前端格式
                List<Map<String, Object>> messageMaps = messages.stream().map(msg -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("type", "user".equals(msg.getRole()) ? "user" : "ai");
                    map.put("content", msg.getContent());
                    map.put("timestamp", msg.getCreatedAt() != null ? msg.getCreatedAt().toString() : null);
                    return map;
                }).collect(Collectors.toList());
                
                history.setMessagesJson(objectMapper.writeValueAsString(messageMaps));
                chatHistoryRepository.save(history);
                
                log.info("更新会话 {} 的 ChatHistory messagesJson", sessionId);
            }
        } catch (JsonProcessingException e) {
            log.error("更新 ChatHistory messagesJson 失败", e);
        }
    }
}