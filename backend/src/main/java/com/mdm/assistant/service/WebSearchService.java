package com.mdm.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${bocha.api.url:https://api.bocha.cn/v1/web-search}")
    private String apiUrl;

    @Value("${bocha.api.key:}")
    private String apiKey;

    @Value("${bocha.api.enabled:false}")
    private boolean enabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public static class SearchResult {
        private String title;
        private String url;
        private String snippet;
        private String summary;
        private String siteName;
        private String datePublished;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getSnippet() { return snippet; }
        public void setSnippet(String snippet) { this.snippet = snippet; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public String getSiteName() { return siteName; }
        public void setSiteName(String siteName) { this.siteName = siteName; }
        public String getDatePublished() { return datePublished; }
        public void setDatePublished(String datePublished) { this.datePublished = datePublished; }
    }

    /**
     * 判断web搜索功能是否可用
     */
    public boolean isEnabled() {
        return enabled && apiKey != null && !apiKey.isEmpty();
    }

    /**
     * 执行web搜索
     *
     * @param query 搜索关键词
     * @param count 返回结果数量（1-50）
     * @return 搜索结果列表
     */
    public List<SearchResult> search(String query, int count) {
        if (!isEnabled()) {
            log.warn("博查AI Web搜索未启用，请检查配置");
            return List.of();
        }

        try {
            log.info("开始博查AI Web搜索: query='{}', count={}", query, count);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");
            headers.set("Authorization", "Bearer " + apiKey);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("query", query);
            requestBody.put("freshness", "noLimit");
            requestBody.put("summary", true);
            requestBody.put("count", Math.min(Math.max(count, 1), 50));

            String jsonBody = objectMapper.writeValueAsString(requestBody);
            log.debug("博查API请求: {}", jsonBody);

            HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);

            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            return parseSearchResponse(response.getBody());
        } catch (Exception e) {
            log.error("博查AI Web搜索请求失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 执行web搜索，使用默认结果数量（10条）
     */
    public List<SearchResult> search(String query) {
        return search(query, 10);
    }

    /**
     * 解析博查搜索API的响应
     */
    private List<SearchResult> parseSearchResponse(String responseBody) {
        List<SearchResult> results = new ArrayList<>();
        if (responseBody == null || responseBody.isEmpty()) {
            return results;
        }

        try {
            JsonNode rootNode = objectMapper.readTree(responseBody);

            // 检查是否有错误
            if (rootNode.has("error")) {
                String errorMsg = rootNode.get("error").asText();
                log.error("博查API返回错误: {}", errorMsg);
                return results;
            }

            // 提取 webPages.value 数组
            JsonNode dataNode = rootNode.path("data");
            if (dataNode.isMissingNode()) {
                log.warn("博查API响应缺少data字段");
                return results;
            }

            JsonNode webPagesNode = dataNode.path("webPages");
            if (webPagesNode.isMissingNode()) {
                log.warn("博查API响应缺少webPages字段");
                return results;
            }

            JsonNode valueNode = webPagesNode.path("value");
            if (!valueNode.isArray() || valueNode.size() == 0) {
                log.info("博查API未搜索到结果");
                return results;
            }

            for (JsonNode item : valueNode) {
                SearchResult result = new SearchResult();
                result.setTitle(getSafeText(item, "name"));
                result.setUrl(getSafeText(item, "url"));
                result.setSnippet(getSafeText(item, "snippet"));
                result.setSummary(getSafeText(item, "summary"));
                result.setSiteName(getSafeText(item, "siteName"));
                result.setDatePublished(getSafeText(item, "datePublished"));
                results.add(result);
            }

            log.info("博查AI搜索到 {} 条结果", results.size());
        } catch (Exception e) {
            log.error("解析博查API响应失败: {}", e.getMessage());
        }

        return results;
    }

    private String getSafeText(JsonNode node, String field) {
        JsonNode fieldNode = node.path(field);
        return fieldNode.isMissingNode() ? "" : fieldNode.asText("");
    }

    /**
     * 将搜索结果格式化为文本上下文，供LLM使用
     */
    public String formatResultsAsContext(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("\n\n【以下是从互联网搜索到的相关信息，请基于这些信息回答用户问题】\n\n");

        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            sb.append("[").append(i + 1).append("] ");
            sb.append(r.getTitle()).append("\n");
            sb.append("   链接: ").append(r.getUrl()).append("\n");
            if (r.getDatePublished() != null && !r.getDatePublished().isEmpty()) {
                sb.append("   日期: ").append(r.getDatePublished()).append("\n");
            }
            if (r.getSiteName() != null && !r.getSiteName().isEmpty()) {
                sb.append("   来源: ").append(r.getSiteName()).append("\n");
            }
            String content = r.getSummary() != null && !r.getSummary().isEmpty() ? r.getSummary() : r.getSnippet();
            if (content != null && !content.isEmpty()) {
                sb.append("   内容: ").append(content).append("\n");
            }
            sb.append("\n");
        }

        sb.append("【搜索信息结束，请结合搜索结果和你的知识回答用户问题】\n");
        sb.append("注意：如果搜索结果中包含相关信息，请优先采用搜索结果。");
        sb.append("如果搜索结果与问题无关，请基于你的知识回答。");
        sb.append("请标注信息来源，格式如 [1][2] 等。");

        return sb.toString();
    }
}
