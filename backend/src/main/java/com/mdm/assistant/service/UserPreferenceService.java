package com.mdm.assistant.service;

import com.mdm.assistant.entity.FavoriteDeviceEntity;
import com.mdm.assistant.entity.UserPreferenceEntity;
import com.mdm.assistant.repository.FavoriteDeviceRepository;
import com.mdm.assistant.repository.UserPreferenceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class UserPreferenceService {

    private static final Logger log = LoggerFactory.getLogger(UserPreferenceService.class);

    private final UserPreferenceRepository userPreferenceRepository;
    private final FavoriteDeviceRepository favoriteDeviceRepository;

    public UserPreferenceService(UserPreferenceRepository userPreferenceRepository,
                                  FavoriteDeviceRepository favoriteDeviceRepository) {
        this.userPreferenceRepository = userPreferenceRepository;
        this.favoriteDeviceRepository = favoriteDeviceRepository;
    }

    /**
     * 从收藏设备自动计算偏好
     * - 品牌：收藏次数最多的品牌
     * - 价格区间：收藏设备中的最低和最高价格
     */
    public Map<String, Object> calculateAutoPreferences(Long userId) {
        String uid = String.valueOf(userId);
        List<FavoriteDeviceEntity> favorites = favoriteDeviceRepository.findByUserIdOrderByCreatedAtDesc(uid);
        Map<String, Object> result = new HashMap<>();

        if (favorites.isEmpty()) {
            result.put("autoBrand", "");
            result.put("autoBudgetMin", "");
            result.put("autoBudgetMax", "");
            return result;
        }

        // 统计品牌：出现次数最多的品牌
        Map<String, Long> brandCount = favorites.stream()
                .filter(f -> f.getBrand() != null && !f.getBrand().isEmpty())
                .collect(Collectors.groupingBy(FavoriteDeviceEntity::getBrand, Collectors.counting()));
        String topBrand = brandCount.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse("");
        result.put("autoBrand", topBrand);

        // 统计价格区间：从价格字符串中提取数字
        List<Integer> prices = new ArrayList<>();
        for (FavoriteDeviceEntity fav : favorites) {
            if (fav.getPrice() != null && !fav.getPrice().isEmpty()) {
                try {
                    String priceStr = fav.getPrice().replaceAll("[^0-9]", "");
                    if (!priceStr.isEmpty()) {
                        prices.add(Integer.parseInt(priceStr));
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        if (!prices.isEmpty()) {
            result.put("autoBudgetMin", Collections.min(prices));
            result.put("autoBudgetMax", Collections.max(prices));
        } else {
            result.put("autoBudgetMin", "");
            result.put("autoBudgetMax", "");
        }

        return result;
    }

    public UserPreferenceEntity getOrCreate(Long userId) {
        String uid = String.valueOf(userId);
        return userPreferenceRepository.findByUserId(uid)
                .orElseGet(() -> {
                    UserPreferenceEntity entity = new UserPreferenceEntity();
                    entity.setUserId(uid);
                    return userPreferenceRepository.save(entity);
                });
    }

    public String getPreferredBrands(Long userId) {
        return userPreferenceRepository.findByUserId(String.valueOf(userId))
                .map(UserPreferenceEntity::getPreferredBrands)
                .orElse(null);
    }

    public String getBudgetRange(Long userId) {
        return userPreferenceRepository.findByUserId(String.valueOf(userId))
                .map(p -> {
                    if (p.getBudgetMin() != null && p.getBudgetMax() != null) {
                        return p.getBudgetMin() + "-" + p.getBudgetMax() + "元";
                    } else if (p.getBudgetMin() != null) {
                        return p.getBudgetMin() + "元以上";
                    } else if (p.getBudgetMax() != null) {
                        return p.getBudgetMax() + "元以下";
                    }
                    return null;
                })
                .orElse(null);
    }

    public Integer getBudgetMin(Long userId) {
        return userPreferenceRepository.findByUserId(String.valueOf(userId))
                .map(UserPreferenceEntity::getBudgetMin)
                .orElse(null);
    }

    public Integer getBudgetMax(Long userId) {
        return userPreferenceRepository.findByUserId(String.valueOf(userId))
                .map(UserPreferenceEntity::getBudgetMax)
                .orElse(null);
    }

    public UserPreferenceEntity save(Long userId, String preferredBrands, Integer budgetMin, Integer budgetMax, String primaryUse) {
        String uid = String.valueOf(userId);
        UserPreferenceEntity entity = userPreferenceRepository.findByUserId(uid)
                .orElseGet(() -> {
                    UserPreferenceEntity e = new UserPreferenceEntity();
                    e.setUserId(uid);
                    return e;
                });
        if (preferredBrands != null) entity.setPreferredBrands(preferredBrands);
        if (budgetMin != null) entity.setBudgetMin(budgetMin);
        if (budgetMax != null) entity.setBudgetMax(budgetMax);
        if (primaryUse != null) entity.setPrimaryUse(primaryUse);
        log.info("保存用户偏好: userId={}, brands={}, budget={}-{}, use={}", userId, preferredBrands, budgetMin, budgetMax, primaryUse);
        return userPreferenceRepository.save(entity);
    }
}
