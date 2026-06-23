package com.mdm.assistant.controller;

import com.mdm.assistant.entity.MyDeviceEntity;
import com.mdm.assistant.repository.MyDeviceRepository;
import com.mdm.assistant.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/my-device")
public class MyDeviceController {

    private final MyDeviceRepository myDeviceRepository;
    private final JwtUtil jwtUtil;

    public MyDeviceController(MyDeviceRepository myDeviceRepository, JwtUtil jwtUtil) {
        this.myDeviceRepository = myDeviceRepository;
        this.jwtUtil = jwtUtil;
    }

    private Long extractUserId(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    return jwtUtil.extractUserId(token);
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addDevice(HttpServletRequest request, @RequestBody Map<String, String> body) {
        Long userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));
        }

        String deviceName = body.get("deviceName");
        String snCode = body.get("snCode");
        String activationDateStr = body.get("activationDate");

        if (deviceName == null || deviceName.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "设备名称不能为空"));
        }
        if (snCode == null || snCode.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "SN码不能为空"));
        }
        if (activationDateStr == null || activationDateStr.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("success", false, "message", "激活日期不能为空"));
        }

        if (myDeviceRepository.existsByUserIdAndSnCode(userId, snCode.trim())) {
            return ResponseEntity.ok(Map.of("success", false, "message", "该SN码设备已存在"));
        }

        MyDeviceEntity entity = new MyDeviceEntity();
        entity.setUserId(userId);
        entity.setDeviceName(deviceName.trim());
        entity.setDeviceCategory(body.getOrDefault("deviceCategory", "").trim());
        entity.setBrand(body.getOrDefault("brand", "").trim());
        entity.setRam(body.getOrDefault("ram", "").trim());
        entity.setRom(body.getOrDefault("rom", "").trim());
        entity.setSnCode(snCode.trim());
        entity.setActivationDate(LocalDate.parse(activationDateStr));

        myDeviceRepository.save(entity);

        return ResponseEntity.ok(Map.of("success", true, "message", "设备添加成功", "device", toMap(entity)));
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> listDevices(
            HttpServletRequest request,
            @RequestParam(required = false) String keyword) {
        Long userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "未登录", "devices", Collections.emptyList()));
        }

        List<MyDeviceEntity> devices;
        if (keyword != null && !keyword.trim().isEmpty()) {
            devices = myDeviceRepository.findByUserIdAndDeviceNameContainingIgnoreCaseOrderByCreatedAtDesc(userId, keyword.trim());
        } else {
            devices = myDeviceRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }

        List<Map<String, Object>> deviceList = devices.stream().map(this::toMap).collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("success", true, "devices", deviceList, "total", deviceList.size()));
    }

    @GetMapping("/detail")
    public ResponseEntity<Map<String, Object>> getDeviceDetail(HttpServletRequest request, @RequestParam Long id) {
        Long userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));
        }

        Optional<MyDeviceEntity> opt = myDeviceRepository.findByUserIdAndId(userId, id);
        if (opt.isPresent()) {
            MyDeviceEntity device = opt.get();
            Map<String, Object> map = toMap(device);
            map.put("warrantyExpireDate", device.getActivationDate().plusYears(1).toString());
            return ResponseEntity.ok(Map.of("success", true, "device", map));
        }

        return ResponseEntity.ok(Map.of("success", false, "message", "设备不存在"));
    }

    @DeleteMapping("/delete")
    public ResponseEntity<Map<String, Object>> deleteDevice(HttpServletRequest request, @RequestParam Long id) {
        Long userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));
        }

        myDeviceRepository.deleteByUserIdAndId(userId, id);
        return ResponseEntity.ok(Map.of("success", true, "message", "设备已删除"));
    }

    @GetMapping("/warranty-nearby")
    public ResponseEntity<Map<String, Object>> getWarrantyNearby(HttpServletRequest request) {
        Long userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "未登录", "devices", Collections.emptyList()));
        }

        List<MyDeviceEntity> allDevices = myDeviceRepository.findByUserIdOrderByCreatedAtDesc(userId);
        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(30);

        List<Map<String, Object>> nearby = new ArrayList<>();
        for (MyDeviceEntity device : allDevices) {
            LocalDate warrantyExpire = device.getActivationDate().plusYears(1);
            if (!warrantyExpire.isBefore(today) && !warrantyExpire.isAfter(deadline)) {
                Map<String, Object> map = toMap(device);
                long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, warrantyExpire);
                map.put("daysRemaining", daysRemaining);
                map.put("warrantyExpireDate", warrantyExpire.toString());
                nearby.add(map);
            }
        }

        nearby.sort((a, b) -> Long.compare((Long) a.get("daysRemaining"), (Long) b.get("daysRemaining")));

        return ResponseEntity.ok(Map.of("success", true, "devices", nearby, "total", nearby.size()));
    }

    private Map<String, Object> toMap(MyDeviceEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entity.getId());
        map.put("deviceName", entity.getDeviceName());
        map.put("deviceCategory", entity.getDeviceCategory());
        map.put("brand", entity.getBrand());
        map.put("ram", entity.getRam());
        map.put("rom", entity.getRom());
        map.put("snCode", entity.getSnCode());
        map.put("activationDate", entity.getActivationDate().toString());
        map.put("warrantyExpireDate", entity.getActivationDate().plusYears(1).toString());
        map.put("createdAt", entity.getCreatedAt() != null ? entity.getCreatedAt().toString() : "");
        return map;
    }
}