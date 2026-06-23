package com.mdm.assistant.websocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ReminderService {

    private static final Logger log = LoggerFactory.getLogger(ReminderService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, ReminderRecord> activeReminders = new ConcurrentHashMap<>();

    @Value("${app.warranty-check-days:30}")
    private int warrantyCheckDays;

    public ReminderService(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void sendWarrantyReminder(String userId, String deviceName, long daysRemaining) {
        String message = String.format("您的设备 %s 保修即将到期，剩余 %d 天", deviceName, daysRemaining);
        sendReminder(userId, "warranty", message);
    }

    public void sendNewProductReminder(String userId, String deviceName, String productInfo) {
        String message = String.format("新品推荐：%s - %s", deviceName, productInfo);
        sendReminder(userId, "new_product", message);
    }

    private void sendReminder(String userId, String type, String message) {
        String reminderId = userId + "_" + type + "_" + System.currentTimeMillis();
        activeReminders.put(reminderId, new ReminderRecord(reminderId, userId, type, message, LocalDateTime.now()));

        messagingTemplate.convertAndSendToUser(userId, "/queue/reminders",
                Map.of("type", type, "message", message, "timestamp", LocalDateTime.now().toString()));

        log.info("Sent reminder to user {}: {}", userId, message);
    }

    @Scheduled(fixedRate = 86400000)
    public void checkWarrantyExpirations() {
        log.info("Running warranty expiration check...");
    }

    public Map<String, ReminderRecord> getActiveReminders(String userId) {
        return Map.copyOf(activeReminders);
    }

    public void clearReminder(String reminderId) {
        activeReminders.remove(reminderId);
    }

    public static class ReminderRecord {
        private final String id;
        private final String userId;
        private final String type;
        private final String message;
        private final LocalDateTime createdAt;

        public ReminderRecord(String id, String userId, String type, String message, LocalDateTime createdAt) {
            this.id = id;
            this.userId = userId;
            this.type = type;
            this.message = message;
            this.createdAt = createdAt;
        }

        public String getId() { return id; }
        public String getUserId() { return userId; }
        public String getType() { return type; }
        public String getMessage() { return message; }
        public LocalDateTime getCreatedAt() { return createdAt; }
    }
}
