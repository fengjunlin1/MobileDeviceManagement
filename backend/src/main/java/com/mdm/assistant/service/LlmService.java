package com.mdm.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.assistant.exception.ApiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${deepseek.api.url}")
    private String deepseekApiUrl;

    @Value("${deepseek.api.key}")
    private String deepseekApiKey;

    @Value("${deepseek.api.model}")
    private String deepseekModel;

    private final RestTemplate restTemplate = new RestTemplate();

    public String generate(String prompt) {
        List<Map<String, String>> messages = new ArrayList<>();
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        return chat(messages);
    }

    public String chat(List<Map<String, String>> messages) {
        int attempts = 0;
        int maxAttempts = 3;

        while (attempts < maxAttempts) {
            attempts++;

            try {
                log.info("DeepSeek API 调用尝试 {}/{}, 消息数: {}", attempts, maxAttempts, messages.size());
                String response = callDeepSeekApi(messages);
                return response;
            } catch (Exception e) {
                log.error("DeepSeek API 调用失败: {}", e.getMessage());
                if (attempts >= maxAttempts) {
                    throw new ApiServiceException("AI 服务调用失败: " + e.getMessage(), attempts);
                }

                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                }
            }
        }
        throw new ApiServiceException("AI 服务暂时不可用", maxAttempts);
    }

    @SuppressWarnings("unchecked")
    private String callDeepSeekApi(List<Map<String, String>> messages) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");
        headers.set("Authorization", "Bearer " + deepseekApiKey);

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", deepseekModel);
            requestBody.put("messages", new ArrayList<>(messages));
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 4096);

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("API 请求体大小: {} 字符", jsonBody.length());

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    deepseekApiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            return parseDeepSeekResponse(response.getBody());
        } catch (Exception e) {
            log.error("API 请求构建或解析失败", e);
            throw new ApiServiceException("请求处理失败: " + e.getMessage(), 0);
        }
    }

    private String parseDeepSeekResponse(String responseBody) {
        if (responseBody == null || responseBody.isEmpty()) return "";
        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);

            // 检查是否有error字段
            if (rootNode.has("error")) {
                JsonNode errorNode = rootNode.get("error");
                String errorMsg = errorNode.has("message") ? errorNode.get("message").asText() : errorNode.toString();
                log.error("DeepSeek API 返回错误: {}", errorMsg);
                return "";
            }

            // 检查choices是否存在
            JsonNode choices = rootNode.path("choices");
            if (choices.isMissingNode() || !choices.isArray() || choices.size() == 0) {
                log.error("DeepSeek API 响应缺少choices字段: {}", responseBody.length() > 500 ? responseBody.substring(0, 500) + "..." : responseBody);
                return "";
            }

            String content = choices.path(0).path("message").path("content").asText();
            if (content == null || content.isEmpty()) {
                log.warn("DeepSeek API 返回内容为空");
                return "";
            }
            return content;
        } catch (Exception e) {
            log.error("解析 DeepSeek 响应失败", e);
            return "";
        }
    }
}
