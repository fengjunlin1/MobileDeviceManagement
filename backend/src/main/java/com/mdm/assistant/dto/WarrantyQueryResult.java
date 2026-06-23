package com.mdm.assistant.dto;

import java.time.LocalDateTime;

public class WarrantyQueryResult {
    private String deviceName;
    private String snCode;
    private String warrantyStatus;
    private LocalDateTime warrantyStart;
    private LocalDateTime warrantyEnd;
    private String warrantyRange;
    private long daysRemaining;

    public WarrantyQueryResult() {
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
    }

    public String getSnCode() {
        return snCode;
    }

    public void setSnCode(String snCode) {
        this.snCode = snCode;
    }

    public String getWarrantyStatus() {
        return warrantyStatus;
    }

    public void setWarrantyStatus(String warrantyStatus) {
        this.warrantyStatus = warrantyStatus;
    }

    public LocalDateTime getWarrantyStart() {
        return warrantyStart;
    }

    public void setWarrantyStart(LocalDateTime warrantyStart) {
        this.warrantyStart = warrantyStart;
    }

    public LocalDateTime getWarrantyEnd() {
        return warrantyEnd;
    }

    public void setWarrantyEnd(LocalDateTime warrantyEnd) {
        this.warrantyEnd = warrantyEnd;
    }

    public String getWarrantyRange() {
        return warrantyRange;
    }

    public void setWarrantyRange(String warrantyRange) {
        this.warrantyRange = warrantyRange;
    }

    public long getDaysRemaining() {
        return daysRemaining;
    }

    public void setDaysRemaining(long daysRemaining) {
        this.daysRemaining = daysRemaining;
    }
}
