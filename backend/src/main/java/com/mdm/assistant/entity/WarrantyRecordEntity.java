package com.mdm.assistant.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "warranty_record")
public class WarrantyRecordEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "device_id", nullable = false, length = 100)
    private String deviceId;

    @Column(name = "sn_code", length = 100)
    private String snCode;

    @Column(name = "device_name", length = 200)
    private String deviceName;

    @Column(name = "warranty_status")
    private String warrantyStatus;

    @Column(name = "warranty_start")
    private LocalDateTime warrantyStart;

    @Column(name = "warranty_end")
    private LocalDateTime warrantyEnd;

    @Column(name = "warranty_range", length = 500)
    private String warrantyRange;

    @Column(name = "query_time")
    private LocalDateTime queryTime;

    public WarrantyRecordEntity() {
    }

    @PrePersist
    protected void onCreate() {
        queryTime = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getSnCode() {
        return snCode;
    }

    public void setSnCode(String snCode) {
        this.snCode = snCode;
    }

    public String getDeviceName() {
        return deviceName;
    }

    public void setDeviceName(String deviceName) {
        this.deviceName = deviceName;
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

    public LocalDateTime getQueryTime() {
        return queryTime;
    }

    public void setQueryTime(LocalDateTime queryTime) {
        this.queryTime = queryTime;
    }
}