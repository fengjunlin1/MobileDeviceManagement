package com.mdm.assistant.dto;

import java.util.Map;

public class ChatResponse {
    private boolean success;
    private String reply;
    private String intent;
    private double confidence;
    private String errorCode;
    private String deviceName;
    private String deviceCategory;
    private boolean usedJdData;
    private String deviceBrand;
    private String devicePrice;
    private String deviceSpecs;
    private boolean imagePending;
    private boolean showFavorite;
    private Map<String, String> ocrDeviceData;

    public ChatResponse() {
    }

    public ChatResponse(boolean success, String reply, String intent, double confidence) {
        this.success = success;
        this.reply = reply;
        this.intent = intent;
        this.confidence = confidence;
    }

    public static ChatResponseBuilder builder() {
        return new ChatResponseBuilder();
    }

    public static class ChatResponseBuilder {
        private boolean success;
        private String reply;
        private String intent;
        private double confidence;
        private String deviceName;
        private String deviceCategory;
        private boolean usedJdData;
        private String deviceBrand;
        private String devicePrice;
        private String deviceSpecs;
        private boolean imagePending;
        private boolean showFavorite;
        private Map<String, String> ocrDeviceData;

        public ChatResponseBuilder success(boolean success) {
            this.success = success;
            return this;
        }

        public ChatResponseBuilder reply(String reply) {
            this.reply = reply;
            return this;
        }

        public ChatResponseBuilder intent(String intent) {
            this.intent = intent;
            return this;
        }

        public ChatResponseBuilder confidence(double confidence) {
            this.confidence = confidence;
            return this;
        }

        public ChatResponseBuilder deviceName(String deviceName) {
            this.deviceName = deviceName;
            return this;
        }

        public ChatResponseBuilder deviceCategory(String deviceCategory) {
            this.deviceCategory = deviceCategory;
            return this;
        }

        public ChatResponseBuilder usedJdData(boolean usedJdData) {
            this.usedJdData = usedJdData;
            return this;
        }

        public ChatResponseBuilder deviceBrand(String deviceBrand) {
            this.deviceBrand = deviceBrand;
            return this;
        }

        public ChatResponseBuilder devicePrice(String devicePrice) {
            this.devicePrice = devicePrice;
            return this;
        }

        public ChatResponseBuilder deviceSpecs(String deviceSpecs) {
            this.deviceSpecs = deviceSpecs;
            return this;
        }

        public ChatResponseBuilder imagePending(boolean imagePending) {
            this.imagePending = imagePending;
            return this;
        }

        public ChatResponseBuilder showFavorite(boolean showFavorite) {
            this.showFavorite = showFavorite;
            return this;
        }

        public ChatResponseBuilder ocrDeviceData(Map<String, String> ocrDeviceData) {
            this.ocrDeviceData = ocrDeviceData;
            return this;
        }

        public ChatResponse build() {
            ChatResponse r = new ChatResponse(success, reply, intent, confidence);
            r.setDeviceName(deviceName);
            r.setDeviceCategory(deviceCategory);
            r.setUsedJdData(usedJdData);
            r.setDeviceBrand(deviceBrand);
            r.setDevicePrice(devicePrice);
            r.setDeviceSpecs(deviceSpecs);
            r.setImagePending(imagePending);
            r.setShowFavorite(showFavorite);
            r.setOcrDeviceData(ocrDeviceData);
            return r;
        }
    }

    public static ChatResponse error(String message) {
        ChatResponse response = new ChatResponse();
        response.setSuccess(false);
        response.setReply(message);
        response.setErrorCode("SERVICE_UNAVAILABLE");
        return response;
    }

    public boolean isSuccess() { return success; }
    public void setSuccess(boolean success) { this.success = success; }
    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
    public String getIntent() { return intent; }
    public void setIntent(String intent) { this.intent = intent; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getDeviceName() { return deviceName; }
    public void setDeviceName(String deviceName) { this.deviceName = deviceName; }
    public String getDeviceCategory() { return deviceCategory; }
    public void setDeviceCategory(String deviceCategory) { this.deviceCategory = deviceCategory; }
    public boolean isUsedJdData() { return usedJdData; }
    public void setUsedJdData(boolean usedJdData) { this.usedJdData = usedJdData; }
    public String getDeviceBrand() { return deviceBrand; }
    public void setDeviceBrand(String deviceBrand) { this.deviceBrand = deviceBrand; }
    public String getDevicePrice() { return devicePrice; }
    public void setDevicePrice(String devicePrice) { this.devicePrice = devicePrice; }
    public String getDeviceSpecs() { return deviceSpecs; }
    public void setDeviceSpecs(String deviceSpecs) { this.deviceSpecs = deviceSpecs; }
    public boolean isImagePending() { return imagePending; }
    public void setImagePending(boolean imagePending) { this.imagePending = imagePending; }
    public boolean isShowFavorite() { return showFavorite; }
    public void setShowFavorite(boolean showFavorite) { this.showFavorite = showFavorite; }
    public Map<String, String> getOcrDeviceData() { return ocrDeviceData; }
    public void setOcrDeviceData(Map<String, String> ocrDeviceData) { this.ocrDeviceData = ocrDeviceData; }
}
