package com.mdm.assistant.controller;

import com.mdm.assistant.dto.ChatRequest;
import com.mdm.assistant.dto.ChatResponse;
import com.mdm.assistant.service.ChatService;
import com.mdm.assistant.service.TokenMonitorService;
import com.mdm.assistant.service.RagService;
import com.mdm.assistant.service.ChatContextService;
import com.mdm.assistant.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final TokenMonitorService tokenMonitorService;
    private final RagService ragService;
    private final JwtUtil jwtUtil;
    private final ChatContextService chatContextService;

    public ChatController(ChatService chatService, TokenMonitorService tokenMonitorService, RagService ragService, JwtUtil jwtUtil, ChatContextService chatContextService) {
        this.chatService = chatService;
        this.tokenMonitorService = tokenMonitorService;
        this.ragService = ragService;
        this.jwtUtil = jwtUtil;
        this.chatContextService = chatContextService;
    }

    @PostMapping("/send")
    public ResponseEntity<ChatResponse> send(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        if (request.getSessionId() == null || request.getSessionId().isEmpty()) {
            request.setSessionId(request.getDeviceId());
        }

        Long userId = extractUserId(httpRequest);
        if (userId != null) {
            request.setUserId(userId);
        }

        ChatResponse response = chatService.chat(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/toggle")
    public ResponseEntity<Map<String, Object>> toggleApi(@RequestBody Map<String, Boolean> request) {
        Boolean enabled = request.get("enabled");
        if (enabled != null) {
            chatService.setApiEnabled(enabled);
        }
        return ResponseEntity.ok(Map.of(
                "enabled", chatService.isApiEnabled(),
                "message", chatService.isApiEnabled() ? "API 已连接" : "API 已断开"
        ));
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(Map.of(
                "status", "running",
                "service", "MDM Assistant",
                "apiEnabled", chatService.isApiEnabled()
        ));
    }

    @GetMapping("/history/{sessionId}")
    public ResponseEntity<Map<String, Object>> getHistory(@PathVariable String sessionId) {
        var usage = tokenMonitorService.getSessionUsage(sessionId);
        return ResponseEntity.ok(Map.of(
                "sessionId", sessionId,
                "totalTokens", usage != null ? usage.getTotalTokens() : 0,
                "inputTokens", usage != null ? usage.getInputTokens() : 0,
                "outputTokens", usage != null ? usage.getOutputTokens() : 0
        ));
    }
    
    @DeleteMapping("/memory/{sessionId}")
    public ResponseEntity<Map<String, Object>> clearMemory(@PathVariable String sessionId) {
        ragService.clearSessionMemories(sessionId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "已清空会话 " + sessionId + " 的记忆"
        ));
    }

    @PostMapping("/recall")
    public ResponseEntity<Map<String, Object>> recallMessage(@RequestBody Map<String, String> request, HttpServletRequest httpRequest) {
        String sessionId = request.get("sessionId");
        String messageCountStr = request.get("count");
        
        if (sessionId == null || sessionId.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "会话ID不能为空"));
        }
        
        int count = 2; // 默认删除用户消息和AI回复
        if (messageCountStr != null) {
            try {
                count = Integer.parseInt(messageCountStr);
            } catch (NumberFormatException e) {
                count = 2;
            }
        }
        
        Long userId = extractUserId(httpRequest);
        if (userId == null) {
            userId = 0L; // 未登录用户使用默认值
        }
        
        // 构建 memoryKey
        String memoryKey = userId + ":" + sessionId;
        
        // 删除数据库中的消息
        chatContextService.deleteRecentMessages(userId, sessionId, count);
        
        // 清空内存缓存中的对应消息
        chatService.removeFromMemory(memoryKey, count);
        
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "已撤回 " + count + " 条消息"
        ));
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
