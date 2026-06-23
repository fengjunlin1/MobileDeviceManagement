package com.mdm.assistant.enums;

public enum DeviceCategory {
    PHONE("手机"),
    SMARTWATCH("智能手表"),
    EARPHONES("耳机"),
    TABLET("平板"),
    UNKNOWN("未知");

    private final String description;

    DeviceCategory(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
