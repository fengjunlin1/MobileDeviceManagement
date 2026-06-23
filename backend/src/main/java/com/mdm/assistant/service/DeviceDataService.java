package com.mdm.assistant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
public class DeviceDataService {

    private static final Logger log = LoggerFactory.getLogger(DeviceDataService.class);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Map<String, ScrapedDevice> deviceMap = new ConcurrentHashMap<>();

    @Value("${device.data.path:data/devices.json}")
    private String dataFilePath;

    @PostConstruct
    public void init() {
        loadFromFile();
    }

    public static class ScrapedDevice {
        private String name;
        private String brand;
        private String category;
        private String price;
        private String processor;
        private String screen;
        private String camera;
        private String battery;
        private String ram;
        private String storage;
        private String rating;
        private String imageUrl;
        private String productUrl;
        private Integer releaseYear;  // 发布年份，如 2024
        private long scrapedAt = System.currentTimeMillis();

        public ScrapedDevice() {}

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getBrand() { return brand; }
        public void setBrand(String brand) { this.brand = brand; }
        public String getCategory() { return category; }
        public void setCategory(String category) { this.category = category; }
        public String getPrice() { return price; }
        public void setPrice(String price) { this.price = price; }
        public String getProcessor() { return processor; }
        public void setProcessor(String processor) { this.processor = processor; }
        public String getScreen() { return screen; }
        public void setScreen(String screen) { this.screen = screen; }
        public String getCamera() { return camera; }
        public void setCamera(String camera) { this.camera = camera; }
        public String getBattery() { return battery; }
        public void setBattery(String battery) { this.battery = battery; }
        public String getRam() { return ram; }
        public void setRam(String ram) { this.ram = ram; }
        public String getStorage() { return storage; }
        public void setStorage(String storage) { this.storage = storage; }
        public String getRating() { return rating; }
        public void setRating(String rating) { this.rating = rating; }
        public String getImageUrl() { return imageUrl; }
        public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
        public String getProductUrl() { return productUrl; }
        public void setProductUrl(String productUrl) { this.productUrl = productUrl; }
        public Integer getReleaseYear() { return releaseYear; }
        public void setReleaseYear(Integer releaseYear) { this.releaseYear = releaseYear; }
        public long getScrapedAt() { return scrapedAt; }
        public void setScrapedAt(long scrapedAt) { this.scrapedAt = scrapedAt; }

        public String toFormattedString() {
            StringBuilder sb = new StringBuilder();
            sb.append("名称: ").append(name != null ? name : "未知");
            if (brand != null) sb.append("\n品牌: ").append(brand);
            if (category != null) sb.append("\n类别: ").append(category);
            if (price != null) sb.append("\n价格: ").append(price);
            if (processor != null) sb.append("\n处理器: ").append(processor);
            if (screen != null) sb.append("\n屏幕: ").append(screen);
            if (camera != null) sb.append("\n摄像头: ").append(camera);
            if (battery != null) sb.append("\n电池: ").append(battery);
            if (ram != null) sb.append("\n内存: ").append(ram);
            if (storage != null) sb.append("\n存储: ").append(storage);
            if (releaseYear != null) sb.append("\n发布年份: ").append(releaseYear);
            if (rating != null) sb.append("\n评分: ").append(rating);
            return sb.toString();
        }
    }

    public void addDevice(ScrapedDevice device) {
        if (device != null && device.getName() != null) {
            String key = normalizeName(device.getName());
            deviceMap.put(key, device);
            log.debug("添加设备到缓存: {} (key={})", device.getName(), key);
        }
    }

    public void addDevices(List<ScrapedDevice> devices) {
        for (ScrapedDevice d : devices) {
            addDevice(d);
        }
        saveToFile();
    }

    public List<ScrapedDevice> searchDevices(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return new ArrayList<>(deviceMap.values());
        }

        String lowerKeyword = keyword.toLowerCase();

        // 拆分关键词（按空格分隔），分别匹配
        String[] keywordParts = lowerKeyword.split("[\\s]+");

