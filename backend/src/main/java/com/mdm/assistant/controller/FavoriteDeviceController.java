package com.mdm.assistant.controller;

import com.mdm.assistant.entity.FavoriteDeviceEntity;
import com.mdm.assistant.repository.FavoriteDeviceRepository;
import com.mdm.assistant.util.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/favorites")
public class FavoriteDeviceController {

    private final FavoriteDeviceRepository favoriteDeviceRepository;
    private final JwtUtil jwtUtil;

    public FavoriteDeviceController(FavoriteDeviceRepository favoriteDeviceRepository, JwtUtil jwtUtil) {
        this.favoriteDeviceRepository = favoriteDeviceRepository;
        this.jwtUtil = jwtUtil;
    }

    private String extractUserId(HttpServletRequest request) {
        String token = extractToken(request);
        if (token == null || !jwtUtil.validateToken(token)) {
            return null;
        }
        return String.valueOf(jwtUtil.extractUserId(token));
    }

    private String extractToken(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    @PostMapping("/add")
    public ResponseEntity<Map<String, Object>> addFavorite(HttpServletRequest request, @RequestBody Map<String, String> requestBody) {
        String userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));
        }

        String deviceName = requestBody.get("deviceName");
        String deviceCategory = requestBody.get("deviceCategory");
        
        if (deviceName == null || deviceCategory == null) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "message", "设备名称和类别不能为空"));
        }

        if (favoriteDeviceRepository.existsByUserIdAndDeviceName(userId, deviceName)) {
            return ResponseEntity.ok(Map.of("success", false, "message", "该设备已收藏"));
        }

        FavoriteDeviceEntity entity = new FavoriteDeviceEntity();
        entity.setUserId(userId);
        entity.setDeviceName(deviceName);
        entity.setDeviceCategory(deviceCategory);
        entity.setBrand(requestBody.getOrDefault("brand", ""));
        entity.setPrice(requestBody.getOrDefault("price", ""));
        entity.setSpecs(requestBody.getOrDefault("specs", ""));
        entity.setImageUrl(requestBody.getOrDefault("imageUrl", ""));
        entity.setJdUrl(requestBody.getOrDefault("jdUrl", ""));

        favoriteDeviceRepository.save(entity);

        return ResponseEntity.ok(Map.of("success", true, "message", "收藏成功"));
    }

    @DeleteMapping("/remove")
    public ResponseEntity<Map<String, Object>> removeFavorite(HttpServletRequest request, @RequestParam String deviceName) {
        String userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "未登录"));
        }

        Optional<FavoriteDeviceEntity> existing = favoriteDeviceRepository.findByUserIdAndDeviceNameIgnoreCaseAndNoSpaceExact(userId, deviceName);
        if (existing.isPresent()) {
            favoriteDeviceRepository.deleteByUserIdAndDeviceNameIgnoreCaseAndNoSpace(userId, deviceName);
            return ResponseEntity.ok(Map.of("success", true, "message", "取消收藏成功"));
        } else {
            return ResponseEntity.ok(Map.of("success", false, "message", "未找到该设备"));
        }
    }

    @GetMapping("/list")
    public ResponseEntity<Map<String, Object>> getFavorites(HttpServletRequest request, @RequestParam(required = false) String category) {
        String userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("success", false, "message", "未登录", "favorites", Map.of(), "total", 0));
        }

        List<FavoriteDeviceEntity> favorites;
        if (category != null && !category.isEmpty()) {
            favorites = favoriteDeviceRepository.findByUserIdAndDeviceCategoryOrderByCreatedAtDesc(userId, category);
        } else {
            favorites = favoriteDeviceRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }

        Map<String, List<Map<String, Object>>> categorizedFavorites = new LinkedHashMap<>();
        String[] categories = {"手机", "平板", "手表/手环", "耳机"};
        for (String cat : categories) {
            List<Map<String, Object>> items = favorites.stream()
                    .filter(f -> {
                        if (cat.equals("手表/手环")) {
                            return "手表/手环".equals(f.getDeviceCategory()) || "手表".equals(f.getDeviceCategory());
                        }
                        return cat.equals(f.getDeviceCategory());
                    })
                    .map(this::toMap)
                    .collect(Collectors.toList());
            if (!items.isEmpty()) {
                categorizedFavorites.put(cat, items);
            }
        }

        return ResponseEntity.ok(Map.of(
                "success", true,
                "favorites", categorizedFavorites,
                "total", favorites.size()
        ));
    }

    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkFavorite(HttpServletRequest request, @RequestParam String deviceName) {
        String userId = extractUserId(request);
        if (userId == null) {
            return ResponseEntity.ok(Map.of("success", false, "favorited", false));
        }

        boolean isFavorited = favoriteDeviceRepository.existsByUserIdAndDeviceNameIgnoreCaseAndNoSpace(userId, deviceName);
        return ResponseEntity.ok(Map.of("success", true, "favorited", isFavorited));
    }

    private Map<String, Object> toMap(FavoriteDeviceEntity entity) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", entity.getId());
        map.put("deviceName", entity.getDeviceName());
        map.put("deviceCategory", entity.getDeviceCategory());
        map.put("brand", entity.getBrand());
        map.put("price", entity.getPrice());
        map.put("specs", entity.getSpecs());
        map.put("imageUrl", entity.getImageUrl());
        map.put("jdUrl", entity.getJdUrl());
        map.put("createdAt", entity.getCreatedAt().toString());
        return map;
    }
}