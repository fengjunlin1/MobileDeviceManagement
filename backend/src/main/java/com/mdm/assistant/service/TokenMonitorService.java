package com.mdm.assistant.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class TokenMonitorService {

    private static final Logger log = LoggerFactory.getLogger(TokenMonitorService.class);

    private final Map<String, SessionTokenUsage> sessionTokenUsage = new ConcurrentHashMap<>();
    private final long sessionTokenLimit = 100000L;

    public void recordUsage(String sessionId, int inputTokens, int outputTokens) {
        SessionTokenUsage usage = sessionTokenUsage.computeIfAbsent(sessionId, k -> new SessionTokenUsage());
        usage.addUsage(inputTokens, outputTokens);

        log.info("Session {} token usage - Input: {}, Output: {}, Total: {}",
                sessionId, inputTokens, outputTokens, usage.getTotalTokens());

        if (usage.getTotalTokens() > sessionTokenLimit) {
            log.warn("Session {} exceeded token limit: {} > {}",
                    sessionId, usage.getTotalTokens(), sessionTokenLimit);
        }
    }

    public long getTotalTokens(String sessionId) {
        SessionTokenUsage usage = sessionTokenUsage.get(sessionId);
        return usage != null ? usage.getTotalTokens() : 0;
    }

    public boolean isOverLimit(String sessionId) {
        return getTotalTokens(sessionId) > sessionTokenLimit;
    }

    public SessionTokenUsage getSessionUsage(String sessionId) {
        return sessionTokenUsage.get(sessionId);
    }

    public void resetSession(String sessionId) {
        sessionTokenUsage.remove(sessionId);
    }

    public static class SessionTokenUsage {
        private final AtomicLong inputTokens = new AtomicLong(0);
        private final AtomicLong outputTokens = new AtomicLong(0);

        public void addUsage(int input, int output) {
            inputTokens.addAndGet(input);
            outputTokens.addAndGet(output);
        }

        public long getInputTokens() {
            return inputTokens.get();
        }

        public long getOutputTokens() {
            return outputTokens.get();
        }

        public long getTotalTokens() {
            return inputTokens.get() + outputTokens.get();
        }
    }
}
