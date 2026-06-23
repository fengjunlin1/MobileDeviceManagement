package com.mdm.assistant.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class TokenMonitorServiceTest {

    private TokenMonitorService tokenMonitorService;

    @BeforeEach
    void setUp() {
        tokenMonitorService = new TokenMonitorService();
    }

    @Test
    @DisplayName("recordUsage应该正确记录token使用量")
    void testRecordUsage() {
        tokenMonitorService.recordUsage("session1", 100, 50);

        TokenMonitorService.SessionTokenUsage usage = tokenMonitorService.getSessionUsage("session1");

        assertNotNull(usage);
        assertEquals(100, usage.getInputTokens());
        assertEquals(50, usage.getOutputTokens());
        assertEquals(150, usage.getTotalTokens());
    }

    @Test
    @DisplayName("多次recordUsage应该累加token")
    void testMultipleRecordUsage() {
        tokenMonitorService.recordUsage("session1", 100, 50);
        tokenMonitorService.recordUsage("session1", 200, 100);

        TokenMonitorService.SessionTokenUsage usage = tokenMonitorService.getSessionUsage("session1");

        assertEquals(300, usage.getInputTokens());
        assertEquals(150, usage.getOutputTokens());
        assertEquals(450, usage.getTotalTokens());
    }

    @Test
    @DisplayName("getTotalTokens应该返回正确值")
    void testGetTotalTokens() {
        tokenMonitorService.recordUsage("session1", 1000, 500);

        long total = tokenMonitorService.getTotalTokens("session1");

        assertEquals(1500, total);
    }

    @Test
    @DisplayName("getTotalTokens对于不存在的session应该返回0")
    void testGetTotalTokensForNonExistentSession() {
        long total = tokenMonitorService.getTotalTokens("non-existent");

        assertEquals(0, total);
    }

    @Test
    @DisplayName("isOverLimit在超过限制时应该返回true")
    void testIsOverLimit() {
        for (int i = 0; i < 100; i++) {
            tokenMonitorService.recordUsage("session1", 1000, 1000);
        }

        assertTrue(tokenMonitorService.isOverLimit("session1"));
    }

    @Test
    @DisplayName("isOverLimit在未超过限制时应该返回false")
    void testIsNotOverLimit() {
        tokenMonitorService.recordUsage("session1", 100, 50);

        assertFalse(tokenMonitorService.isOverLimit("session1"));
    }

    @Test
    @DisplayName("resetSession应该清除session数据")
    void testResetSession() {
        tokenMonitorService.recordUsage("session1", 1000, 500);
        tokenMonitorService.resetSession("session1");

        assertEquals(0, tokenMonitorService.getTotalTokens("session1"));
    }
}
