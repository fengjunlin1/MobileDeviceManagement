package com.mdm.assistant.service;

import com.mdm.assistant.dto.WarrantyQueryResult;
import com.mdm.assistant.entity.WarrantyRecordEntity;
import com.mdm.assistant.ocr.OcrService;
import com.mdm.assistant.repository.WarrantyRecordRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class WarrantyService {

    private static final Logger log = LoggerFactory.getLogger(WarrantyService.class);

    private final WarrantyRecordRepository warrantyRepository;
    private final ApiProtectionService apiProtectionService;
    private final Map<String, WarrantyQueryResult> mockWarrantyData = new ConcurrentHashMap<>();
    private final Random random = new Random();

    @Value("${app.warranty-check-days:30}")
    private int warrantyCheckDays;

    public WarrantyService(WarrantyRecordRepository warrantyRepository,
                          ApiProtectionService apiProtectionService) {
        this.warrantyRepository = warrantyRepository;
        this.apiProtectionService = apiProtectionService;
        initializeMockData();
    }

    private void initializeMockData() {
        mockWarrantyData.put("SN12345678", createMockResult("SN12345678", "iPhone 15 Pro", true, 180));
        mockWarrantyData.put("SN87654321", createMockResult("SN87654321", "小米14 Ultra", true, 90));
        mockWarrantyData.put("SN11223344", createMockResult("SN11223344", "华为Mate 60 Pro", true, 365));
        mockWarrantyData.put("SN55667788", createMockResult("SN55667788", "三星Galaxy S24", false, -30));
    }

    private WarrantyQueryResult createMockResult(String sn, String name, boolean valid, int daysRemaining) {
        WarrantyQueryResult result = new WarrantyQueryResult();
        result.setSnCode(sn);
        result.setDeviceName(name);
        result.setWarrantyStart(LocalDateTime.now().minusDays(365 - daysRemaining));
        result.setWarrantyEnd(LocalDateTime.now().plusDays(daysRemaining));
        result.setDaysRemaining(daysRemaining);
        result.setWarrantyStatus(valid ? (daysRemaining <= 30 ? "即将到期" : "有效") : "已过期");
        result.setWarrantyRange("整机保修 + 充电器");
        return result;
    }

    public WarrantyQueryResult queryBySnCode(String snCode, Long userId) {
        return apiProtectionService.executeWithProtection("warranty-query", () -> {
            Optional<WarrantyRecordEntity> cached;
            if (userId != null) {
                cached = warrantyRepository.findByUserIdAndSnCode(userId, snCode);
            } else {
                cached = warrantyRepository.findBySnCode(snCode);
            }
            
            if (cached.isPresent()) {
                return convertToResult(cached.get());
            }

            WarrantyQueryResult result = queryExternalWarrantyApi(snCode);
            saveWarrantyRecord(result, userId);
            return result;
        });
    }

    public WarrantyQueryResult queryByImage(String imageBase64, OcrService ocrService, Long userId) {
        String snCode = ocrService.recognizeSnCode(imageBase64);
        return queryBySnCode(snCode, userId);
    }

    private WarrantyQueryResult queryExternalWarrantyApi(String snCode) {
        log.info("Querying warranty for SN code: {}", snCode);

        WarrantyQueryResult mockResult = mockWarrantyData.get(snCode);
        if (mockResult != null) {
            return mockResult;
        }

        int daysRemaining = random.nextInt(365) - 60;
        String deviceName = "设备型号-" + snCode.substring(0, Math.min(4, snCode.length()));

        WarrantyQueryResult result = new WarrantyQueryResult();
        result.setSnCode(snCode);
        result.setDeviceName(deviceName);
        result.setWarrantyStart(LocalDateTime.now().minusDays(365 - daysRemaining));
        result.setWarrantyEnd(LocalDateTime.now().plusDays(Math.max(0, daysRemaining)));
        result.setDaysRemaining(Math.max(0, daysRemaining));

        if (daysRemaining < 0) {
            result.setWarrantyStatus("已过期");
        } else if (daysRemaining <= 30) {
            result.setWarrantyStatus("即将到期");
        } else {
            result.setWarrantyStatus("有效");
        }

        result.setWarrantyRange("整机一年保修");

        return result;
    }

    private void saveWarrantyRecord(WarrantyQueryResult result, Long userId) {
        WarrantyRecordEntity entity = new WarrantyRecordEntity();
        entity.setUserId(userId != null ? userId : 0L);
        entity.setDeviceId(result.getSnCode());
        entity.setSnCode(result.getSnCode());
        entity.setDeviceName(result.getDeviceName());
        entity.setWarrantyStatus(result.getWarrantyStatus());
        entity.setWarrantyStart(result.getWarrantyStart());
        entity.setWarrantyEnd(result.getWarrantyEnd());
        entity.setWarrantyRange(result.getWarrantyRange());
        warrantyRepository.save(entity);
    }

    private WarrantyQueryResult convertToResult(WarrantyRecordEntity entity) {
        WarrantyQueryResult result = new WarrantyQueryResult();
        result.setSnCode(entity.getSnCode());
        result.setDeviceName(entity.getDeviceName());
        result.setWarrantyStatus(entity.getWarrantyStatus());
        result.setWarrantyStart(entity.getWarrantyStart());
        result.setWarrantyEnd(entity.getWarrantyEnd());
        result.setWarrantyRange(entity.getWarrantyRange());

        if (entity.getWarrantyEnd() != null) {
            result.setDaysRemaining(ChronoUnit.DAYS.between(LocalDateTime.now(), entity.getWarrantyEnd()));
        } else {
            result.setDaysRemaining(-1);
        }

        return result;
    }
}