        return deviceMap.values().stream()
                .filter(d -> {
                    if (d.getName() == null) return false;
                    String lowerName = d.getName().toLowerCase();
                    String normalizedName = normalizeName(d.getName());

                    // 策略1: 所有关键词部分都匹配到名称中
                    boolean allMatch = true;
                    for (String part : keywordParts) {
                        if (part.isEmpty()) continue;
                        // 跳过类别关键词（手机、平板等），它们不需要出现在设备名中
                        if (isCategoryKeyword(part)) continue;
                        if (!normalizedName.contains(normalizeName(part)) && !lowerName.contains(part)) {
                            allMatch = false;
                            break;
                        }
                    }
                    if (allMatch && keywordParts.length > 0) return true;

                    // 策略2: 品牌名匹配
                    if (d.getBrand() != null) {
                        String lowerBrand = d.getBrand().toLowerCase();
                        for (String part : keywordParts) {
                            if (part.isEmpty() || isCategoryKeyword(part)) continue;
                            if (lowerBrand.contains(part) || part.contains(lowerBrand)) {
                                return true;
                            }
                        }
                    }

                    // 策略3: 整体模糊匹配
                    String normalizedKeyword = normalizeName(keyword);
                    return normalizedName.contains(normalizedKeyword) || lowerName.contains(lowerKeyword);
                })
                .collect(Collectors.toList());
    }

    /** 判断是否为类别关键词（不需要出现在设备名中） */
    private boolean isCategoryKeyword(String keyword) {
        return keyword.equals("手机") || keyword.equals("平板") || keyword.equals("平板电脑") ||
               keyword.equals("手表") || keyword.equals("智能手表") || keyword.equals("手环") ||
               keyword.equals("耳机") || keyword.equals("蓝牙耳机");
    }

    /**
     * 搜索设备并根据预算 + 发布年份进行智能排序
     * 预算充足时优先推荐新机（近1-2年），预算紧张时兼顾性价比机型
     *
     * @param keyword 搜索关键词
     * @param budget  用户预算（如 "8000元"、"5000以内"），可为 null
     * @return 按推荐优先级排序的设备列表
     */
    public List<ScrapedDevice> searchDevicesWithRanking(String keyword, String budget) {
        List<ScrapedDevice> matched = searchDevices(keyword);
        if (matched.isEmpty()) return matched;

        int budgetNum = extractBudgetNumber(budget);
        int currentYear = java.time.Year.now().getValue();

        // 按综合评分排序：预算适配 + 新机优先
        matched.sort((a, b) -> {
            double scoreA = calculateDeviceScore(a, budgetNum, currentYear);
            double scoreB = calculateDeviceScore(b, budgetNum, currentYear);
            return Double.compare(scoreB, scoreA); // 降序排列
        });

        return matched;
    }

    /**
     * 计算设备综合评分
     * 评分维度：
     * 1. 预算适配度 - 价格在预算范围内得分高
     * 2. 新机偏好度 - 预算充足时，新机加分更多
     */
    private double calculateDeviceScore(ScrapedDevice device, int budgetNum, int currentYear) {
        double score = 0.0;

        // 1. 预算适配度 (满分 40)
        if (budgetNum > 0 && device.getPrice() != null) {
            try {
                int price = Integer.parseInt(device.getPrice().replaceAll("[^0-9]", ""));
                if (price <= budgetNum) {
                    // 价格在预算内，越接近预算越好（充分利用预算）
                    score += 40.0 * (1.0 - (budgetNum - price) / (double) budgetNum * 0.3);
                } else if (price <= budgetNum * 1.15) {
                    // 略微超出预算（15%以内），小幅扣分但仍可接受
                    score += 20.0;
                }
                // 超出预算太多，不加分
            } catch (NumberFormatException ignored) {}
        } else {
            score += 30.0; // 无预算约束时给基础分
        }

        // 2. 新机偏好度 (满分 60)
        // 预算越高，新机偏好越强
        Integer releaseYear = device.getReleaseYear();
        if (releaseYear != null && releaseYear > 0) {
            int age = currentYear - releaseYear;
            double recencyWeight;

            if (budgetNum >= 5000) {
                // 预算充足（5000+）：强力偏好新机，老旧机型大扣分
                recencyWeight = 1.8;
            } else if (budgetNum >= 3000) {
                // 中等预算（3000-5000）：适度偏好新机
                recencyWeight = 1.3;
            } else if (budgetNum > 0) {
                // 低预算：轻度偏好新机
                recencyWeight = 1.0;
            } else {
                // 无预算：默认偏好新机
                recencyWeight = 1.2;
            }

            // 当年发布：满分 60；每早一年递减 15 分
            double recencyScore = Math.max(0, 60.0 - age * 15.0);
            score += recencyScore * recencyWeight;
        } else {
            // 无发布年份的设备给少量基础分
            score += 10.0;
        }

        return score;
    }

    /**
     * 从预算字符串中提取数字值
     * 如 "8000元" -> 8000, "5000以内" -> 5000, "2000-3000" -> 3000
     */
    private int extractBudgetNumber(String budget) {
        if (budget == null || budget.isEmpty()) return 0;
        try {
            // 提取所有数字
            String nums = budget.replaceAll("[^0-9]", "");
            if (nums.isEmpty()) return 0;
            int num = Integer.parseInt(nums);
            // 如果数字小于 100，可能是"8000元"被提取为"8000"而不是"8000"
            return num;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public ScrapedDevice findExact(String deviceName) {
        if (deviceName == null) return null;
        String key = normalizeName(deviceName);
        ScrapedDevice exact = deviceMap.get(key);
        if (exact != null) return exact;

        String lowerName = deviceName.toLowerCase();
        return deviceMap.values().stream()
                .filter(d -> d.getName() != null && normalizeName(d.getName()).equals(key))
                .findFirst()
                .orElse(null);
    }

    private String normalizeName(String name) {
        if (name == null) return "";
        return name.toLowerCase()
                .replaceAll("[\\s\\-_:：，,]", "")
                .replaceAll("[（(].*?[）)]", "")
                .trim();
    }

    public void loadFromFile() {
        try {
            File file = new File(dataFilePath);
            if (file.exists()) {
                List<ScrapedDevice> devices = objectMapper.readValue(
                        file, new TypeReference<List<ScrapedDevice>>() {});
                int filteredCount = 0;
                for (ScrapedDevice d : devices) {
                    if (d.getName() != null && isAllowedCategory(d)) {
                        deviceMap.put(normalizeName(d.getName()), d);
                    } else if (d.getName() != null) {
                        filteredCount++;
                        log.debug("加载时过滤非电子产品: {} (category={})", d.getName(), d.getCategory());
                    }
                }
                log.info("从文件加载了 {} 个设备数据 (过滤{}个非电子产品): {}", deviceMap.size(), filteredCount, dataFilePath);
            } else {
                log.info("设备数据文件不存在，将创建新文件: {}", dataFilePath);
            }
        } catch (Exception e) {
            log.warn("加载设备数据文件失败: {}", e.getMessage());
        }
    }

    /** 检查设备类别是否在允许的电子产品范围内 */
    private boolean isAllowedCategory(ScrapedDevice device) {
        if (device == null) return false;
        String category = device.getCategory();
        if (category == null) return false;
        return category.equals("手机") || category.equals("平板") || 
               category.equals("耳机") || category.equals("手表/手环");
    }

    public void saveToFile() {
        try {
            File file = new File(dataFilePath);
            file.getParentFile().mkdirs();
            objectMapper.writeValue(file, new ArrayList<>(deviceMap.values()));
            log.info("已保存 {} 个设备数据到文件: {}", deviceMap.size(), dataFilePath);
        } catch (IOException e) {
            log.error("保存设备数据文件失败: {}", e.getMessage());
        }
    }

    public int getDeviceCount() {
        return deviceMap.size();
    }
}
