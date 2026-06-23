package com.mdm.assistant.dto;

public class ChatRequest {
    private String deviceId;
    private String sessionId;
    private String message;
    private String imageBase64;
    private String imageType;
    private Long userId;

    public ChatRequest() {
    }

    public ChatRequest(String deviceId, String sessionId, String message) {
        this.deviceId = deviceId;
        this.sessionId = sessionId;
        this.message = message;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getImageBase64() {
        return imageBase64;
    }

    public void setImageBase64(String imageBase64) {
        this.imageBase64 = imageBase64;
    }

    public String getImageType() {
        return imageType;
    }

    public void setImageType(String imageType) {
        this.imageType = imageType;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }
}
