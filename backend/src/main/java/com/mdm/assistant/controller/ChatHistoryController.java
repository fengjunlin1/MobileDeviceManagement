package com.mdm.assistant.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.assistant.entity.ChatHistoryEntity;
import com.mdm.assistant.repository.ChatHistoryRepository;
import com.mdm.assistant.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/chat-history")
public class ChatHistoryController {

    private final ChatHistoryRepository chatHistoryRepository;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ChatHistoryController(ChatHistoryRepository chatHistoryRepository, JwtUtil jwtUtil) {
        this.chatHistoryRepository = chatHistoryRepository;
        this.jwtUtil = jwtUtil;
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getChatHistoryList(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long userId = extractUserId(request);

        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录");
            response.put("histories", Collections.emptyList());
            return ResponseEntity.ok(response);
        }

        List<ChatHistoryEntity> histories = chatHistoryRepository.findByUserIdOrderByUpdatedAtDesc(userId);
        List<Map<String, Object>> historyList = histories.stream().map(h -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", h.getId());
            map.put("sessionId", h.getSessionId());
            map.put("title", h.getTitle());
            map.put("updatedAt", h.getUpdatedAt() != null ? h.getUpdatedAt().toString() : null);
            return map;
        }).collect(Collectors.toList());

        response.put("success", true);
        response.put("histories", historyList);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> getChatHistoryDetail(
            @RequestParam String sessionId, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long userId = extractUserId(request);

        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.ok(response);
        }

        var entityOpt = chatHistoryRepository.findByUserIdAndSessionId(userId, sessionId);
        if (entityOpt.isPresent()) {
            ChatHistoryEntity entity = entityOpt.get();
            try {
                List<Map<String, Object>> messages = objectMapper.readValue(
                        entity.getMessagesJson(), new TypeReference<List<Map<String, Object>>>() {});
                response.put("success", true);
                response.put("sessionId", entity.getSessionId());
                response.put("title", entity.getTitle());
                response.put("messages", messages);
            } catch (JsonProcessingException e) {
                response.put("success", false);
                response.put("message", "数据解析失败");
            }
        } else {
            response.put("success", false);
            response.put("message", "对话不存在");
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/save")
    @Transactional
    public ResponseEntity<Map<String, Object>> saveChatHistory(
            @RequestParam String sessionId,
            @RequestParam String messagesJson,
            @RequestParam(required = false) String title,
            HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long userId = extractUserId(request);

        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.ok(response);
        }

        var entityOpt = chatHistoryRepository.findByUserIdAndSessionId(userId, sessionId);
        ChatHistoryEntity entity;
        if (entityOpt.isPresent()) {
            entity = entityOpt.get();
        } else {
            entity = new ChatHistoryEntity();
            entity.setUserId(userId);
            entity.setSessionId(sessionId);
        }
        entity.setMessagesJson(messagesJson);
        if (title != null) {
            entity.setTitle(title);
        }
        chatHistoryRepository.save(entity);

        response.put("success", true);
        response.put("message", "保存成功");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/delete")
    @Transactional
    public ResponseEntity<Map<String, Object>> deleteChatHistory(
            @RequestParam String sessionId, HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long userId = extractUserId(request);

        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.ok(response);
        }

        chatHistoryRepository.deleteByUserIdAndSessionId(userId, sessionId);
        response.put("success", true);
        response.put("message", "删除成功");
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/clear")
    @Transactional
    public ResponseEntity<Map<String, Object>> clearAllChatHistory(HttpServletRequest request) {
        Map<String, Object> response = new HashMap<>();
        Long userId = extractUserId(request);

        if (userId == null) {
            response.put("success", false);
            response.put("message", "未登录");
            return ResponseEntity.ok(response);
        }

        chatHistoryRepository.deleteByUserId(userId);
        response.put("success", true);
        response.put("message", "全部删除成功");
        return ResponseEntity.ok(response);
    }

    private Long extractUserId(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    return jwtUtil.extractUserId(token);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}