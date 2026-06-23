package com.mdm.assistant.enums;

public enum WarrantyStatus {
    VALID("有效"),
    EXPIRED("已过期"),
    NEAR_EXPIRE("即将到期"),
    UNKNOWN("未知");

    private final String description;

    WarrantyStatus(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
