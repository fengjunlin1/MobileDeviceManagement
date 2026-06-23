package com.mdm.assistant.dto;

public class IntentRecognitionResult {
    private String intent;
    private String extractedInfo;
    private double confidence;

    public IntentRecognitionResult() {
    }

    public IntentRecognitionResult(String intent, String extractedInfo, double confidence) {
        this.intent = intent;
        this.extractedInfo = extractedInfo;
        this.confidence = confidence;
    }

    public String getIntent() {
        return intent;
    }

    public void setIntent(String intent) {
        this.intent = intent;
    }

    public String getExtractedInfo() {
        return extractedInfo;
    }

    public void setExtractedInfo(String extractedInfo) {
        this.extractedInfo = extractedInfo;
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }
}
