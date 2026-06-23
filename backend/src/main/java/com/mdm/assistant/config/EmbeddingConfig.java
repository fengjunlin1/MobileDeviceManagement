package com.mdm.assistant.config;

import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.AllMiniLmL6V2EmbeddingModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class EmbeddingConfig {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingConfig.class);

    @Bean
    public EmbeddingModel embeddingModel() {
        try {
            log.info("尝试加载 AllMiniLmL6V2EmbeddingModel...");
            return new AllMiniLmL6V2EmbeddingModel();
        } catch (Exception e) {
            log.warn("加载 AllMiniLmL6V2EmbeddingModel 失败，使用空实现", e);
            // 返回一个不会出错的空实现
            return new EmbeddingModel() {
                @Override
                public dev.langchain4j.model.output.Response<dev.langchain4j.data.embedding.Embedding> embed(String text) {
                    float[] vector = new float[384];
                    return dev.langchain4j.model.output.Response.from(
                        dev.langchain4j.data.embedding.Embedding.from(vector)
                    );
                }

                @Override
                public dev.langchain4j.model.output.Response<java.util.List<dev.langchain4j.data.embedding.Embedding>> embedAll(
                    java.util.List<dev.langchain4j.data.segment.TextSegment> segments
                ) {
                    java.util.List<dev.langchain4j.data.embedding.Embedding> embeddings = new java.util.ArrayList<>();
                    for (int i = 0; i < segments.size(); i++) {
                        embeddings.add(dev.langchain4j.data.embedding.Embedding.from(new float[384]));
                    }
                    return dev.langchain4j.model.output.Response.from(embeddings);
                }
            };
        }
    }
}
