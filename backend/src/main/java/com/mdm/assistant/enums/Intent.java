package com.mdm.assistant.enums;

public enum Intent {
    WARRANTY_QUERY("保修查询"),
    PARAM_COMPARE("参数对比"),
    DEVICE_QUERY("设备查询"),
    PURCHASE_ADVICE("购机建议"),
    BIND_DEVICE("绑定设备"),
    HISTORY_QUERY("历史记录查询"),
    FAVORITE_OPERATION("收藏操作"),
    MY_DEVICE_OPERATION("我的设备操作"),
    UNKNOWN("未知意图");

    private final String description;

    Intent(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
