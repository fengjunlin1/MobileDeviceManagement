package com.mdm.assistant.service;

import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    private final EmbeddingModel embeddingModel;
    private final EmbeddingStore<TextSegment> embeddingStore;
    private volatile boolean initialized = false;

    public RagService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = new InMemoryEmbeddingStore<>();
    }

    @PostConstruct
    public void init() {
        try {
            log.info("初始化 RAG 服务...");
            // 测试一下 EmbeddingModel 是否可用
            embeddingModel.embed("test");
            initialized = true;
            log.info("RAG 服务初始化成功");
        } catch (Exception e) {
            log.warn("RAG 服务初始化失败，将使用纯 AI 模式", e);
            initialized = false;
        }
    }

    /**
     * 添加文档到向量库
     */
    public void addDocument(String content, String type, String source) {
        if (!initialized) {
            log.debug("RAG 未初始化，跳过添加文档");
            return;
        }
        try {
            Map<String, String> metadataMap = new HashMap<>();
            metadataMap.put("type", type);
            metadataMap.put("source", source);

            Metadata metadata = new Metadata(metadataMap);
            TextSegment segment = TextSegment.from(content, metadata);
            Embedding embedding = embeddingModel.embed(segment).content();

            embeddingStore.add(embedding, segment);
            log.debug("添加文档到向量库: type={}, source={}", type, source);
        } catch (Exception e) {
            log.error("添加文档到向量库失败", e);
        }
    }

    /**
     * 检索相关文档
     */
    public List<String> retrieveRelevantDocuments(String query, int maxResults) {
        if (!initialized) {
            log.debug("RAG 未初始化，返回空结果");
            return new ArrayList<>();
        }
        try {
            Embedding queryEmbedding = embeddingModel.embed(query).content();
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, maxResults);

            return matches.stream()
                    .map(match -> match.embedded().text())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("检索相关文档失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 检索设备相关信息
     */
    public String retrieveDeviceInfo(String deviceName) {
        if (!initialized) {
            log.debug("RAG 未初始化，返回空结果");
            return "";
        }
        List<String> results = retrieveRelevantDocuments(deviceName, 5);
        if (results.isEmpty()) {
            return "";
        }

        for (String result : results) {
            if (result != null && !result.isEmpty()) {
                String lowerResult = result.toLowerCase();
                String lowerDeviceName = deviceName.toLowerCase();

                if (lowerResult.contains(lowerDeviceName) ||
                    result.contains(deviceName) ||
                    isRelevantToDevice(result, deviceName)) {
                    log.info("检索到相关设备信息: {}", deviceName);
                    return result;
                }
            }
        }

        log.warn("未找到与 '{}' 相关的设备信息", deviceName);
        return "";
    }

    /**
     * 判断检索结果是否与查询设备相关
     */
    private boolean isRelevantToDevice(String content, String deviceName) {
        String[] keywords = deviceName.split("[\\s\\-]+");
        int matchCount = 0;

        for (String keyword : keywords) {
            if (keyword.length() > 1 && content.toLowerCase().contains(keyword.toLowerCase())) {
                matchCount++;
            }
        }

        return matchCount >= Math.max(1, keywords.length / 2);
    }

    /**
     * 添加对话历史到向量库（用于长期记忆）
     */
    public void addConversationToMemory(String sessionId, String userMessage, String assistantResponse) {
        if (!initialized) {
            log.debug("RAG 未初始化，跳过添加对话记忆");
            return;
        }
        try {
            String content = "用户: " + userMessage + "\n助手: " + assistantResponse;

            Map<String, String> metadataMap = new HashMap<>();
            metadataMap.put("type", "conversation");
            metadataMap.put("sessionId", sessionId);
            metadataMap.put("timestamp", String.valueOf(System.currentTimeMillis()));

            Metadata metadata = new Metadata(metadataMap);
            TextSegment segment = TextSegment.from(content, metadata);
            Embedding embedding = embeddingModel.embed(segment).content();

            embeddingStore.add(embedding, segment);
            log.debug("添加对话到记忆: sessionId={}", sessionId);
        } catch (Exception e) {
            log.error("添加对话到记忆失败", e);
        }
    }

    /**
     * 检索历史对话相关记忆
     */
    public List<String> retrieveRelevantMemories(String query, String sessionId, int maxResults) {
        if (!initialized) {
            log.debug("RAG 未初始化，返回空结果");
            return new ArrayList<>();
        }
        try {
            log.debug("检索记忆 - query: {}, sessionId: {}, maxResults: {}", query, sessionId, maxResults);

            Embedding queryEmbedding = embeddingModel.embed(query).content();
            List<EmbeddingMatch<TextSegment>> matches = embeddingStore.findRelevant(queryEmbedding, maxResults * 3);

            List<String> filteredMemories = matches.stream()
                    .filter(match -> {
                        String matchSessionId = match.embedded().metadata().asMap().get("sessionId");
                        return sessionId.equals(matchSessionId);
                    })
                    .limit(maxResults)
                    .map(match -> match.embedded().text())
                    .collect(Collectors.toList());

            log.debug("检索到 {} 条相关记忆", filteredMemories.size());
            return filteredMemories;
        } catch (Exception e) {
            log.error("检索相关记忆失败", e);
            return new ArrayList<>();
        }
    }

    /**
     * 清空指定 sessionId 的所有记忆
     */
    public void clearSessionMemories(String sessionId) {
        log.info("清空对话记忆请求（无操作）: {}", sessionId);
    }

    /**
     * 重建向量库（清空所有数据）
     */
    public void rebuildVectorStore() {
        log.info("重建向量库请求（无操作）");
    }
}
