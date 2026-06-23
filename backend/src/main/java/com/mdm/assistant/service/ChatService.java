package com.mdm.assistant.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.assistant.dto.*;
import com.mdm.assistant.enums.Intent;
import com.mdm.assistant.entity.FavoriteDeviceEntity;
import com.mdm.assistant.entity.MyDeviceEntity;
import com.mdm.assistant.repository.FavoriteDeviceRepository;
import com.mdm.assistant.repository.MyDeviceRepository;

import com.mdm.assistant.ocr.OcrService;
import com.mdm.assistant.service.UserPreferenceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final IntentRecognitionService intentRecognitionService;
    private final WarrantyService warrantyService;
    private final OcrService ocrService;
    private final LlmService llmService;
    private final RagService ragService;
    private final DeviceDataService deviceDataService;
    private final MyDeviceRepository myDeviceRepository;
    private final FavoriteDeviceRepository favoriteDeviceRepository;
    private final WebSearchService webSearchService;
    private final UserPreferenceService userPreferenceService;
    private final ChatContextService chatContextService;  // 新增：对话上下文服务
    
    // 内存缓存保留用于临时存储（如图片暂存），主要存储使用数据库
    private final Map<String, List<ChatMessage>> memoryCache = new ConcurrentHashMap<>();
    private static final int MAX_MESSAGES = 20;
    private final AtomicBoolean apiEnabled = new AtomicBoolean(true);
    private volatile String lastPurchaseDeviceName;
    private volatile boolean lastPurchaseUsedJdData;
    private volatile String lastPurchaseDeviceBrand;
    private volatile String lastPurchaseDevicePrice;
    private volatile String lastPurchaseDeviceSpecs;

    // 暂存用户上传的图片，key=sessionId, value={base64, type}
    private final Map<String, PendingImage> pendingImages = new ConcurrentHashMap<>();

    private static class PendingImage {
        String base64;
        String type;
        PendingImage(String base64, String type) { this.base64 = base64; this.type = type; }
    }

    public ChatService(IntentRecognitionService intentRecognitionService,
                       WarrantyService warrantyService,
                       OcrService ocrService,
                       LlmService llmService,
                       RagService ragService,
                       DeviceDataService deviceDataService,
                       MyDeviceRepository myDeviceRepository,
                       FavoriteDeviceRepository favoriteDeviceRepository,
                       WebSearchService webSearchService,
                       UserPreferenceService userPreferenceService,
                       ChatContextService chatContextService) {
        this.intentRecognitionService = intentRecognitionService;
        this.warrantyService = warrantyService;
        this.ocrService = ocrService;
        this.llmService = llmService;
        this.ragService = ragService;
        this.deviceDataService = deviceDataService;
        this.myDeviceRepository = myDeviceRepository;
        this.favoriteDeviceRepository = favoriteDeviceRepository;
        this.webSearchService = webSearchService;
        this.userPreferenceService = userPreferenceService;
        this.chatContextService = chatContextService;
    }

    public void setApiEnabled(boolean enabled) {
        apiEnabled.set(enabled);
    }

    public boolean isApiEnabled() {
        return apiEnabled.get();
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.getSessionId();
        String message = request.getMessage();
        Long userId = request.getUserId();
        String memoryKey = getMemoryKey(userId, sessionId);
        String rawSessionId = extractSessionId(memoryKey);

        try {
            // 保存用户消息到内存缓存和数据库
            addToMemory(memoryKey, new ChatMessage("user", message));
            
            // 提取用户消息中提到的设备
            Set<String> userMentionedDevices = new LinkedHashSet<>();
            extractDevicesFromMessage(message, userMentionedDevices);
            
            // 保存用户消息到数据库（userId 可能为 null，使用默认值）
            Long dbUserId = userId != null && userId > 0 ? userId : 0L;
            chatContextService.saveUserMessage(dbUserId, rawSessionId, message, null, userMentionedDevices);

            if (request.getImageBase64() != null && !request.getImageBase64().isEmpty()) {
                if (!apiEnabled.get()) {
                    String reply = getOfflineImageResponse();
                    addToMemory(memoryKey, new ChatMessage("assistant", reply));
                    chatContextService.saveAssistantMessage(dbUserId, rawSessionId, reply, "OFFLINE", false, null);
                    return ChatResponse.builder()
                            .success(true)
                            .reply(reply)
                            .intent("OFFLINE")
                            .confidence(1.0f)
                            .build();
                }

                // 暂存图片，不立即处理，引导用户选择
                String imageType = request.getImageType();
                if (imageType == null || imageType.isEmpty()) imageType = "jpeg";
                pendingImages.put(sessionId, new PendingImage(request.getImageBase64(), imageType));
                String choicePrompt = "我已收到您上传的图片。请告诉我您想做什么？\n\n" +
                        "回复 1 或「保存到设备」：我将从图片中提取设备信息，自动填充到新增设备表单中，缺失的信息由您补充\n" +
                        "回复 2 或「输出内容」：我将识别图片中的文字并输出";
                addToMemory(memoryKey, new ChatMessage("assistant", choicePrompt));
                chatContextService.saveAssistantMessage(dbUserId, rawSessionId, choicePrompt, "IMAGE_CHOICE", false, null);
                return ChatResponse.builder()
                        .success(true)
                        .reply(choicePrompt)
                        .intent("IMAGE_CHOICE")
                        .confidence(1.0f)
                        .imagePending(true)
                        .build();
            }

            // 检查是否有暂存的图片待处理
            if (pendingImages.containsKey(sessionId)) {
                PendingImage pending = pendingImages.get(sessionId);
                String pendingImage = pending.base64;
                String imageType = pending.type;
                String lowerMsg = message.toLowerCase().trim();
                boolean wantSave = lowerMsg.matches(".*[1一].*") || lowerMsg.contains("保存") || lowerMsg.contains("新增") || lowerMsg.contains("设备");
                boolean wantOutput = lowerMsg.matches(".*[2二].*") || lowerMsg.contains("输出") || lowerMsg.contains("内容") || lowerMsg.contains("文字");

                if (wantSave || wantOutput) {
                    pendingImages.remove(sessionId);

                    // 尝试OCR服务识别图片
                    String ocrText = recognizeImageText(pendingImage);

                    if (wantSave) {
                        // 从OCR文本中提取设备信息，只取真实数据
                        Map<String, String> deviceData = extractDeviceInfoViaLLM(ocrText);
                        StringBuilder reply = new StringBuilder("我已从图片中识别到以下设备信息：\n\n");
                        boolean hasInfo = false;
                        if (!deviceData.getOrDefault("deviceName", "").isEmpty()) {
                            reply.append("设备名称：").append(deviceData.get("deviceName")).append("\n");
                            hasInfo = true;
                        }
                        if (!deviceData.getOrDefault("brand", "").isEmpty()) {
                            reply.append("品牌：").append(deviceData.get("brand")).append("\n");
                            hasInfo = true;
                        }
                        if (!deviceData.getOrDefault("snCode", "").isEmpty()) {
                            reply.append("SN码：").append(deviceData.get("snCode")).append("\n");
                            hasInfo = true;
                        }
                        if (!deviceData.getOrDefault("deviceCategory", "").isEmpty()) {
                            reply.append("产品类型：").append(deviceData.get("deviceCategory")).append("\n");
                            hasInfo = true;
                        }
                        if (!deviceData.getOrDefault("ram", "").isEmpty()) {
                            reply.append("RAM：").append(deviceData.get("ram")).append("\n");
                        }
                        if (!deviceData.getOrDefault("rom", "").isEmpty()) {
                            reply.append("ROM：").append(deviceData.get("rom")).append("\n");
                        }

                        if (hasInfo) {
                            reply.append("\n已自动打开新增设备页面，请确认并补充缺失的信息后提交。");
                        } else {
                            reply = new StringBuilder("抱歉，我无法从这张图片中识别到设备信息。请手动填写新增设备表单。");
                        }
                        addToMemory(memoryKey, new ChatMessage("assistant", reply.toString()));
                        chatContextService.saveAssistantMessage(dbUserId, rawSessionId, reply.toString(), "OCR_SAVE_DEVICE", false, null);
                        return ChatResponse.builder()
                                .success(true)
                                .reply(reply.toString())
                                .intent("OCR_SAVE_DEVICE")
                                .confidence(1.0f)
                                .ocrDeviceData(deviceData)
                                .build();
                    } else {
                        // 输出图片内容
                        String displayText;
                        if (ocrText != null && !ocrText.trim().isEmpty()) {
                            displayText = formatOcrOutput(ocrText);
                        } else {
                            displayText = "当前图片识别服务（OCR）不可用，无法提取图片中的内容。请检查OCR服务是否已启动。";
                        }
                        String reply = "图片识别结果如下：\n\n" + displayText;
                        addToMemory(memoryKey, new ChatMessage("assistant", reply));
                        chatContextService.saveAssistantMessage(dbUserId, rawSessionId, reply, "OCR_OUTPUT", false, null);
                        return ChatResponse.builder()
                                .success(true)
                                .reply(reply)
                                .intent("OCR_OUTPUT")
                                .confidence(1.0f)
                                .build();
                    }
                }
                // 否则继续正常对话流程，图片保持暂存
            }

            if (isGreeting(message)) {
                String reply = "很高兴为您服务";
                addToMemory(memoryKey, new ChatMessage("assistant", reply));
                chatContextService.saveAssistantMessage(dbUserId, rawSessionId, reply, "GREETING", false, null);
                return ChatResponse.builder()
                        .success(true)
                        .reply(reply)
                        .intent("GREETING")
                        .confidence(1.0f)
                        .build();
            }

            if (isThanks(message)) {
                String reply = "不客气，欢迎再次提问";
                addToMemory(memoryKey, new ChatMessage("assistant", reply));
                chatContextService.saveAssistantMessage(dbUserId, rawSessionId, reply, "THANKS", false, null);
                return ChatResponse.builder()
                        .success(true)
                        .reply(reply)
                        .intent("THANKS")
                        .confidence(1.0f)
                        .build();
            }

            if (!apiEnabled.get()) {
                String reply = getOfflineResponse(message);
                addToMemory(memoryKey, new ChatMessage("assistant", reply));
                chatContextService.saveAssistantMessage(dbUserId, rawSessionId, reply, "OFFLINE", false, null);
                return ChatResponse.builder()
                        .success(true)
                        .reply(reply)
                        .intent("OFFLINE")
                        .confidence(1.0f)
                        .build();
            }

            // 检查数字选择（用于取消收藏时选择设备）
            String lowerMsg = message.toLowerCase().trim();
            if (userId != null && lowerMsg.matches("^\\d+$")) {
                int selection = Integer.parseInt(lowerMsg);
                List<FavoriteDeviceEntity> allFavorites = favoriteDeviceRepository.findByUserIdOrderByCreatedAtDesc(String.valueOf(userId));
                if (selection > 0 && selection <= allFavorites.size()) {
                    FavoriteDeviceEntity fav = allFavorites.get(selection - 1);
                    String reply = String.format("[ACTION_REQUIRED]\n{\n  \"action\": \"favorite_delete\",\n  \"deviceName\": \"%s\",\n  \"confirmMessage\": \"确定要删除收藏设备 %s 吗？\",\n  \"redirectPage\": \"favorite\"\n}", fav.getDeviceName(), fav.getDeviceName());
                    addToMemory(memoryKey, new ChatMessage("assistant", reply));
                    chatContextService.saveAssistantMessage(dbUserId, rawSessionId, reply, "FAVORITE_DELETE_SELECT", false, null);
                    return ChatResponse.builder()
                            .success(true)
                            .reply(reply)
                            .intent("FAVORITE_DELETE_SELECT")
                            .confidence(1.0f)
                            .build();
                }
            }

            String conversationContext = getConversationContext(memoryKey);
            IntentRecognitionResult intentResult = intentRecognitionService.recognize(message, conversationContext);
            String reply;
            Intent intent;
            try {
                intent = Intent.valueOf(intentResult.getIntent());
            } catch (IllegalArgumentException e) {
                intent = Intent.UNKNOWN;
            }

            // 传递sessionId给路由方法
            reply = routeByIntent(memoryKey, intentResult, message, userId);

            addToMemory(memoryKey, new ChatMessage("assistant", reply));

            // === 根据意图类型追踪推荐/对比设备 ===
            if (intent == Intent.PURCHASE_ADVICE) {
                // 从回复中提取所有推荐设备并加入推荐追踪列表
                List<String> recDevices = extractRecommendedDevicesFromReply(reply);
                for (String dev : recDevices) {
                    addRecommendedDevice(rawSessionId, dev);
                    addDeviceToTracking(rawSessionId, dev);
                }
                // 同时为前端保留第一个推荐设备信息
                if (!recDevices.isEmpty()) {
                    lastPurchaseDeviceName = recDevices.get(0);
                }
            } else if (intent == Intent.PARAM_COMPARE) {
                // 从消息中提取两个对比设备
                String[] compareDevices = extractDeviceNames(message);
                if (compareDevices.length == 2) {
                    addComparedPair(rawSessionId, compareDevices[0], compareDevices[1]);
                } else {
                    // 尝试从上下文提取
                    String[] ctxDevices = extractDeviceNamesFromContext(sessionId, message);
                    if (ctxDevices.length == 2) {
                        addComparedPair(rawSessionId, ctxDevices[0], ctxDevices[1]);
                    }
                }
            } else if (intent == Intent.DEVICE_QUERY) {
                // 在构建回复时追踪设备名
            }

            String deviceName = null;
            String deviceCategory = null;
            boolean usedJdData = false;
            String deviceBrand = null;
            String devicePrice = null;
            String deviceSpecs = null;
            boolean showFavorite = false;
            if (intent == Intent.DEVICE_QUERY) {
                deviceName = extractDeviceName(message);
                // DEVICE_QUERY 不显示收藏按钮，收藏只在推荐时显示
                if (deviceName != null && !deviceName.isEmpty()) {
                    addDeviceToTracking(rawSessionId, deviceName);
                }
            } else if (intent == Intent.PURCHASE_ADVICE) {
                deviceName = lastPurchaseDeviceName;
                usedJdData = lastPurchaseUsedJdData;
                deviceBrand = lastPurchaseDeviceBrand;
                devicePrice = lastPurchaseDevicePrice;
                deviceSpecs = lastPurchaseDeviceSpecs;
                // 仅在推荐设备时显示收藏按钮
                showFavorite = (deviceName != null && !deviceName.isEmpty());
            }

            ChatResponse.ChatResponseBuilder responseBuilder = ChatResponse.builder()
                    .success(true)
                    .reply(reply)
                    .intent(intentResult.getIntent())
                    .confidence(intentResult.getConfidence())
                    .usedJdData(usedJdData)
                    .deviceBrand(deviceBrand != null ? deviceBrand : "")
                    .devicePrice(devicePrice != null ? devicePrice : "")
                    .deviceSpecs(deviceSpecs != null ? deviceSpecs : "")
                    .showFavorite(showFavorite);
            if (deviceName != null) responseBuilder.deviceName(deviceName);
            if (deviceCategory != null) responseBuilder.deviceCategory(deviceCategory);
            
            // 保存助手回复到数据库
            boolean isRecommendation = intent == Intent.PURCHASE_ADVICE;
            List<String> recDevices = isRecommendation ? extractRecommendedDevicesFromReply(reply) : null;
            chatContextService.saveAssistantMessage(dbUserId, rawSessionId, reply, intent.name(), isRecommendation, recDevices);
            
            return responseBuilder.build();
        } catch (Exception e) {
            log.error("Chat error: ", e);
            return ChatResponse.error("抱歉，发生了错误：" + e.getMessage());
        }
    }

    /**
     * 识别图片文字：仅依赖OCR服务，不可用时返回空
     */
    private String recognizeImageText(String base64Image) {
        try {
            String result = ocrService.recognizeText(base64Image);
            if (result != null && !result.trim().isEmpty() && !result.contains("OCR 识别失败")
                    && !result.contains("HTTPException") && !result.contains("Internal Server Error")) {
                log.info("OCR服务识别成功，文本长度: {}", result.length());
                return result;
            }
        } catch (Exception e) {
            log.warn("OCR服务识别失败: {} ({})", e.getMessage(), e.getClass().getSimpleName());
        }
        log.info("OCR服务不可用，无法识别图片");
        return "";
    }

    /**
     * 从OCR识别的文本中提取设备信息，只提取明确出现的信息
     * 注意：OCR文本为空时直接返回空Map，不尝试任何图片识别
     */
    private Map<String, String> extractDeviceInfoViaLLM(String ocrText) {
        Map<String, String> result = new HashMap<>();
        result.put("deviceName", "");
        result.put("brand", "");
        result.put("snCode", "");
        result.put("deviceCategory", "");
        result.put("ram", "");
        result.put("rom", "");

        if (ocrText == null || ocrText.trim().isEmpty()) {
            log.warn("OCR文本为空，无法提取设备信息");
            return result;
        }

        String ocr = ocrText.trim();

        // === 第一步：正则从原文提取（不依赖LLM）===

        // SN码：连续数字字母组合8-20位（排除纯字母和无数字的字符串）
        java.util.regex.Matcher snMatcher = Pattern.compile("[A-Za-z0-9]{8,20}").matcher(ocr);
        if (snMatcher.find()) {
            String sn = snMatcher.group();
            // 必须包含至少一位数字，排除纯字母单词（如 Expecting）
            // 同时过滤纯数字日期（如20241012）
            boolean hasDigit = sn.matches(".*\\d+.*");
            if (hasDigit && !sn.matches("\\d{8}")) {
                result.put("snCode", sn);
            }
        }

        // 品牌：常见品牌关键词
        String[] brandKeywords = {"苹果", "华为", "小米", "三星", "OPPO", "vivo", "荣耀",
                "一加", "魅族", "realme", "iQOO", "Redmi", "联想", "中兴", "索尼", "谷歌"};
        for (String brand : brandKeywords) {
            if (ocr.contains(brand)) {
                result.put("brand", brand);
                break;
            }
        }

        // 设备分类关键词
        String[] categoryKeywords = {"手机", "平板", "手表", "耳机", "手环", "电脑", "笔记本", "显示器"};
        for (String cat : categoryKeywords) {
            if (ocr.contains(cat)) {
                result.put("deviceCategory", cat);
                break;
            }
        }

        // RAM/ROM
        java.util.regex.Matcher ramMatcher = Pattern.compile("(\\d+)\\s*GB.*(RAM|内存|运行)", Pattern.CASE_INSENSITIVE).matcher(ocr);
        if (ramMatcher.find()) {
            result.put("ram", ramMatcher.group(1) + "GB");
        }
        java.util.regex.Matcher romMatcher = Pattern.compile("(\\d+)\\s*GB.*(ROM|存储|储存|闪存)", Pattern.CASE_INSENSITIVE).matcher(ocr);
        if (romMatcher.find()) {
            result.put("rom", romMatcher.group(1) + "GB");
        }
        // 如果上面没匹配到，用更宽松的匹配：数字+GB后跟存储相关字
        if (result.get("ram").isEmpty() || result.get("rom").isEmpty()) {
            java.util.regex.Matcher gbMatcher = Pattern.compile("(\\d+)\\s*(?:/|\\s*)\\s*(\\d+)\\s*GB").matcher(ocr);
            if (gbMatcher.find()) {
                if (result.get("ram").isEmpty()) result.put("ram", gbMatcher.group(1) + "GB");
                if (result.get("rom").isEmpty()) result.put("rom", gbMatcher.group(2) + "GB");
            }
        }

        // 设备名：如果品牌+型号模式
        java.util.regex.Matcher nameMatcher = Pattern.compile("(iPhone\\s*\\d+[\\w\\s]*|iPad\\s*[\\w\\s]*|Mac[Bb]ook\\s*[\\w\\s]*|华为\\s*\\S+|小米\\s*\\S+|三星\\s*\\S+|OPPO\\s*\\S+|vivo\\s*\\S+)").matcher(ocr);
        if (nameMatcher.find()) {
            result.put("deviceName", nameMatcher.group(1).trim());
        }

        log.info("正则从OCR提取的设备信息: {}", result);

        // === 第二步：尝试LLM提取（覆盖正则没有匹配到的情况）===
        boolean llmFoundExtra = false;
        try {
            String prompt = "以下是从一张图片中识别出的文字内容。请从中提取电子设备的相关信息。" +
                    "只提取明确出现在以下文字中的信息，不要编造。\n\n" +
                    "文字内容：\n" + ocr + "\n\n" +
                    "请严格按照以下JSON格式输出，不要添加任何其他内容：\n" +
                    "{\"deviceName\":\"\", \"brand\":\"\", \"snCode\":\"\", \"deviceCategory\":\"\", \"ram\":\"\", \"rom\":\"\"}\n" +
                    "如果某个信息在文字中没有明确出现，对应字段留空字符串。"
                    + "特别注意：SN码/序列号是连续的数字字母组合，长度通常在8-20位之间。";
            String llmResult = llmService.generate(prompt);

            if (llmResult != null && !llmResult.trim().isEmpty()) {
                int start = llmResult.indexOf("{");
                int end = llmResult.lastIndexOf("}");
                if (start != -1 && end > start) {
                    String jsonStr = llmResult.substring(start, end + 1);
                    try {
                        JsonNode node = objectMapper.readTree(jsonStr);
                        for (String field : new String[]{"deviceName", "brand", "snCode", "deviceCategory", "ram", "rom"}) {
                            if (node.has(field)) {
                                String v = node.get(field).asText().trim();
                                // 仅当正则没提取到时，才用LLM的结果
                                if (!v.isEmpty() && result.get(field).isEmpty()) {
                                    result.put(field, v);
                                    llmFoundExtra = true;
                                }
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.warn("LLM提取设备信息失败，使用正则结果: {}", e.getMessage());
        }

        if (llmFoundExtra) {
            log.info("LLM补充了正则未提取到的字段: {}", result);
        }

        return result;
    }

    /**
     * 直接返回OCR识别的原始文本，不经过LLM格式化
     */
    private String formatOcrOutput(String ocrText) {
        if (ocrText == null || ocrText.trim().isEmpty()) {
            return "无法从图片中识别到文字内容。";
        }
        return ocrText.trim();
    }

    private String getOfflineResponse(String message) {
        String lowerMessage = message.toLowerCase().trim();

        if (lowerMessage.contains("对比") || lowerMessage.contains("比较")) {
            return "目前无法提供设备对比功能，请稍后重新连接 API 后再试。";
        }

        if (lowerMessage.contains("推荐") || lowerMessage.contains("建议") ||
                lowerMessage.contains("购机") || lowerMessage.contains("购买")) {
            return "目前无法提供设备推荐建议，请稍后重新连接 API 后再试。";
        }

        if (lowerMessage.contains("保修") || lowerMessage.contains("sn") || lowerMessage.contains("序列号")) {
            return "保修查询功能需要连接 API 才能使用，请稍后重新连接后再试。";
        }

        if (lowerMessage.contains("查询") || lowerMessage.contains("设备") ||
                lowerMessage.contains("介绍") || lowerMessage.contains("参数")) {
            return "目前无法查询设备信息，请稍后重新连接 API 后再试。";
        }

        if (lowerMessage.contains("绑定")) {
            return "设备绑定功能正在开发中，预计下周上线。";
        }

        if (lowerMessage.contains("历史") || lowerMessage.contains("记录")) {
            return "历史记录查询功能正在开发中，我们将记录您的偏好和查询历史。";
        }

        return "API 已断开，目前仅支持基础问候交流。请重新连接后使用完整功能。";
    }

    private String getOfflineImageResponse() {
        return "目前无法处理图片识别，请重新连接 API 后再试。";
    }

    /**
     * 添加消息到内存缓存（用于当前会话的快速访问）
     * 注意：数据库持久化在 chat() 方法中单独处理，这里只处理内存缓存
     */
    private void addToMemory(String memoryKey, ChatMessage message) {
        List<ChatMessage> messages = memoryCache.computeIfAbsent(memoryKey, k -> new ArrayList<>());
        messages.add(message);
        if (messages.size() > MAX_MESSAGES) {
            messages.remove(0);
        }
        log.debug("添加消息到内存缓存 {}，当前消息数: {}", memoryKey, messages.size());
    }

    /**
     * 从内存缓存中移除消息（用于撤回）
     */
    public void removeFromMemory(String memoryKey, int count) {
        List<ChatMessage> messages = memoryCache.get(memoryKey);
        if (messages != null && !messages.isEmpty()) {
            int removeCount = Math.min(count, messages.size());
            for (int i = 0; i < removeCount; i++) {
                messages.remove(messages.size() - 1);
            }
            log.info("从内存缓存移除 {} 条消息: {}", removeCount, memoryKey);
        }
    }

    /**
     * 从 memoryKey 中提取 userId 和 sessionId
     */
    private Long extractUserIdFromMemoryKey(String memoryKey) {
        if (memoryKey != null && memoryKey.contains(":")) {
            try {
                return Long.parseLong(memoryKey.substring(0, memoryKey.indexOf(":")));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    /**
     * 获取会话中提到的所有设备列表（从数据库）
     */
    private Set<String> getMentionedDevices(String sessionId) {
        return chatContextService.getMentionedDevices(sessionId);
    }
    
    /**
     * 获取会话中提到的设备列表，格式化为上下文文本
     */
    private String getMentionedDevicesContext(String sessionId) {
        return chatContextService.getDeviceTrackingContext(sessionId);
    }

    /**
     * 手动向会话设备追踪列表添加设备名（已由数据库管理，此方法保留兼容）
     */
    private void addDeviceToTracking(String sessionId, String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) return;
        log.debug("设备追踪由数据库管理: sessionId={}, device={}", sessionId, deviceName);
    }

    /**
     * 提取 sessionId（从 memoryKey 中剥离 userId 前缀）
     */
    private String extractSessionId(String memoryKey) {
        if (memoryKey != null && memoryKey.contains(":")) {
            return memoryKey.substring(memoryKey.indexOf(":") + 1);
        }
        return memoryKey;
    }

    /**
     * 记录助手明确推荐的设备到推荐追踪器（已由数据库管理，此方法保留兼容）
     */
    private void addRecommendedDevice(String sessionId, String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) return;
        log.debug("推荐设备追踪由数据库管理: sessionId={}, device={}", sessionId, deviceName);
    }

    /**
     * 记录助手对比过的设备对（已由数据库管理，此方法保留兼容）
     */
    private void addComparedPair(String sessionId, String device1, String device2) {
        if (device1 == null || device2 == null) return;
        log.debug("对比设备追踪由数据库管理: sessionId={}, devices={} vs {}", sessionId, device1, device2);
    }

    /**
     * 获取会话中所有推荐过的设备列表（从数据库）
     */
    private List<String> getRecommendedDevices(String sessionId) {
        return chatContextService.getRecommendedDevices(sessionId);
    }

    /**
     * 从助手回复中提取所有推荐设备（格式：第一推荐：XXX、第二推荐：XXX 等）
     */
    private List<String> extractRecommendedDevicesFromReply(String reply) {
        List<String> devices = new ArrayList<>();
        if (reply == null || reply.isEmpty()) return devices;

        // 匹配 "第一推荐：设备名"、"第二推荐：设备名" 等格式
        Pattern[] patterns = {
            Pattern.compile("(?:第一推荐|第二推荐|第三推荐|第四推荐|第五推荐)[：:]\\s*([^\\n。]+?)(?:\\n|。|$)"),
            Pattern.compile("推荐[：:]\\s*([^\\n。]+?)(?:\\n|。|$)"),
        };
        for (Pattern p : patterns) {
            Matcher m = p.matcher(reply);
            while (m.find()) {
                String name = m.group(1).trim();
                // 清理常见无意义后缀
                name = name.replaceAll("[，,。、！!?？]$", "").trim();
                if (!name.isEmpty() && name.length() > 1) {
                    devices.add(name);
                }
            }
        }

        // 如果没有匹配到"第一推荐"格式，尝试从品牌名+型号模式提取
        if (devices.isEmpty()) {
            Set<String> extracted = new LinkedHashSet<>();
            extractDevicesFromMessage(reply, extracted);
            devices.addAll(extracted);
        }

        log.info("从回复中提取到 {} 个推荐设备: {}", devices.size(), devices);
        return devices;
    }

    private String getMemoryKey(Long userId, String sessionId) {
        if (userId != null && userId > 0) {
            return userId + ":" + sessionId;
        }
        return sessionId;
    }

    private String handleImageInput(ChatRequest request, Intent intent) {
        if (!ocrService.isAvailable()) {
            return "抱歉，OCR服务暂时不可用，请稍后再试。";
        }

        try {
            String snCode = ocrService.recognizeSnCode(request.getImageBase64());
            WarrantyQueryResult result = warrantyService.queryBySnCode(snCode, request.getUserId());

            return formatWarrantyResult(result);
        } catch (Exception e) {
            log.error("Image processing error: ", e);
            return "图片识别失败，请确保图片清晰并包含SN码。";
        }
    }

    private boolean isGreeting(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String lowerMessage = message.toLowerCase().trim();
        return lowerMessage.matches("^(你好|您好|早上好|晚上好|下午好|嗨|hello|hi|hey|早安|午安|晚安)$") ||
                lowerMessage.contains("你好") ||
                lowerMessage.contains("您好") ||
                (lowerMessage.contains("早") && lowerMessage.contains("好")) ||
                (lowerMessage.contains("晚") && lowerMessage.contains("好"));
    }

    private boolean isThanks(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        String lowerMessage = message.toLowerCase().trim();
        return lowerMessage.matches("^(谢谢|谢谢你|多谢|感谢|thanks|thank you|thx)$") ||
                lowerMessage.contains("谢谢") ||
                lowerMessage.contains("感谢") ||
                lowerMessage.contains("多谢");
    }

    private String routeByIntent(String sessionId, IntentRecognitionResult intentResult, String message, Long userId) {
        Intent intent;
        try {
            intent = Intent.valueOf(intentResult.getIntent());
        } catch (IllegalArgumentException e) {
            intent = Intent.UNKNOWN;
        }

        return switch (intent) {
            case WARRANTY_QUERY -> generateWarrantyResponse(message, userId);
            case PARAM_COMPARE -> generateCompareResponse(sessionId, message);
            case DEVICE_QUERY -> generateDeviceResponse(message);
            case PURCHASE_ADVICE -> generatePurchaseResponse(sessionId, message, userId);
            case BIND_DEVICE -> generateBindResponse(message);
            case HISTORY_QUERY -> generateHistoryResponse(sessionId, message);
            case FAVORITE_OPERATION -> generateFavoriteOperationResponse(message, userId);
            case MY_DEVICE_OPERATION -> generateMyDeviceOperationResponse(message, userId);
            case UNKNOWN -> generateGeneralResponse(sessionId, message);
        };
    }

    private String getCurrentTimeInfo() {
        return java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy年MM月dd日"));
    }

    private String generateWarrantyResponse(String message, Long userId) {
        log.info("处理保修查询请求: userId={}, message={}", userId, message);

        if (userId == null || userId <= 0) {
            return "请先登录后再查询保修信息。";
        }

        // 如果消息包含"所有""全部""我的设备"等关键词，直接查全部设备
        String lowerMsg = message.toLowerCase();
        boolean queryAllDevices = lowerMsg.contains("所有") || lowerMsg.contains("全部") 
                || lowerMsg.contains("我的设备") || lowerMsg.contains("我的");
        
        // 尝试从消息中提取设备名称
        String deviceName = null;
        if (!queryAllDevices) {
            deviceName = extractDeviceNameFromWarrantyQuery(message);
            log.info("提取到的设备名称: '{}'", deviceName);
        } else {
            log.info("检测到全部设备保修查询，跳过设备名称提取");
        }

        // 从数据库查询用户的设备
        List<MyDeviceEntity> devices;
        if (deviceName != null && !deviceName.isEmpty()) {
            devices = myDeviceRepository.findByUserIdAndDeviceNameIgnoringSpaces(userId, deviceName);
        } else {
            devices = myDeviceRepository.findByUserIdOrderByCreatedAtDesc(userId);
        }

        if (devices == null || devices.isEmpty()) {
            if (deviceName != null && !deviceName.isEmpty()) {
                return String.format("抱歉，您的设备列表中没有找到包含\"%s\"的设备。请先在\"我的设备\"中添加该设备，然后再查询保修信息。", deviceName);
            } else {
                return "您的设备列表为空，请先在\"我的设备\"中添加设备，然后再查询保修信息。";
            }
        }

        // 构建保修信息回复
        StringBuilder sb = new StringBuilder();
        sb.append("以下是您的设备保修信息：\n\n");

        java.time.LocalDate today = java.time.LocalDate.now();

        for (MyDeviceEntity device : devices) {
            sb.append("设备名称：").append(device.getDeviceName()).append("\n");
            sb.append("品牌：").append(device.getBrand() != null ? device.getBrand() : "未知").append("\n");
            sb.append("序列号：").append(device.getSnCode()).append("\n");
            sb.append("激活日期：").append(device.getActivationDate() != null ? device.getActivationDate().toString() : "未知").append("\n");

            // 计算保修截止日期（激活日期 + 1年）
            java.time.LocalDate warrantyEnd = null;
            if (device.getActivationDate() != null) {
                warrantyEnd = device.getActivationDate().plusYears(1);
                sb.append("保修截止日期：").append(warrantyEnd.toString()).append("\n");

                // 计算剩余天数
                long daysRemaining = java.time.temporal.ChronoUnit.DAYS.between(today, warrantyEnd);
                if (daysRemaining > 30) {
                    sb.append("保修状态：有效（剩余 ").append(daysRemaining).append(" 天）\n");
                } else if (daysRemaining > 0) {
                    sb.append("保修状态：即将到期（剩余 ").append(daysRemaining).append(" 天）\n");
                } else {
                    sb.append("保修状态：已过期（过期 ").append(Math.abs(daysRemaining)).append(" 天）\n");
                }
            } else {
                sb.append("保修截止日期：未知（未设置激活日期）\n");
            }

            sb.append("\n");
        }

        sb.append("如需了解更多保修政策，请访问设备品牌官网或联系官方客服。");

        // 将数据库查询的结构化数据传给LLM做自然语言格式化
        String structuredData = sb.toString();
        try {
            String formatPrompt = String.format("""
                以下是用户的设备保修信息（来自数据库查询的结构化数据），请用自然友好、流畅的中文回复用户。
                保持所有数据和日期不变，只改变表达方式，让回复更亲切易懂。
                不要使用Markdown符号。

                原始数据：
                %s
                """, structuredData);
            String formatted = llmService.generate(formatPrompt);
            if (formatted != null && !formatted.trim().isEmpty()) {
                return formatted;
            }
        } catch (Exception e) {
            log.warn("LLM格式化保修信息失败，使用原始输出: {}", e.getMessage());
        }
        return structuredData;
    }

    /**
     * 从保修查询消息中提取设备名称
     */
    private String extractDeviceNameFromWarrantyQuery(String message) {
        if (message == null || message.isEmpty()) return null;

        // 尝试匹配 "查询XXX的保修期"、"XXX的保修"、"保修XXX" 等模式
        java.util.regex.Pattern[] patterns = {
                java.util.regex.Pattern.compile("(?:查询|查看|查一下)(.+?)(?:的|保修|保修期|保修状态|保修信息)"),
                java.util.regex.Pattern.compile("(.+?)(?:的|保修|保修期|保修状态|保修信息)"),
                java.util.regex.Pattern.compile("(?:保修|保修期|保修状态)(.+?)(?:的|吗|？|\\?)"),
        };

        for (java.util.regex.Pattern pattern : patterns) {
            var matcher = pattern.matcher(message);
            if (matcher.find()) {
                String name = matcher.group(1).trim();
                // 清理常见后缀
                name = name.replaceAll("(的|保修|保修期|保修状态|保修信息|吗|？|\\?)$", "").trim();
                if (!name.isEmpty() && name.length() >= 2 && !name.contains("设备") && !name.contains("我的")) {
                    return name;
                }
            }
        }

        return null;
    }

    /**
     * 从 DeepSeek 本地知识库获取设备信息
     */
    private String getDeviceInfoFromGsma(String deviceName) {
        try {
            log.info("开始通过 DeepSeek 查询设备信息: {}", deviceName);

            String prompt = String.format("""
                    你是一个专业的移动电子设备数据库助手，支持查询手机、平板、智能手表、耳机等各类移动设备。
                    
                    请提供以下设备的详细配置信息：
                    
                    设备名称：%s
                    
                    请根据设备类型提供相应的详细信息：
                    
                    【如果是手机】
                    - 品牌、型号、发布日期
                    - 处理器/芯片组、CPU、GPU
                    - 运行内存(RAM)、存储容量
                    - 电池容量、充电功率
                    - 屏幕尺寸、分辨率、刷新率
                    - 后置摄像头、前置摄像头
                    - 价格、评分
                    
                    【如果是平板】
                    - 品牌、型号、发布日期
                    - 处理器、内存、存储
                    - 屏幕尺寸、分辨率、刷新率
                    - 电池容量、充电功率
                    - 是否支持手写笔、键盘
                    - 价格、评分
                    
                    【如果是智能手表】
                    - 品牌、型号、发布日期
                    - 屏幕尺寸、类型、分辨率
                    - 电池续航时间
                    - 健康监测功能（心率、血氧、GPS等）
                    - 防水等级
                    - 兼容系统
                    - 价格、评分
                    
                    【如果是耳机】
                    - 品牌、型号、发布日期
                    - 类型（真无线/头戴式/颈挂式等）
                    - 降噪功能（主动降噪/被动降噪）
                    - 续航时间
                    - 蓝牙版本
                    - 音质特点
                    - 价格、评分
                    
                    【重要规则】
                    1. 只返回真实的、已发布的设备信息
                    2. 如果不知道某个配置，就跳过该项
                    3. 如果完全不知道这个设备，请直接回复"未找到该设备信息"
                    4. 不要编造任何信息
                    5. 使用简洁的格式，每行一个配置项
                    6. 不要使用 Markdown 符号
                    """, deviceName);

            String result = llmService.generate(prompt);

            if (result != null && !result.contains("未找到") && !result.isEmpty()) {
                log.info("成功获取设备信息: {}", deviceName);
                return result;
            } else {
                log.warn("未找到设备信息: {}", deviceName);
            }
        } catch (Exception e) {
            log.error("获取设备信息失败: {}", deviceName, e);
        }
        return "";
    }

    /**
     * 从AI返回文本中提取字段值
     */
    private String extractField(String text, String... fieldNames) {
        for (String fieldName : fieldNames) {
            Pattern pattern = Pattern.compile(fieldName + "\\s*[:：]\\s*([^\\n]+)");
            Matcher matcher = pattern.matcher(text);
            if (matcher.find()) {
                return matcher.group(1).trim();
            }
        }
        return null;
    }

    private String[] extractDeviceNamesFromContext(String sessionId, String message) {
        log.info("尝试从上下文提取设备名称，sessionId: {}, message: {}", sessionId, message);

        List<ChatMessage> messages = memoryCache.get(sessionId);
        if (messages == null || messages.isEmpty()) {
            log.warn("会话中没有历史消息");
            return new String[0];
        }

        log.info("历史消息数量: {}", messages.size());

        Set<String> mentionedDevices = new LinkedHashSet<>();

        String lowerMessage = message.toLowerCase();

        boolean shouldExtract = lowerMessage.contains("对比") || lowerMessage.contains("比较") ||
                lowerMessage.contains("刚才") || lowerMessage.contains("之前") ||
                lowerMessage.contains("上面") || lowerMessage.contains("这两个") ||
                lowerMessage.contains("两个") || lowerMessage.contains("哪两个") ||
                lowerMessage.contains("机型");

        if (shouldExtract) {
            log.info("用户请求包含上下文引用关键词，开始提取设备");

            for (int i = messages.size() - 1; i >= 0; i--) {
                ChatMessage msg = messages.get(i);

                if ("user".equals(msg.getRole())) {
                    String content = msg.getContent();
                    log.info("分析用户历史消息[{}]: {}", i, content);

                    String deviceName = extractDeviceNameFromUserMessage(content);

                    if (deviceName != null && !deviceName.isEmpty() && deviceName.length() > 1) {
                        log.info("从用户消息提取到设备名称: {}", deviceName);
                        mentionedDevices.add(deviceName);
                        if (mentionedDevices.size() >= 2) {
                            log.info("已提取到2个设备，但继续搜索以找到更早的记录");
                        }
                    }
                }

                if ("assistant".equals(msg.getRole())) {
                    String content = msg.getContent();
                    if (content.contains("设备名称") || content.contains("处理器") ||
                            content.contains("屏幕") || content.contains("摄像头") ||
                            content.contains("电池") || content.contains("品牌")) {
                        log.info("助手消息包含设备信息，尝试提取");

                        String deviceName = extractDeviceNameFromAssistantResponse(content);
                        if (deviceName != null && !deviceName.isEmpty() && deviceName.length() > 1) {
                            log.info("从助手消息提取到设备: {}", deviceName);
                            mentionedDevices.add(deviceName);
                        }
                    }
                }
            }
        }

        if (mentionedDevices.size() < 2) {
            log.warn("提取到的设备数量不足: {}, 设备列表: {}", mentionedDevices.size(), mentionedDevices);
            return new String[0];
        }

        List<String> deviceList = new ArrayList<>(mentionedDevices);
        Collections.reverse(deviceList);
        log.info("从上下文成功提取到设备名称（已反转顺序）: {}", deviceList);

        String[] result = new String[Math.min(2, deviceList.size())];
        for (int i = 0; i < result.length; i++) {
            result[i] = deviceList.get(i);
        }
        return result;
    }



    private String generateCompareResponse(String sessionId, String message) {
        log.info("开始处理设备对比请求: {}, sessionId: {}", message, sessionId);

        try {
            String[] deviceNames = extractDeviceNames(message);
            log.info("从当前消息提取设备名称: {}", java.util.Arrays.toString(deviceNames));

            if (deviceNames.length != 2) {
                log.info("当前消息未找到两个设备，尝试从上下文提取");
                deviceNames = extractDeviceNamesFromContext(sessionId, message);
                log.info("从上下文提取设备名称: {}", java.util.Arrays.toString(deviceNames));
            }

            if (deviceNames.length == 2) {
                log.info("准备检索设备信息 - 设备1: '{}', 设备2: '{}'", deviceNames[0], deviceNames[1]);

                String device1Info = getDeviceCompareInfo(deviceNames[0]);
                log.info("设备1信息获取结果长度: {}", device1Info.length());

                String device2Info = getDeviceCompareInfo(deviceNames[1]);
                log.info("设备2信息获取结果长度: {}", device2Info.length());

                if (device1Info.isEmpty() && device2Info.isEmpty()) {
                    return String.format("""
                            抱歉，我暂时无法找到关于"%s"和"%s"的详细信息来进行对比。
                            
                            请检查设备名称是否正确或提供准确的设备名，也可以询问其他设备。
                            """, deviceNames[0], deviceNames[1]);
                }

                if (device1Info.isEmpty()) {
                    log.warn("设备1信息为空: '{}'", deviceNames[0]);
                }

                if (device2Info.isEmpty()) {
                    log.warn("设备2信息为空: '{}'", deviceNames[1]);
                }

                String prompt = String.format("""
                        你是一个专业的移动电子设备分析助手。
                        用户请求：%s
                        
                        【设备1名称】
                        %s
                        
                        【设备1信息】
                        %s
                        
                        【设备2名称】
                        %s
                        
                        【设备2信息】
                        %s
                        
                        请基于以上信息进行详细对比分析，包括处理器、屏幕、摄像头、电池、价格等方面的差异。
                        
                        【极其重要的格式要求 - 必须严格遵守】
                        1. 使用HTML表格格式输出对比结果
                        2. 表格必须包含表头行，包含"参数"列和两个设备名称列（必须使用上面提供的【设备1名称】和【设备2名称】作为列标题，不要写"设备1"或"设备2"）
                        3. 参数列包含：处理器、屏幕、摄像头、电池、内存、存储、价格、发布日期等关键配置项
                        4. 在表格后添加对比总结
                        
                        【输出格式示例 - 必须按照这个格式，注意列标题必须是实际设备名】
                        <table border="1" cellpadding="6" cellspacing="0" style="width:100%%;border-collapse:collapse;">
                        <tr><th style="text-align:center;background-color:#ff6b9d;color:black;">参数</th><th style="text-align:center;background-color:#ff6b9d;color:black;">iPhone 15</th><th style="text-align:center;background-color:#ff6b9d;color:black;">小米14</th></tr>
                        <tr><td>处理器</td><td>XXX</td><td>XXX</td></tr>
                        <tr><td>屏幕</td><td>XXX</td><td>XXX</td></tr>
                        <tr><td>摄像头</td><td>XXX</td><td>XXX</td></tr>
                        <tr><td>电池</td><td>XXX</td><td>XXX</td></tr>
                        <tr><td>内存</td><td>XXX</td><td>XXX</td></tr>
                        <tr><td>存储</td><td>XXX</td><td>XXX</td></tr>
                        <tr><td>价格</td><td>XXX</td><td>XXX</td></tr>
                        </table>
                        
                        <h3>对比总结</h3>
                        XXXX（详细描述两个设备的差异和优劣）
                        
                        【重要规则】
                        1. 可以使用HTML标签：table, tr, th, td, h3, p, br
                        2. 表格必须有边框和合适的内边距
                        3. 语言自然友好，分段清晰
                        4. 只对比用户明确提到的两个设备，不要引入其他设备
                        5. 设备1名称和设备2名称要替换为实际的设备名称
                        
                        请直接回复，不要添加额外说明。
                        """, message,
                        deviceNames[0],
                        device1Info.isEmpty() ? "未找到设备信息" : device1Info,
                        deviceNames[1],
                        device2Info.isEmpty() ? "未找到设备信息" : device2Info);

                String response = llmService.generate(prompt);

                // 后处理：替换表格中可能的"设备1"/"设备2"占位符为实际设备名
                if (response != null) {
                    response = response.replace("设备1名称", deviceNames[0])
                                       .replace("设备2名称", deviceNames[1])
                                       .replace("设备1", deviceNames[0])
                                       .replace("设备2", deviceNames[1]);
                }

                return response;
            }

            if (deviceNames.length == 1) {
                return String.format("""
                        我只找到了您之前查询过的"%s"这一个设备。
                        
                        要进行设备对比，请先查询另一个设备，然后再说"对比这两个设备"。
                        
                        例如：
                        1. 先查询："查询小米14"
                        2. 然后说："对比一下这两个手机"
                        """, deviceNames[0]);
            }

            return "请明确指定要对比的两个设备，例如：\"对比iPhone 15 和小米 14\"";
        } catch (Exception e) {
            log.error("设备对比请求处理失败: {}", e.getMessage(), e);
            return "分析功能暂时繁忙，请稍后再试。";
        }
    }

    private String getDeviceCompareInfo(String deviceName) {
        log.info("开始获取设备对比信息: {}", deviceName);
        
        try {
            List<DeviceDataService.ScrapedDevice> localDevices = deviceDataService.searchDevices(deviceName);
            
            // 从本地缓存获取信息
            String localInfo = "";
            if (!localDevices.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                DeviceDataService.ScrapedDevice device = localDevices.get(0);
                sb.append("品牌: ").append(device.getBrand() != null ? device.getBrand() : "未知").append("\n");
                sb.append("型号: ").append(device.getName()).append("\n");
                sb.append("类别: ").append(device.getCategory() != null ? device.getCategory() : "未知").append("\n");
                if (device.getPrice() != null) sb.append("价格: ").append(device.getPrice()).append("\n");
                if (device.getProcessor() != null) sb.append("处理器: ").append(device.getProcessor()).append("\n");
                if (device.getRam() != null) sb.append("内存: ").append(device.getRam()).append("\n");
                if (device.getStorage() != null) sb.append("存储: ").append(device.getStorage()).append("\n");
                if (device.getScreen() != null) sb.append("屏幕: ").append(device.getScreen()).append("\n");
                if (device.getBattery() != null) sb.append("电池: ").append(device.getBattery()).append("\n");
                if (device.getCamera() != null) sb.append("摄像头: ").append(device.getCamera()).append("\n");
                localInfo = sb.toString();
            }
            
            // 执行博查AI Web搜索获取实时设备信息
            String webInfo = "";
            if (webSearchService.isEnabled()) {
                log.info("执行博查AI Web搜索设备对比信息: {}", deviceName);
                var searchResults = webSearchService.search(deviceName + " 配置 参数 价格", 5);
                if (!searchResults.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("\n【互联网搜索结果】\n");
                    for (int i = 0; i < searchResults.size(); i++) {
                        var r = searchResults.get(i);
                        String content = r.getSummary() != null && !r.getSummary().isEmpty() ? r.getSummary() : r.getSnippet();
                        if (content != null && !content.isEmpty()) {
                            sb.append("[").append(i + 1).append("] ").append(content).append("\n");
                        }
                    }
                    webInfo = sb.toString();
                    log.info("博查AI搜索到 {} 条设备对比信息", searchResults.size());
                }
            }
            
            // 合并本地信息和Web搜索信息
            if (!localInfo.isEmpty() || !webInfo.isEmpty()) {
                return localInfo + webInfo;
            }
            
            log.warn("本地缓存和Web搜索均未找到设备: {}", deviceName);
            String aiInfo = getDeviceInfoFromGsma(deviceName);
            if (!aiInfo.isEmpty()) {
                return aiInfo;
            }
            
        } catch (Exception e) {
            log.error("获取设备对比信息失败: {}", deviceName, e);
        }
        
        return "";
    }

    private String extractDeviceNameFromUserMessage(String content) {
        String[] prefixes = {"查询", "看看", "介绍", "对比"};

        for (String prefix : prefixes) {
            if (content.startsWith(prefix)) {
                String deviceName = content.substring(prefix.length()).trim();
                deviceName = cleanDeviceName(deviceName);
                return deviceName;
            }
        }

        String deviceName = content.trim();
        return cleanDeviceName(deviceName);
    }

    private String cleanDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) {
            return "";
        }

        deviceName = deviceName.replaceAll("^(查询|看看|介绍|对比|推荐)", "")
                .trim();

        deviceName = deviceName.replaceAll("(是什么|怎么样)$", "")
                .trim();

        deviceName = deviceName.replaceAll("^的|的$", "").trim();

        return deviceName;
    }

    /**
     * 清理设备名称，用于生成deviceUrl
     * 去除"配置"、"参数"、"是什么"等无关关键词
     */
    private String cleanDeviceNameForUrl(String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) {
            return "";
        }

        // 去除常见的无关关键词
        deviceName = deviceName.replaceAll("(的配置|的配置信息|的参数|的参数信息|是什么|怎么样)", "")
                .trim();

        // 去除多余的空格
        deviceName = deviceName.replaceAll("\\s+", " ").trim();

        return deviceName;
    }

    private String extractDeviceNameFromAssistantResponse(String content) {
        Pattern pattern = Pattern.compile("设备名称[：:]\\s*([^\\n]+)");
        Matcher matcher = pattern.matcher(content);
        if (matcher.find()) {
            String deviceName = matcher.group(1).trim();
            deviceName = deviceName.replaceAll("[，,。、]", "").trim();
            deviceName = deviceName.replaceAll("(的配置|的配置信息|的参数)$", "").trim();
            return deviceName;
        }
        return null;
    }


    private String generateDeviceResponse(String message) {
        String deviceName = extractDeviceName(message);
        String currentTimeInfo = getCurrentTimeInfo();

        log.info("开始处理设备查询: {}", deviceName);

        try {
            // 先搜索本地缓存
            List<DeviceDataService.ScrapedDevice> localDevices = deviceDataService.searchDevices(deviceName);

            // 执行博查AI Web搜索获取实时信息
            String webSearchContext = "";
            if (webSearchService.isEnabled()) {
                log.info("执行博查AI Web搜索设备信息: {}", deviceName);
                var searchResults = webSearchService.search(deviceName + " 手机 配置 参数 价格", 8);
                if (!searchResults.isEmpty()) {
                    webSearchContext = "\n\n【以下是从互联网搜索到的设备信息，请优先参考】\n\n";
                    for (int i = 0; i < searchResults.size(); i++) {
                        var r = searchResults.get(i);
                        webSearchContext += "[" + (i + 1) + "] " + r.getTitle() + "\n";
                        if (r.getDatePublished() != null && !r.getDatePublished().isEmpty()) {
                            webSearchContext += "   日期: " + r.getDatePublished() + "\n";
                        }
                        String content = r.getSummary() != null && !r.getSummary().isEmpty() ? r.getSummary() : r.getSnippet();
                        if (content != null && !content.isEmpty()) {
                            webSearchContext += "   内容: " + content + "\n";
                        }
                        webSearchContext += "\n";
                    }
                    webSearchContext += "【搜索信息结束】\n";
                    log.info("博查AI搜索到 {} 条设备信息", searchResults.size());
                }
            }

            // 本地缓存数据
            String localData = "";
            if (!localDevices.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("【本地缓存的设备数据】\n");
                for (int i = 0; i < Math.min(localDevices.size(), 3); i++) {
                    sb.append("--- 设备 ").append(i + 1).append(" ---\n");
                    sb.append(localDevices.get(i).toFormattedString()).append("\n");
                }
                localData = sb.toString();
                log.info("提供 {} 条本地缓存设备数据", localDevices.size());
            }

            String prompt = String.format("""
                    你是一个专业的移动电子设备专家。用户询问关于"%s"的信息。
                    当前系统时间：%s
                    
                    %s
                    %s
                    
                    请根据以上信息回答用户关于该设备的问题。
                    
                    【重要规则】
                    1. 优先使用搜索到的数据中的信息回答
                    2. 如果搜索数据包含价格信息，请告知用户当前参考价格
                    3. 如果搜索数据中有详细参数（处理器、内存、屏幕、电池、摄像头等），请详细介绍
                    4. 如果在搜索数据中都找不到相关信息，请告诉用户你知道的信息
                    5. 不知道的具体参数不要编造
                    6. 请标注信息来源，格式如 [1][2] 等
                    7. 语言自然友好，分段清晰
                    8. 绝对不要使用任何 Markdown 符号
                    9. 使用纯文本，用换行和空格来组织内容
                    
                    请直接回答用户，不要有开场白。
                    """, deviceName, currentTimeInfo,
                    localData.isEmpty() ? "" : localData,
                    webSearchContext.isEmpty() ? "" : webSearchContext);

            String result = llmService.generate(prompt);

            if (result != null && !result.isEmpty()) {
                log.info("成功生成设备回复: {}", deviceName);
                return result;
            }
        } catch (Exception e) {
            log.error("获取设备信息失败: {}", deviceName, e);
        }

        return String.format("""
                抱歉，我暂时无法找到关于"%s"的详细信息。
                
                可能的原因：
                1. 该设备尚未发布或信息不足
                2. 设备名称可能输入有误
                
                请检查设备名称后再次尝试，或询问其他设备信息。
                """, deviceName);
    }

    private String inferBrand(String deviceName) {
        if (deviceName == null) return null;
        String lower = deviceName.toLowerCase();
        if (lower.contains("苹果") || lower.contains("iphone") || lower.contains("apple")) return "苹果";
        if (lower.contains("华为") || lower.contains("huawei")) return "华为";
        if (lower.contains("小米") || lower.contains("xiaomi") || lower.contains("redmi") || lower.contains("红米")) return "小米";
        if (lower.contains("oppo") || lower.contains("一加") || lower.contains("oneplus")) return "OPPO";
        if (lower.contains("vivo") || lower.contains("iqoo")) return "vivo";
        if (lower.contains("三星") || lower.contains("samsung")) return "三星";
        if (lower.contains("荣耀")) return "荣耀";
        if (lower.contains("realme") || lower.contains("真我")) return "realme";
        if (lower.contains("魅族") || lower.contains("meizu")) return "魅族";
        if (lower.contains("索尼") || lower.contains("sony")) return "索尼";
        return null;
    }


    private String generatePurchaseResponse(String sessionId, String message, Long userId) {
        log.info("========== 开始处理购机推荐请求 ==========");
        log.info("sessionId: {}, message: {}", sessionId, message);

        // 重置上一次推荐结果，新推荐即将开始
        lastPurchaseDeviceName = null;
        lastPurchaseUsedJdData = false;
        lastPurchaseDeviceBrand = null;
        lastPurchaseDevicePrice = null;
        lastPurchaseDeviceSpecs = null;

        String conversationContext = getConversationContext(sessionId);

        String lowerMessage = message.toLowerCase();

        String brandPreference = extractBrandPreference(message);
        String budgetConstraint = extractBudgetConstraint(message, conversationContext);
        String useCasePreference = extractUseCasePreference(message, conversationContext);
        int recommendCount = extractRecommendCount(message, conversationContext);

        // 如果用户没有明确指定品牌和预算，尝试从个人偏好中读取
        if ((brandPreference == null || brandPreference.isEmpty()) && userId != null) {
            String prefBrands = userPreferenceService.getPreferredBrands(userId);
            if (prefBrands != null && !prefBrands.isEmpty()) {
                brandPreference = prefBrands;
                log.info("从用户偏好读取品牌: {}", brandPreference);
            }
        }
        if ((budgetConstraint == null || budgetConstraint.isEmpty()) && userId != null) {
            String prefBudget = userPreferenceService.getBudgetRange(userId);
            if (prefBudget != null && !prefBudget.isEmpty()) {
                budgetConstraint = prefBudget;
                log.info("从用户偏好读取预算: {}", budgetConstraint);
            }
        }

        log.info("提取到的偏好 - 品牌: '{}', 预算: '{}', 用途: '{}', 推荐数量: {}",
                brandPreference, budgetConstraint, useCasePreference, recommendCount);

        String searchQuery = buildSearchQuery(brandPreference, budgetConstraint, useCasePreference, message);
        log.info("构建的搜索查询: '{}'", searchQuery);

        List<Map<String, String>> messages = new ArrayList<>();

        String scrapedProducts = getScrapedProductsForRecommendation(message, brandPreference, budgetConstraint, useCasePreference);
        boolean hasLocalOrWebData = !scrapedProducts.isEmpty();

        // 当用户指定品牌但没有找到数据时，添加明确提示
        String brandConstraintHint = "";
        if (brandPreference != null && !brandPreference.isEmpty() && !hasLocalOrWebData) {
            brandConstraintHint = String.format("\n\n【品牌约束】用户明确要求%s品牌，但当前数据中没有找到%s设备的相关信息。请不要推荐其他品牌的设备，直接建议用户更换关键词重新搜索。", brandPreference, brandPreference);
        }

        String dataSourceInfo = hasLocalOrWebData ? "\n以下是从互联网搜索到的设备信息，请优先参考这些信息进行推荐。如果信息中包含价格请注明参考价格。" : "";

        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", String.format("""
                你是一个专业的移动电子设备选购助手。你精通各类手机、平板、手表、耳机等设备。
                当前系统时间：%s

                请根据用户的预算、品牌偏好和使用场景，推荐最适合的设备。

                %s

                %s

                %s

                【重要规则】
                1. 推荐真实可靠的设备，不要编造不存在的型号
                2. 如果用户没有指定品牌，可以从多个品牌中推荐
                3. 推荐时简要说明每个设备的优点和推荐理由
                4. 如果预算太少或太多，可以适当调整推荐范围并说明
                5. 语言简洁明了，直接给出推荐结果
                6. 必须严格遵守用户的预算约束（允许±10%%的浮动）
                7. 语言自然友好，分段清晰
                8. 绝对不要使用任何 Markdown 符号（**、*、###、- 等）
                9. 使用纯文本，用换行和空格来组织内容
                10. 第一个推荐的设备必须是最推荐的设备，给出完整的设备名称和参数信息
                11.【格式要求】每个推荐的设备必须使用以下格式开头：
                    第一推荐：完整设备名称（如：第一推荐：小米14 Pro）
                    第二推荐：完整设备名称
                    第三推荐：完整设备名称
                   这样可以方便系统识别和收藏设备
                12.【推荐多样性要求】如果推荐多款设备（2款及以上）：
                    - 第一推荐：严格按用户最偏好的品牌和预算推荐
                    - 第二推荐及之后：推荐其他品牌或不同价位的设备，让用户有对比选择的空间
                    - 例如用户偏好小米，则第一推荐小米，第二推荐其他品牌如华为/OPPO/vivo等
                    - 确保推荐的设备都是同类型（如都是手机）且真实存在
                    - 注意：如果用户本次明确指定了品牌（如"推荐小米手机"），则所有推荐都必须是该品牌

                请直接回复，不要添加额外说明。
                """, getCurrentTimeInfo(),
                dataSourceInfo.isEmpty() ? "" : "\n以下是从互联网搜索到的设备信息，请优先参考：\n" + scrapedProducts,
                dataSourceInfo,
                brandConstraintHint));
        messages.add(systemMessage);

        // 添加历史对话消息
        List<ChatMessage> historyMessages = memoryCache.get(sessionId);
        if (historyMessages != null && !historyMessages.isEmpty()) {
            int startIndex = Math.max(0, historyMessages.size() - 4);
            for (int i = startIndex; i < historyMessages.size(); i++) {
                ChatMessage historyMsg = historyMessages.get(i);
                Map<String, String> msg = new HashMap<>();
                msg.put("role", historyMsg.getRole());
                msg.put("content", historyMsg.getContent());
                messages.add(msg);
            }
        }

        // 添加当前用户消息（包含提取到的偏好信息）
        String userContent = String.format("""
                用户请求：%s

                【提取的用户偏好】
                - 品牌偏好：%s
                - 预算约束：%s
                - 使用场景：%s
                - 推荐数量：%d
                """, message,
                brandPreference.isEmpty() ? "无特殊要求" : brandPreference,
                budgetConstraint.isEmpty() ? "无特殊要求" : budgetConstraint,
                useCasePreference.isEmpty() ? "无特殊要求" : useCasePreference,
                recommendCount);

        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", userContent);
        messages.add(userMessage);

        try {
            log.info("开始调用 LLM 生成推荐回复...");
            String response = llmService.chat(messages);
            log.info("LLM 回复生成成功，长度: {}", response != null ? response.length() : 0);

            lastPurchaseUsedJdData = hasLocalOrWebData;
            if (response != null && !response.isEmpty()) {
                extractAndStoreFirstDeviceInfo(response, hasLocalOrWebData, scrapedProducts);
            } else {
                lastPurchaseDeviceName = null;
                lastPurchaseDeviceBrand = null;
                lastPurchaseDevicePrice = null;
                lastPurchaseDeviceSpecs = null;
            }

            return response;
        } catch (Exception e) {
            log.error("Purchase response generation failed: ", e);
            lastPurchaseDeviceName = null;
            lastPurchaseUsedJdData = false;
            return "抱歉，我现在无法为您提供选购建议，请稍后再试。";
        }
    }

    private void extractAndStoreFirstDeviceInfo(String reply, boolean hasJdData, String scrapedProducts) {
        lastPurchaseDeviceName = extractFirstDeviceNameFromReply(reply);
        lastPurchaseDeviceBrand = extractBrandFromDeviceName(lastPurchaseDeviceName);
        
        log.info("开始提取设备信息: deviceName={}", lastPurchaseDeviceName);

        // 只提取产品名和品牌，不提取参数（收藏只显示名称和价格）
        if (hasJdData && lastPurchaseDeviceName != null && !scrapedProducts.isEmpty()) {
            String[] lines = scrapedProducts.split("\n");
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i].trim();
                if (lastPurchaseDeviceName != null && line.contains(lastPurchaseDeviceName.substring(0, Math.min(4, lastPurchaseDeviceName.length())))) {
                    // 只提取价格
                    for (int j = i; j < Math.min(lines.length, i + 8); j++) {
                        String specLine = lines[j].trim();
                        if (specLine.startsWith("商品") && j > i) break;
                        if (!specLine.isEmpty() && !specLine.startsWith("商品")) {
                            if (specLine.contains("价格")) {
                                lastPurchaseDevicePrice = specLine.replace("价格：", "").replace("价格:", "").trim();
                                break;
                            }
                        }
                    }
                    break;
                }
            }
        }

        // 如果没有从JD提取到价格，尝试从AI回复中提取
        if (lastPurchaseDevicePrice == null || lastPurchaseDevicePrice.isEmpty()) {
            lastPurchaseDevicePrice = extractPriceFromReply(reply, lastPurchaseDeviceName);
        }

        // 不再提取参数，收藏只显示产品名和价格
        lastPurchaseDeviceSpecs = null;

        log.info("最终设备信息: name={}, brand={}, price={}",
                lastPurchaseDeviceName, lastPurchaseDeviceBrand, lastPurchaseDevicePrice);
    }

    private String extractBrandFromDeviceName(String deviceName) {
        if (deviceName == null) return null;
        String[] brands = {"华为", "小米", "OPPO", "vivo", "三星", "荣耀", "一加", "realme", "魅族", "索尼", "苹果", "Bose", "JBL", "森海塞尔", "AKG", "佳明", "Fitbit"};
        for (String brand : brands) {
            if (deviceName.startsWith(brand) || deviceName.contains(brand)) {
                return brand;
            }
        }
        if (deviceName.toLowerCase().startsWith("iphone") || deviceName.toLowerCase().startsWith("airpods")
                || deviceName.toLowerCase().startsWith("ipad") || deviceName.toLowerCase().startsWith("apple")) {
            return "苹果";
        }
        return "";
    }

    private String extractPriceFromReply(String reply, String deviceName) {
        if (reply == null || deviceName == null) return null;
        int deviceIndex = reply.indexOf(deviceName);
        if (deviceIndex < 0) {
            // 尝试从设备名称中提取前几个字符来查找
            if (deviceName.length() > 4) {
                deviceIndex = reply.indexOf(deviceName.substring(0, 4));
            }
            if (deviceIndex < 0) {
                // 仍然找不到，从开头开始查找
                deviceIndex = 0;
            }
        }
        String afterDevice = reply.substring(deviceIndex, Math.min(reply.length(), deviceIndex + 800));
        
        // 只在第一个推荐设备的范围内查找
        int secondRecIndex = -1;
        String[] stopMarkers = {"第二个推荐", "第三个推荐", "第二个：", "第二个:", "第三个：", "第三个:", "2.", "3."};
        for (String marker : stopMarkers) {
            int idx = afterDevice.indexOf(marker);
            if (idx > 0 && (secondRecIndex < 0 || idx < secondRecIndex)) {
                secondRecIndex = idx;
            }
        }
        
        String searchText = secondRecIndex > 0 ? afterDevice.substring(0, secondRecIndex) : afterDevice;
        
        // 多种价格格式匹配（支持1-6位数字）
        String[] pricePatterns = {
            "(\\d{2,6})\\s*元",
            "约?(\\d{2,6})\\s*元",
            "价格[：:]\\s*(\\d{2,6})",
            "售价[：:]\\s*(\\d{2,6})",
            "参考价[：:]\\s*(\\d{2,6})",
            "¥?(\\d{2,6})",
            "(\\d{2,6})\\s*元左右",
            "(\\d{2,6})\\s*元起"
        };
        
        for (String patternStr : pricePatterns) {
            Pattern pricePattern = Pattern.compile(patternStr);
            Matcher m = pricePattern.matcher(searchText);
            if (m.find()) {
                String price = m.group(1) + "元";
                log.info("extractPriceFromReply - 从回复中提取的价格: {}", price);
                return price;
            }
        }
        
        log.info("extractPriceFromReply - 未能从回复中提取到价格");
        return null;
    }

    private String extractSpecsFromReplyStrict(String reply, String deviceName) {
        if (reply == null || deviceName == null) return null;
        log.info("extractSpecsFromReplyStrict - 开始提取参数，设备名: {}", deviceName);
        
        int deviceIndex = reply.indexOf(deviceName);
        if (deviceIndex < 0 && deviceName.length() > 4) {
            deviceIndex = reply.indexOf(deviceName.substring(0, 4));
        }
        if (deviceIndex < 0) {
            log.info("extractSpecsFromReplyStrict - 未找到设备名称");
            return null;
        }
        
        // 严格限制搜索范围：只提取设备名后800字符内的内容
        String afterDevice = reply.substring(deviceIndex, Math.min(reply.length(), deviceIndex + 800));
        log.info("extractSpecsFromReplyStrict - 搜索范围: {} 字符", afterDevice.length());
        
        String[] lines = afterDevice.split("\n");
        StringBuilder specs = new StringBuilder();
        int lineCount = 0;
        boolean foundDevice = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // 确认找到了当前设备
            if (!foundDevice && (line.contains(deviceName) || 
                (deviceName.length() > 3 && line.contains(deviceName.substring(0, 3))))) {
                foundDevice = true;
            }
            
            // 严格停止条件：遇到任何其他推荐就立即停止
            if (foundDevice && lineCount > 2 &&
                (line.contains("第二个") || line.contains("第三个") || line.contains("另一个") ||
                 line.startsWith("2.") || line.startsWith("3.") ||
                 line.contains("备选") || line.contains("可选") ||
                 line.contains("此外") || line.contains("另外") ||
                 (line.matches("^[\\*\\-]?\\d+[\\.、].*") && !line.contains(deviceName)))) {
                log.info("extractSpecsFromReplyStrict - 遇到边界标记，停止: {}", line.substring(0, Math.min(30, line.length())));
                break;
            }
            
            // 只提取核心参数，排除推荐理由等
            boolean isCoreSpec = false;
            String[] coreKeywords = {"屏幕", "处理器", "内存", "存储", "电池", "摄像头", 
                                    "充电", "重量", "尺寸", "分辨率", "刷新率", "芯片",
                                    "像素", "防水", "mAh", "Hz", "英寸", "W", "GB",
                                    "CPU", "GPU", "RAM", "ROM"};
            
            for (String keyword : coreKeywords) {
                if (line.contains(keyword)) {
                    isCoreSpec = true;
                    break;
                }
            }
            
            // 也匹配"参数："格式的行
            if ((line.contains("：") || line.contains(":")) && 
                line.length() < 100 && !line.startsWith("推荐")) {
                isCoreSpec = true;
            }
            
            if (isCoreSpec && foundDevice) {
                // 推荐理由和总结性语句排除
                if (line.startsWith("推荐理由") || line.startsWith("推荐原因") || 
                    line.startsWith("【") || line.contains("总结") || line.contains("注意")) {
                    continue;
                }
                
                // 清理格式
                line = line.replaceAll("^\\*", "").replaceAll("^-", "").replaceAll("^\\d+\\.", "").trim();
                if (line.length() > 5 && line.length() < 150) {
                    if (specs.length() > 0) specs.append(" | ");
                    specs.append(line);
                    lineCount++;
                    
                    // 严格限制最多10行
                    if (lineCount >= 10) {
                        log.info("extractSpecsFromReplyStrict - 达到最大行数限制");
                        break;
                    }
                }
            }
        }
        
        String result = specs.length() > 0 ? specs.toString() : null;
        log.info("extractSpecsFromReplyStrict - 提取完成，结果长度: {}", result != null ? result.length() : 0);
        return result;
    }

    private String extractSpecsFromReply(String reply, String deviceName) {
        if (reply == null || deviceName == null) return null;
        log.info("extractSpecsFromReply - 开始提取参数，设备名: {}, 回复长度: {}", deviceName, reply.length());
        
        // 查找设备名称的位置，更宽松的查找方式
        int deviceIndex = -1;
        
        // 首先尝试完整匹配
        deviceIndex = reply.indexOf(deviceName);
        
        // 如果没找到，尝试使用设备名的部分内容查找
        if (deviceIndex < 0 && deviceName.length() > 6) {
            String shortName = deviceName.substring(0, Math.min(6, deviceName.length()));
            deviceIndex = reply.indexOf(shortName);
        }
        
        // 如果还是没找到，尝试查找常见的设备名模式
        if (deviceIndex < 0) {
            // 查找"iPhone"或其他品牌的起始位置
            String[] brandPrefixes = {"iPhone", "华为", "小米", "OPPO", "vivo", "三星", "荣耀", "一加"};
            for (String prefix : brandPrefixes) {
                if (reply.contains(prefix)) {
                    deviceIndex = reply.indexOf(prefix);
                    break;
                }
            }
        }
        
        // 如果都找不到，从开头开始
        if (deviceIndex < 0) {
            deviceIndex = 0;
        }
        
        log.info("extractSpecsFromReply - 找到设备起始位置: {}", deviceIndex);
        
        // 截取从设备开始到下一个推荐之间的内容，加长搜索范围
        String afterDevice = reply.substring(deviceIndex, Math.min(reply.length(), deviceIndex + 2500));
        log.info("extractSpecsFromReply - 截取的内容长度: {}", afterDevice.length());
        
        String[] lines = afterDevice.split("\n");
        StringBuilder specs = new StringBuilder();
        int lineCount = 0;
        boolean foundDevice = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            // 先确认我们找到了设备相关的内容
            if (!foundDevice) {
                if (line.contains(deviceName) || (deviceName.length() > 4 && line.contains(deviceName.substring(0, 4)))) {
                    foundDevice = true;
                }
            }
            
            // 遇到第二个/第三个推荐的标记就停止
            if (line.contains("第二个推荐") || line.contains("第三个推荐") || 
                line.startsWith("第二个：") || line.startsWith("第二个:") || 
                line.startsWith("第三个：") || line.startsWith("第三个:") ||
                line.startsWith("2.") || line.startsWith("3.")) {
                log.info("extractSpecsFromReply - 遇到下一个推荐标记，停止提取");
                break;
            }
            
            // 遇到明显的总结性语句也停止
            if (line.contains("其他可选") || line.contains("请注意") || line.contains("总结") ||
                line.startsWith("【") && line.endsWith("】")) {
                log.info("extractSpecsFromReply - 遇到总结性语句，停止提取");
                break;
            }
            
            // 更宽松的匹配条件
            boolean isSpecLine = false;
            
            // 检查是否包含参数相关关键词
            String[] specKeywords = {"屏幕", "处理器", "内存", "存储", "电池", "摄像头", 
                                    "充电", "重量", "尺寸", "续航", "降噪", "音质", 
                                    "刷新率", "分辨率", "芯片", "像素", "防水", 
                                    "厚度", "网络", "系统", "颜色", "版本", "英寸",
                                    "CPU", "GPU", "RAM", "ROM", "mAh", "W", "Hz", "K"};
            for (String keyword : specKeywords) {
                if (line.contains(keyword)) {
                    isSpecLine = true;
                    break;
                }
            }
            
            // 检查是否包含冒号（中文或英文）
            if (line.contains("：") || line.contains(":")) {
                isSpecLine = true;
            }
            
            // 检查是否是数字开头的参数描述
            if (Character.isDigit(line.charAt(0)) && line.length() > 5) {
                isSpecLine = true;
            }
            
            // 检查是否是参数信息行
            if (isSpecLine) {
                // 排除一些明显非参数相关的句子，但不要太严格
                if (line.startsWith("推荐理由") || line.startsWith("推荐原因") || 
                    line.startsWith("【推荐理由】")) {
                    continue;
                }
                
                // 清理行内容
                line = line.replaceAll("^\\*", "").replaceAll("^-", "").replaceAll("^\\d+\\.", "").trim();
                if (line.isEmpty()) continue;
                
                if (specs.length() > 0) specs.append(" | ");
                specs.append(line);
                lineCount++;
                log.info("extractSpecsFromReply - 添加参数行: {}", line.substring(0, Math.min(50, line.length())));
            }
            
            // 限制提取的行数，稍微增加一些
            if (lineCount >= 20) break;
        }
        
        String result = specs.length() > 0 ? specs.toString() : null;
        log.info("extractSpecsFromReply - 最终提取的参数: {}", result != null ? result.substring(0, Math.min(200, result.length())) : "null");
        return result;
    }

    private String getScrapedProductsForRecommendation(String message, String brand, String budget, String useCase) {
        try {
            // 先搜索本地缓存
            String searchQuery = brand + " " + useCase;
            if (searchQuery.trim().isEmpty()) {
                searchQuery = "手机 平板 智能设备";
            }
            
            List<DeviceDataService.ScrapedDevice> localDevices = deviceDataService.searchDevices(searchQuery);
            String localData = "";
            if (!localDevices.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < Math.min(localDevices.size(), 8); i++) {
                    sb.append("商品").append(i + 1).append(":\n");
                    sb.append(localDevices.get(i).toFormattedString()).append("\n\n");
                }
                localData = sb.toString();
                log.info("购机推荐提供 {} 条本地缓存数据", Math.min(localDevices.size(), 8));
            }

            // 执行博查AI Web搜索获取实时推荐信息
            String webSearchData = "";
            if (webSearchService.isEnabled()) {
                String webQuery = (brand != null && !brand.isEmpty() ? brand + " " : "") 
                                + (useCase != null && !useCase.isEmpty() ? useCase + " " : "") 
                                + "手机推荐 2025 2026 性价比";
                log.info("执行博查AI Web搜索购机推荐: {}", webQuery);
                var searchResults = webSearchService.search(webQuery, 8);
                if (!searchResults.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("\n【以下是从互联网搜索到的购机推荐信息】\n\n");
                    for (int i = 0; i < searchResults.size(); i++) {
                        var r = searchResults.get(i);
                        sb.append("[").append(i + 1).append("] ").append(r.getTitle()).append("\n");
                        if (r.getSiteName() != null && !r.getSiteName().isEmpty()) {
                            sb.append("   来源: ").append(r.getSiteName()).append("\n");
                        }
                        String content = r.getSummary() != null && !r.getSummary().isEmpty() ? r.getSummary() : r.getSnippet();
                        if (content != null && !content.isEmpty()) {
                            sb.append("   内容: ").append(content).append("\n");
                        }
                        sb.append("\n");
                    }
                    sb.append("【搜索信息结束】\n");
                    sb.append("请参考以上搜索结果，结合用户的预算和需求推荐合适的设备。");
                    webSearchData = sb.toString();
                    log.info("博查AI搜索到 {} 条推荐信息", searchResults.size());
                }
            }

            // 合并本地数据和Web搜索数据
            return localData + webSearchData;
            
        } catch (Exception e) {
            log.warn("获取推荐用商品数据失败: {}", e.getMessage());
            return "";
        }
    }

    /**
     * 根据预算 + 发布年份对设备列表进行智能排序
     * 预算充足时（>=5000）强力偏好近1-2年新品，预算紧张时兼顾性价比
     */
    private List<DeviceDataService.ScrapedDevice> sortDevicesForRecommendation(
            List<DeviceDataService.ScrapedDevice> devices, String budget) {

        if (devices == null || devices.isEmpty()) return devices;

        // 去重
        Set<String> seen = new java.util.LinkedHashSet<>();
        List<DeviceDataService.ScrapedDevice> unique = new ArrayList<>();
        for (DeviceDataService.ScrapedDevice d : devices) {
            if (d.getName() != null && seen.add(d.getName().toLowerCase().replaceAll("\\s", ""))) {
                unique.add(d);
            }
        }

        int budgetNum = 0;
        if (budget != null && !budget.isEmpty()) {
            try {
                budgetNum = Integer.parseInt(budget.replaceAll("[^0-9]", ""));
            } catch (NumberFormatException ignored) {}
        }

        int currentYear = java.time.Year.now().getValue();
        final int finalBudget = budgetNum;

        // 按综合评分排序
        unique.sort((a, b) -> {
            double scoreA = scoreDevice(a, finalBudget, currentYear);
            double scoreB = scoreDevice(b, finalBudget, currentYear);
            return Double.compare(scoreB, scoreA); // 降序
        });

        return unique;
    }

    private double scoreDevice(DeviceDataService.ScrapedDevice device, int budgetNum, int currentYear) {
        double score = 0.0;

        // 预算适配度（满分 40）
        if (budgetNum > 0 && device.getPrice() != null) {
            try {
                int price = Integer.parseInt(device.getPrice().replaceAll("[^0-9]", ""));
                if (price <= budgetNum) {
                    score += 40.0 * (1.0 - (budgetNum - price) / (double) budgetNum * 0.3);
                } else if (price <= budgetNum * 1.15) {
                    score += 20.0;
                }
            } catch (NumberFormatException ignored) {}
        } else {
            score += 30.0;
        }

        // 新机偏好度（满分 60）：预算越高权重越大
        Integer releaseYear = device.getReleaseYear();
        if (releaseYear != null && releaseYear > 0) {
            int age = currentYear - releaseYear;
            double weight;
            if (budgetNum >= 8000) weight = 2.0;      // 预算充裕：强力偏好新机
            else if (budgetNum >= 5000) weight = 1.5;  // 预算充足：偏好新机
            else if (budgetNum >= 3000) weight = 1.2;  // 中等预算：温和偏好
            else weight = 1.0;                          // 低预算：一般偏好

            double recencyScore = Math.max(0, 60.0 - age * 15.0);
            score += recencyScore * weight;
        } else {
            score += 10.0;
        }

        return score;
    }

    private String getDeviceInfoFromDeepSeekForRecommendation(String brand, String budget, String useCase, int count) {
        log.info("========== 开始调用 DeepSeek 获取推荐设备 ==========");
        log.info("参数 - 品牌: '{}', 预算: '{}', 用途: '{}', 数量: {}", brand, budget, useCase, count);

        try {
            String prompt = String.format("""
                    你是一个专业的移动电子设备数据库助手。
                    
                    请提供%d款适合以下需求的真实设备信息：
                    - 品牌：%s
                    - 预算：%s
                    - 使用场景：%s
                    
                    对于每款设备，请提供：
                    - 设备名称
                    - 品牌
                    - 处理器
                    - 内存
                    - 存储
                    - 电池
                    - 摄像头
                    - 价格（人民币）
                    
                    【重要规则】
                    1. 只返回真实的、已发布的设备信息
                    2. 如果指定了品牌，只返回该品牌的设备
                    3. 不要编造任何设备
                    4. 每款设备之间用空行分隔
                    5. 使用简洁的格式，每行一个配置项
                    6. 不要使用 Markdown 符号
                    7. 如果暂时无法提供信息，也请返回一个占位说明
                    """, count,
                    brand.isEmpty() ? "不限" : brand,
                    budget.isEmpty() ? "不限" : budget,
                    useCase.isEmpty() ? "日常使用" : useCase);

            log.info("发送给 DeepSeek 的提示词长度: {} 字符", prompt.length());
            log.info("开始调用 llmService.generate()...");

            long startTime = System.currentTimeMillis();
            String result = llmService.generate(prompt);
            long endTime = System.currentTimeMillis();

            log.info("DeepSeek 调用耗时: {} ms", endTime - startTime);
            log.info("DeepSeek 返回结果: {}", result != null ? "非空" : "null");
            log.info("DeepSeek 返回结果长度: {}", result != null ? result.length() : 0);

            if (result != null && result.length() > 50) {
                log.info("DeepSeek 返回结果预览: {}", result.substring(0, Math.min(200, result.length())));
            } else if (result != null) {
                log.info("DeepSeek 返回短结果: {}", result);
            }

            if (result != null && !result.isEmpty()) {
                log.info("========== DeepSeek 调用成功 ==========");
                return result;
            } else {
                log.warn("========== DeepSeek 返回空结果 ==========");
            }
        } catch (Exception e) {
            log.error("========== DeepSeek 调用异常 ==========", e);
            log.error("异常类型: {}", e.getClass().getName());
            log.error("异常信息: {}", e.getMessage());
        }
        return "";
    }

    /**
     * 从数据库中查询指定品牌和预算的设备信息
     * @param brand 品牌偏好
     * @param budget 预算约束
     * @param count 需要的设备数量
     * @return 格式化的设备信息字符串
     */
// ... existing code ...

        private int extractRecommendCount(String message, String conversationContext) {
        String lowerMessage = message.toLowerCase();
        String lowerContext = conversationContext.toLowerCase();

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d+)\s*(部|个|款)");

        var matcher = pattern.matcher(lowerMessage);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        matcher = pattern.matcher(lowerContext);
        if (matcher.find()) {
            return Integer.parseInt(matcher.group(1));
        }

        if (lowerMessage.contains("一部") || lowerMessage.contains("一个") ||
                lowerMessage.contains("一款") || lowerMessage.contains("推荐.*手机")) {
            return 1;
        }

        return 3;
    }


    private String extractBrandPreference(String message) {
        String lowerMessage = message.toLowerCase();

        String[] brands = {"小米", "xiaomi", "华为", "huawei", "苹果", "iphone",
                "三星", "samsung", "oppo", "vivo", "荣耀", "honor",
                "一加", "oneplus", "魅族", "meizu", "realme", "iqoo"};

        for (String brand : brands) {
            if (lowerMessage.contains(brand)) {
                return brand;
            }
        }
        return "";
    }

    private String extractBudgetConstraint(String message, String conversationContext) {
        String lowerMessage = message.toLowerCase();
        String lowerContext = conversationContext.toLowerCase();

        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{3,5})\\s*元");

        var matcher = pattern.matcher(lowerMessage);
        if (matcher.find()) {
            return matcher.group(1) + "元";
        }

        matcher = pattern.matcher(lowerContext);
        if (matcher.find()) {
            return matcher.group(1) + "元";
        }

        if (lowerMessage.contains("千元") || lowerContext.contains("千元")) {
            return "1000-2000元";
        }

        return "";
    }

    private String extractUseCasePreference(String message, String conversationContext) {
        String lowerMessage = message.toLowerCase();
        String lowerContext = conversationContext.toLowerCase();

        String[] useCases = {"游戏", "拍照", "续航", "办公", "日常",
                "运动", "音乐", "学习", "商务", "娱乐"};

        for (String useCase : useCases) {
            if (lowerMessage.contains(useCase)) {
                return useCase;
            }
        }

        for (String useCase : useCases) {
            if (lowerContext.contains(useCase)) {
                return useCase;
            }
        }

        return "";
    }

    private String buildSearchQuery(String brand, String budget, String useCase, String message) {
        StringBuilder query = new StringBuilder();

        if (!brand.isEmpty()) {
            query.append(brand).append(" ");
        }

        if (!budget.isEmpty()) {
            query.append(budget).append(" ");
        }

        if (!useCase.isEmpty()) {
            query.append(useCase).append(" ");
        }

        query.append("手机");

        return query.toString().trim();
    }


    private String generateBindResponse(String message) {
        String prompt = String.format("""
                你是一个专业的手机设备管理助手，负责处理设备绑定请求。
                当前系统时间：%s

                用户消息：%s

                请解释设备绑定功能的作用和好处，
                并告知用户该功能正在开发中，预计下周上线。

                【重要规则】
                1. 绝对不要使用任何 Markdown 符号（**、*、###、- 等）
                2. 使用纯文本，用换行和空格来组织内容
                3. 语言自然友好，分段清晰

                请直接回复，不要使用任何特殊符号。
                """, getCurrentTimeInfo(), message);

        try {
            return llmService.generate(prompt);
        } catch (Exception e) {
            log.error("Bind response generation failed: ", e);
            return "抱歉，我现在无法处理您的设备绑定请求，请稍后再试。";
        }
    }

    private String generateFavoriteOperationResponse(String message, Long userId) {
        log.info("处理收藏操作请求: {}", message);
        String lowerMsg = message.toLowerCase().trim();
        
        // 处理数字选择（用户回复1、2、3等）
        if (userId != null && lowerMsg.matches("^\\d+$")) {
            int selection = Integer.parseInt(lowerMsg);
            List<FavoriteDeviceEntity> allFavorites = favoriteDeviceRepository.findByUserIdOrderByCreatedAtDesc(String.valueOf(userId));
            if (selection > 0 && selection <= allFavorites.size()) {
                FavoriteDeviceEntity fav = allFavorites.get(selection - 1);
                return String.format("[ACTION_REQUIRED]\n{\n  \"action\": \"favorite_delete\",\n  \"deviceName\": \"%s\",\n  \"confirmMessage\": \"确定要删除收藏设备 %s 吗？\",\n  \"redirectPage\": \"favorite\"\n}", fav.getDeviceName(), fav.getDeviceName());
            }
        }
        
        if (lowerMsg.contains("取消收藏") || lowerMsg.contains("删除收藏") || lowerMsg.contains("移除收藏")) {
            if (userId != null) {
                String deviceName = extractDeviceName(message);
                if (deviceName != null && !deviceName.isEmpty()) {
                    // 提取关键词（去除存储规格等次要信息）
                    String keywords = extractKeywordsFromDeviceName(deviceName);
                    log.info("提取的关键词: {}", keywords);
                    
                    // 使用关键词匹配收藏项
                    List<FavoriteDeviceEntity> matchedFavorites = matchFavoritesByKeywords(String.valueOf(userId), keywords);
                    
                    if (matchedFavorites.isEmpty()) {
                        return "未找到匹配的收藏设备。请检查设备名称或查看您的收藏列表。";
                    } else if (matchedFavorites.size() == 1) {
                        // 只匹配到一个，直接返回删除确认
                        FavoriteDeviceEntity fav = matchedFavorites.get(0);
                        return String.format("[ACTION_REQUIRED]\n{\n  \"action\": \"favorite_delete\",\n  \"deviceName\": \"%s\",\n  \"confirmMessage\": \"确定要删除收藏设备 %s 吗？\",\n  \"redirectPage\": \"favorite\"\n}", fav.getDeviceName(), fav.getDeviceName());
                    } else {
                        // 匹配到多个，返回选择列表
                        StringBuilder sb = new StringBuilder();
                        sb.append("找到多个匹配的收藏设备，请选择要取消收藏的设备：\n\n");
                        for (int i = 0; i < matchedFavorites.size(); i++) {
                            FavoriteDeviceEntity fav = matchedFavorites.get(i);
                            sb.append(i + 1).append(". ").append(fav.getDeviceName());
                            if (fav.getBrand() != null && !fav.getBrand().isEmpty()) {
                                sb.append("（").append(fav.getBrand()).append("）");
                            }
                            if (fav.getPrice() != null && !fav.getPrice().isEmpty()) {
                                sb.append(" - ").append(fav.getPrice());
                            }
                            sb.append("\n");
                        }
                        sb.append("\n请告诉我您要取消收藏第几个设备，或者直接说出设备名称。");
                        return sb.toString();
                    }
                }
            }
            return "[ACTION_REQUIRED]\n{\n  \"action\": \"open_favorite_page\",\n  \"confirmMessage\": \"\",\n  \"redirectPage\": \"favorite\"\n}";
        }
        
        if (lowerMsg.contains("收藏") && (lowerMsg.contains("查看") || lowerMsg.contains("打开") || lowerMsg.contains("列表") || lowerMsg.contains("哪些") || lowerMsg.contains("什么") || lowerMsg.contains("我的收藏"))) {
            // 直接查询数据库输出收藏列表
            if (userId != null) {
                List<FavoriteDeviceEntity> favorites = favoriteDeviceRepository.findByUserIdOrderByCreatedAtDesc(String.valueOf(userId));
                if (favorites.isEmpty()) {
                    return "您目前还没有收藏任何设备。您可以在聊天中让我推荐设备，然后点击收藏按钮来收藏您喜欢的设备。";
                }
                StringBuilder sb = new StringBuilder();
                sb.append("您的收藏列表：\n\n");
                for (int i = 0; i < favorites.size(); i++) {
                    FavoriteDeviceEntity fav = favorites.get(i);
                    sb.append(i + 1).append(". ").append(fav.getDeviceName());
                    if (fav.getBrand() != null && !fav.getBrand().isEmpty()) {
                        sb.append("（").append(fav.getBrand()).append("）");
                    }
                    if (fav.getPrice() != null && !fav.getPrice().isEmpty()) {
                        sb.append(" - ").append(fav.getPrice());
                    }
                    sb.append("\n");
                }
                sb.append("\n如需取消收藏某台设备，请告诉我设备名称。");
                return sb.toString();
            }
            return "请先登录后再查看收藏列表。";
        }
        
        // 其他包含"收藏"的意图视为添加收藏
        if (lowerMsg.contains("收藏")) {
            String deviceName = extractDeviceName(message);
            if (deviceName != null && !deviceName.isEmpty()) {
                return String.format("[ACTION_REQUIRED]\n{\n  \"action\": \"favorite_add\",\n  \"deviceName\": \"%s\",\n  \"confirmMessage\": \"确定要收藏设备 %s 吗？\",\n  \"redirectPage\": \"favorite\"\n}", deviceName, deviceName);
            }
            return "请告诉我您想收藏的设备名称。";
        }
        
        return "[ACTION_REQUIRED]\n{\n  \"action\": \"open_favorite_page\",\n  \"confirmMessage\": \"\",\n  \"redirectPage\": \"favorite\"\n}";
    }

    private String generateMyDeviceOperationResponse(String message, Long userId) {
        log.info("处理我的设备操作请求: {}", message);
        String lowerMsg = message.toLowerCase();
        
        if (lowerMsg.contains("删除设备") || lowerMsg.contains("移除设备")) {
            String deviceName = extractDeviceName(message);
            if (deviceName != null && !deviceName.isEmpty()) {
                return String.format("[ACTION_REQUIRED]\n{\n  \"action\": \"mydevice_delete\",\n  \"deviceName\": \"%s\",\n  \"confirmMessage\": \"确定要删除设备 %s 吗？此操作无法撤销。\",\n  \"redirectPage\": \"mydevice\"\n}", deviceName, deviceName);
            }
            return "[ACTION_REQUIRED]\n{\n  \"action\": \"open_mydevice_page\",\n  \"confirmMessage\": \"\",\n  \"redirectPage\": \"mydevice\"\n}";
        }
        
        if (lowerMsg.contains("修改设备") || lowerMsg.contains("编辑设备")) {
            String deviceName = extractDeviceName(message);
            if (deviceName != null && !deviceName.isEmpty()) {
                return String.format("[ACTION_REQUIRED]\n{\n  \"action\": \"mydevice_edit\",\n  \"deviceName\": \"%s\",\n  \"confirmMessage\": \"\",\n  \"redirectPage\": \"mydevice\"\n}", deviceName);
            }
            return "[ACTION_REQUIRED]\n{\n  \"action\": \"open_mydevice_page\",\n  \"confirmMessage\": \"\",\n  \"redirectPage\": \"mydevice\"\n}";
        }
        
        if (lowerMsg.contains("添加设备")) {
            String deviceName = extractDeviceName(message);
            if (deviceName != null && !deviceName.isEmpty()) {
                return String.format("[ACTION_REQUIRED]\n{\n  \"action\": \"mydevice_add\",\n  \"deviceName\": \"%s\",\n  \"confirmMessage\": \"确定要添加设备 %s 吗？\",\n  \"redirectPage\": \"mydevice\"\n}", deviceName, deviceName);
            }
            return "[ACTION_REQUIRED]\n{\n  \"action\": \"open_mydevice_page\",\n  \"confirmMessage\": \"\",\n  \"redirectPage\": \"mydevice\"\n}";
        }
        
        if (lowerMsg.contains("查看设备") || lowerMsg.contains("我的设备") || 
            lowerMsg.contains("我有哪些设备") || lowerMsg.contains("我有什么设备") ||
            lowerMsg.contains("我拥有的设备")) {
            String deviceName = extractDeviceName(message);
            // 防止extractDeviceName返回无效设备名（如返回"我有哪些设备"本身）
            if (deviceName != null && !deviceName.isEmpty() 
                && !deviceName.equals(message.trim())
                && !lowerMsg.matches(".*(我的设备|我有哪些设备|我有什么设备|我拥有的设备|查看设备).*")) {
                return String.format("[ACTION_REQUIRED]\n{\n  \"action\": \"mydevice_detail\",\n  \"deviceName\": \"%s\",\n  \"confirmMessage\": \"\",\n  \"redirectPage\": \"mydevice\"\n}", deviceName);
            }
            
            // 直接查询用户的设备列表并返回
            if (userId != null) {
                List<MyDeviceEntity> devices = myDeviceRepository.findByUserIdOrderByCreatedAtDesc(userId);
                if (devices.isEmpty()) {
                    return "您目前还没有添加任何设备。您可以点击右侧导航栏的\"我的设备\"来添加您的第一台设备。";
                }
                StringBuilder sb = new StringBuilder();
                sb.append("您当前拥有以下设备：\n\n");
                for (int i = 0; i < devices.size(); i++) {
                    MyDeviceEntity device = devices.get(i);
                    sb.append(i + 1).append(". ").append(device.getDeviceName());
                    if (device.getBrand() != null && !device.getBrand().isEmpty()) {
                        sb.append("（").append(device.getBrand()).append("）");
                    }
                    if (device.getRam() != null || device.getRom() != null) {
                        sb.append(" - ").append(device.getRam() != null ? device.getRam() : "").append(device.getRam() != null && device.getRom() != null ? "+" : "").append(device.getRom() != null ? device.getRom() : "");
                    }
                    if (device.getSnCode() != null && !device.getSnCode().isEmpty()) {
                        sb.append("\n   SN码：").append(device.getSnCode());
                    }
                    sb.append("\n");
                }
                sb.append("\n如果您想查看某台设备的详细信息或保修状态，请告诉我设备名称。");
                return sb.toString();
            }
        }
        
        return "[ACTION_REQUIRED]\n{\n  \"action\": \"open_mydevice_page\",\n  \"confirmMessage\": \"\",\n  \"redirectPage\": \"mydevice\"\n}";
    }

    private String generateHistoryResponse(String memoryKey, String message) {
        log.info("开始处理历史记录查询请求, memoryKey={}, message={}", memoryKey, message);
        String rawSessionId = extractSessionId(memoryKey);
        Long userId = extractUserIdFromMemoryKey(memoryKey);
        if (userId == null) userId = 0L;
        String lowerMsg = message.toLowerCase();

        // === 询问的是推荐过的设备 ===
        boolean askingAboutRecommendations = lowerMsg.contains("推荐") 
                || lowerMsg.contains("选购") || lowerMsg.contains("买");
        if (askingAboutRecommendations) {
            List<String> recDevices = getRecommendedDevices(rawSessionId);
            if (!recDevices.isEmpty()) {
                StringBuilder reply = new StringBuilder("根据推荐记录，我之前为您推荐过以下设备：\n\n");
                int count = 1;
                for (String device : recDevices) {
                    reply.append(count).append(". ").append(device).append("\n");
                    count++;
                }
                reply.append("\n如果您想了解某款设备的更多信息，请告诉我。");
                log.info("使用推荐追踪列表回复（{} 个设备）", recDevices.size());
                return reply.toString();
            }
        }

        // === 询问的是对比过的设备 ===
        boolean askingAboutComparison = lowerMsg.contains("对比") || lowerMsg.contains("比较");
        if (askingAboutComparison) {
            // 从数据库获取对比记录（暂时使用提到的设备列表）
            Set<String> mentionedDevices = getMentionedDevices(rawSessionId);
            if (mentionedDevices.size() >= 2) {
                StringBuilder reply = new StringBuilder("根据记录，我们在本次对话中讨论过以下设备：\n\n");
                int count = 1;
                for (String device : mentionedDevices) {
                    reply.append(count).append(". ").append(device).append("\n");
                    count++;
                }
                reply.append("\n如果您想重新对比或了解其他设备，请告诉我。");
                log.info("使用设备列表回复对比查询（{} 个设备）", mentionedDevices.size());
                return reply.toString();
            }
        }

        // === 使用追踪的设备列表（一般性的"之前问了什么"） ===
        Set<String> trackedDevices = getMentionedDevices(rawSessionId);
        if (!trackedDevices.isEmpty()) {
            log.info("从数据库追踪列表获取到 {} 个设备", trackedDevices.size());

            String targetDevice = null;
            for (String device : trackedDevices) {
                if (lowerMsg.contains(device.toLowerCase())) {
                    targetDevice = device;
                    break;
                }
            }

            if (targetDevice != null) {
                return String.format("根据对话记录，您之前询问过\"%s\"。如果需要了解该设备的详细信息，请告诉我。", targetDevice);
            }

            StringBuilder reply = new StringBuilder("根据我们的对话记录，您之前询问过以下设备：\n\n");
            int count = 1;
            for (String device : trackedDevices) {
                reply.append(count).append(". ").append(device).append("\n");
                count++;
            }
            reply.append("\n如果您需要了解更多细节或有其他问题，随时告诉我！");
            return reply.toString();
        }

        // === 如果数据库追踪列表为空，检查是否有历史消息 ===
        List<ChatMessage> messages = memoryCache.get(memoryKey);
        if (messages == null || messages.isEmpty()) {
            log.warn("memoryKey {} 没有历史消息，返回空记录提示", memoryKey);
            return "目前我们还没有对话记录。您可以开始提问，我会记住我们的对话内容。";
        }

        log.info("从内存缓存历史消息中提取设备，消息数: {}", messages.size());
        Set<String> mentionedDevices = new LinkedHashSet<>();
        for (ChatMessage msg : messages) {
            String content = msg.getContent();
            if (content != null && !content.isEmpty()) {
                extractDevicesFromMessage(content, mentionedDevices);
            }
        }

        if (mentionedDevices.isEmpty()) {
            return "我们的对话中还没有涉及到具体的设备查询。您可以问我关于手机、平板、手表或耳机的信息。";
        }

        StringBuilder reply = new StringBuilder("根据我们的对话记录，您之前询问过以下设备：\n\n");
        int count = 1;
        for (String device : mentionedDevices) {
            reply.append(count).append(". ").append(device).append("\n");
            count++;
        }
        reply.append("\n如果您需要了解更多细节或有其他问题，随时告诉我！");
        return reply.toString();
    }

    /**
     * 从消息中提取设备名称
     */
    private void extractDevicesFromMessage(String message, Set<String> devices) {
        if (message == null || message.isEmpty()) return;
        String lowerMessage = message.toLowerCase();

        // === 先尝试提取"第一推荐：XXX"格式（助手回复中的推荐格式）===
        Pattern recPattern = Pattern.compile("(?:第一推荐|第二推荐|第三推荐|第四推荐|第五推荐)[：:]\\s*([^\\n。，,]{2,40}?)(?:\\n|。|，|,|$)");
        Matcher recMatcher = recPattern.matcher(message);
        while (recMatcher.find()) {
            String name = recMatcher.group(1).trim();
            if (!name.isEmpty()) {
                devices.add(name);
            }
        }
        // 如果通过推荐格式提取到了设备，不再继续品牌匹配（避免重复提取品牌碎片）
        if (!devices.isEmpty()) return;

        // 常见设备品牌（按长度降序，优先匹配长品牌名）
        // 注意：使用小写匹配，支持大小写混写
        String[][] brandsWithCheck = {
            {"iphone", "iPhone"}, {"xiaomi", "Xiaomi"}, {"samsung", "Samsung"},
            {"oneplus", "OnePlus"}, {"huawei", "Huawei"}, {"honor", "Honor"},
            {"realme", "realme"}, {"meizu", "Meizu"}, {"iqoo", "iQOO"},
            {"oppo", "OPPO"}, {"vivo", "vivo"}, {"redmi", "Redmi"}, {"redme", "Redme"},
            {"苹果", "苹果"}, {"华为", "华为"}, {"小米", "小米"},
            {"三星", "三星"}, {"荣耀", "荣耀"}, {"一加", "一加"},
            {"魅族", "魅族"}, {"红米", "红米"}
        };

        // 常见设备型号后缀
        String[] modelSuffixes = {"pro\\+", "pro max", "ultra", "plus", "se", "lite",
                "青春版", "标准版", "至尊版", "pro", "max"};

        for (String[] brandPair : brandsWithCheck) {
            String brandLower = brandPair[0];
            String brandDisplay = brandPair[1];

            int brandIndex = lowerMessage.indexOf(brandLower);
            if (brandIndex < 0) continue;

            // 从品牌名后面开始提取型号（使用原始消息保持大小写）
            int originalBrandEnd = brandIndex + brandLower.length();
            String afterBrand = message.length() > originalBrandEnd ? message.substring(originalBrandEnd) : "";

            // 尝试匹配型号后缀
            String modelName = brandDisplay;
            for (String suffix : modelSuffixes) {
                java.util.regex.Pattern p = java.util.regex.Pattern.compile("^\\s*" + suffix, java.util.regex.Pattern.CASE_INSENSITIVE);
                java.util.regex.Matcher m = p.matcher(afterBrand);
                if (m.find()) {
                    modelName = brandDisplay + " " + m.group().trim();
                    break;
                }
            }

            // 也尝试匹配数字型号（如 iPhone 15, 小米14）
            java.util.regex.Pattern numPattern = java.util.regex.Pattern.compile("^\\s*(\\d+[a-zA-Z]*)\\s*");
            java.util.regex.Matcher numMatcher = numPattern.matcher(afterBrand);
            if (numMatcher.find()) {
                // 中文品牌不加空格（小米15），英文品牌加空格（iPhone 15）
                boolean isChineseBrand = brandDisplay.matches("[\u4e00-\u9fff]+");
                if (isChineseBrand) {
                    modelName = brandDisplay + numMatcher.group(1).trim();
                } else {
                    modelName = brandDisplay + " " + numMatcher.group(1).trim();
                }
            }

            if (modelName.length() > brandDisplay.length()) {
                devices.add(modelName.trim());
            } else {
                devices.add(brandDisplay);
            }
        }
    }

    private String generateGeneralResponse(String sessionId, String message) {
        List<Map<String, String>> messages = new ArrayList<>();

        // 系统提示词
        Map<String, String> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        StringBuilder systemPrompt = new StringBuilder();
        systemPrompt.append(String.format("""
                你是一个友好的手机设备管理助手。请用简洁自然的方式回复用户。
                当前系统时间：%s

                【重要规则】
                1. 仔细阅读历史对话上下文，理解当前消息的语境
                2. 如果用户消息是对话的延续（如"单独了解"、"是的"、"好的"等），请结合上下文回复
                3. 如果上下文提到过具体设备（如小米15），用户说"单独了解"时应该继续介绍该设备
                4. 如果用户的问题在历史对话中已经讨论过，请基于之前的讨论继续回答
                5. 不要主动提供设备数据库中的详细信息，除非用户明确询问某个设备的具体参数
                6. 如果用户只是问候或闲聊，请用自然的方式回应，不要牵扯到设备信息
                7. 绝对不要使用任何 Markdown 符号（**、*、###、- 等）
                8. 使用纯文本，用换行和空格来组织内容
                9. 语言自然友好，分段清晰
                10. 如果用户问"之前推荐了什么"、"刚才那个设备"等，请根据历史对话和推荐记录回答
                """, getCurrentTimeInfo()));

        // 添加上下文推荐记录 - 使用推荐追踪器（准确，不依赖 LLM 记忆）
        String rawSessionId = extractSessionId(sessionId);
        List<String> recDevices = getRecommendedDevices(rawSessionId);
        if (!recDevices.isEmpty()) {
            systemPrompt.append("\n\n【本次对话中的推荐记录】\n");
            for (int i = 0; i < recDevices.size(); i++) {
                systemPrompt.append(i + 1).append(". ").append(recDevices.get(i)).append("\n");
            }
            systemPrompt.append("以上是本次对话中实际推荐过的设备。如果用户询问推荐了哪些设备，请严格基于此列表回答，不要编造任何未出现在此列表中的设备。");
        }

        // 添加上下文设备追踪信息 - 记录本次对话中所有提到过的设备
        String deviceContext = getMentionedDevicesContext(rawSessionId);
        if (!deviceContext.isEmpty()) {
            systemPrompt.append(deviceContext);
            log.info("已将 {} 个追踪设备添加上下文", getMentionedDevices(rawSessionId).size());
        }

        // 执行Web搜索获取实时信息
        try {
            if (webSearchService.isEnabled()) {
                log.info("执行博查AI Web搜索: {}", message);
                var searchResults = webSearchService.search(message, 8);
                if (!searchResults.isEmpty()) {
                    String searchContext = webSearchService.formatResultsAsContext(searchResults);
                    systemPrompt.append(searchContext);
                    log.info("已将 {} 条搜索结果添加上下文", searchResults.size());
                } else {
                    log.info("博查AI搜索未返回结果");
                }
            }
        } catch (Exception e) {
            log.warn("Web搜索异常，跳过搜索: {}", e.getMessage());
        }
        systemPrompt.append("\n请直接回复，不要使用任何特殊符号。");
        systemMessage.put("content", systemPrompt.toString());
        messages.add(systemMessage);

        // 添加历史对话消息（带正确角色），增加上下文窗口到10条
        List<ChatMessage> historyMessages = memoryCache.get(sessionId);
        if (historyMessages != null && !historyMessages.isEmpty()) {
            int startIndex = Math.max(0, historyMessages.size() - 10);
            for (int i = startIndex; i < historyMessages.size(); i++) {
                ChatMessage historyMsg = historyMessages.get(i);
                Map<String, String> msg = new HashMap<>();
                msg.put("role", historyMsg.getRole());
                msg.put("content", historyMsg.getContent());
                messages.add(msg);
            }
        }

        // 添加当前用户消息
        Map<String, String> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", message);
        messages.add(userMessage);

        log.info("生成通用回复 - sessionId: {}, 消息数: {}", sessionId, messages.size());

        try {
            return llmService.chat(messages);
        } catch (Exception e) {
            log.error("General response generation failed: ", e);
            return "抱歉，我现在无法回答您的问题，请稍后再试。";
        }
    }

    /**
     * 判断是否应该检索历史记忆
     * 只有在用户询问之前讨论过的内容、提到"之前"、"刚才"等关键词时才检索
     */
    private boolean shouldRetrieveMemory(String message) {
        String lowerMessage = message.toLowerCase().trim();

        // 用户明确询问历史对话内容
        String[] memoryKeywords = {
                "之前", "刚才", "上次", "历史记录", "说过", "讨论",
                "你刚才说", "你之前说", "我们刚才", "我们之前"
        };

        for (String keyword : memoryKeywords) {
            if (lowerMessage.contains(keyword)) {
                return true;
            }
        }

        // 用户追问或延续之前的话题（通常很短或包含代词）
        if (lowerMessage.length() < 10 ||
                lowerMessage.contains("它") ||
                lowerMessage.contains("这个") ||
                lowerMessage.contains("那个") ||
                lowerMessage.contains("呢") ||
                lowerMessage.contains("吗")) {
            return true;
        }

        return false;
    }



    /**
     * 获取最近的历史对话上下文（优先从数据库获取）
     * @param memoryKey 缓存键，格式为 userId:sessionId 或纯 sessionId
     */
    private String getConversationContext(String memoryKey) {
        // 从 memoryKey 中提取原始 sessionId 和 userId
        String rawSessionId = extractSessionId(memoryKey);
        Long userId = extractUserIdFromMemoryKey(memoryKey);
        if (userId == null) userId = 0L;
        
        // 优先从数据库获取历史消息
        String dbContext = chatContextService.getConversationContext(userId, rawSessionId);
        if (dbContext != null && !dbContext.isEmpty()) {
            log.debug("从数据库获取会话 {} 的上下文", rawSessionId);
            return dbContext;
        }
        
        // 如果数据库没有数据，尝试从内存缓存获取（用于新会话或数据库不可用时）
        List<ChatMessage> messages = memoryCache.get(memoryKey);
        if (messages == null || messages.isEmpty()) {
            log.debug("memoryKey {} 没有历史消息", memoryKey);
            return getMentionedDevicesContext(rawSessionId);
        }

        int startIndex = Math.max(0, messages.size() - 10);
        List<ChatMessage> recentMessages = messages.subList(startIndex, messages.size());

        StringBuilder context = new StringBuilder();
        context.append("【对话历史上下文】\n");
        for (ChatMessage msg : recentMessages) {
            String role = "user".equals(msg.getRole()) ? "用户" : "助手";
            context.append(role).append(": ").append(msg.getContent()).append("\n");
        }

        String deviceContext = getMentionedDevicesContext(rawSessionId);
        if (!deviceContext.isEmpty()) {
            context.append("\n").append(deviceContext);
        }

        log.debug("从内存缓存获取 memoryKey {} 的上下文: {} 条消息", memoryKey, recentMessages.size());
        return context.toString();
    }


    private String extractSnCode(String message) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("[A-Z0-9]{8,20}", java.util.regex.Pattern.CASE_INSENSITIVE);
        var matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }

    private String[] extractDeviceNames(String message) {
        // 支持多种格式
        java.util.regex.Pattern[] patterns = {
                java.util.regex.Pattern.compile("对比(.+?)(?:和|与|vs|VS|比较)(.+?)(?:哪个|谁|更|好|？|\\?|$)"),
                java.util.regex.Pattern.compile("对比(.+?)(?:和|与|vs|VS|比较)(.+)"),
                java.util.regex.Pattern.compile("(.+?)(?:和|与|vs|VS|比较)(.+?)(?:哪个|谁|更|好|？|\\?|$)"),
                java.util.regex.Pattern.compile("(.+?)(?:和|与|vs|VS|比较)(.+)")
        };

        for (java.util.regex.Pattern pattern : patterns) {
            var matcher = pattern.matcher(message);
            if (matcher.find()) {
                String device1 = matcher.group(1).trim();
                String device2 = matcher.group(2).trim();
                // 清理设备名称
                device1 = device1.replaceAll("^(查询|看看|介绍|对比|推荐|的|配置|参数|是什么|怎么样)", "")
                        .replaceAll("(查询|看看|介绍|对比|推荐|的|配置|参数|是什么|怎么样)$", "")
                        .trim();
                device2 = device2.replaceAll("^(查询|看看|介绍|对比|推荐|的|配置|参数|是什么|怎么样)", "")
                        .replaceAll("(查询|看看|介绍|对比|推荐|的|配置|参数|是什么|怎么样)$", "")
                        .trim();
                return new String[]{device1, device2};
            }
        }
        return new String[0];
    }

    private String extractDeviceName(String message) {
        // 先去除操作类关键词前缀，如"收藏""添加设备""删除设备""取消收藏"等
        String cleaned = message.replaceAll("^(收藏|取消收藏|删除收藏|移除收藏|查看收藏|打开收藏|添加设备|删除设备|移除设备|修改设备|编辑设备|查看设备|查询|看看|介绍)\\s*", "").trim();

        if (cleaned.equals(message.trim())) {
            // 没有匹配到关键词前缀，使用旧的提取逻辑
            String[] prefixes = {"查询", "看看", "介绍"};
            for (String prefix : prefixes) {
                if (message.contains(prefix)) {
                    int idx = message.indexOf(prefix) + prefix.length();
                    return message.substring(idx).trim();
                }
            }
            cleaned = message.replaceAll("^(查询|看看|介绍)", "").trim();
        }

        if (cleaned.contains("的配置") || cleaned.contains("的配置信息") ||
                cleaned.contains("的参数") || cleaned.contains("的配置怎么样")) {
            cleaned = cleaned.replaceAll("(的配置|的配置信息|的参数|的配置怎么样)$", "").trim();
        }

        return cleaned.isEmpty() ? null : cleaned;
    }

    /**
     * 从设备名称中提取关键词（去除存储规格等次要信息）
     * 例如："三星 Galaxy S24 Ultra 12GB+256GB" -> "galaxy s24 ultra"
     */
    private String extractKeywordsFromDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isEmpty()) {
            return "";
        }

        // 转换为小写并去除多余空格
        String keywords = deviceName.toLowerCase().replaceAll("\\s+", " ").trim();

        // 去除存储规格模式：12GB+256GB、8GB+128GB、256GB、512GB等
        keywords = keywords.replaceAll("\\d+GB[+\\s]*\\d+GB", "");
        keywords = keywords.replaceAll("\\d+GB", "");
        keywords = keywords.replaceAll("\\d+TB", "");

        // 去除颜色信息
        keywords = keywords.replaceAll("(黑色|白色|金色|银色|灰色|蓝色|红色|绿色|紫色|粉色|黄色|橙色|棕色|钛色|钛灰|钛银|钛黑)", "");

        // 去除网络制式
        keywords = keywords.replaceAll("(5G|4G|LTE|Wi-Fi|WiFi)", "");

        // 去除特殊字符和多余空格
        keywords = keywords.replaceAll("[,，。、；;：:]", " ");
        keywords = keywords.replaceAll("\\s+", " ").trim();

        return keywords;
    }

    /**
     * 使用关键词匹配收藏项（不区分大小写和空格）
     */
    private List<FavoriteDeviceEntity> matchFavoritesByKeywords(String userId, String keywords) {
        List<FavoriteDeviceEntity> allFavorites = favoriteDeviceRepository.findByUserIdOrderByCreatedAtDesc(userId);
        List<FavoriteDeviceEntity> matched = new ArrayList<>();

        if (keywords == null || keywords.isEmpty()) {
            return matched;
        }

        // 去除所有空格并转换为小写
        String normalizedKeywords = keywords.toLowerCase().replaceAll("\\s+", "");
        String[] keywordArray = normalizedKeywords.split("(?=[A-Za-z])|(?<=[A-Za-z])(?=[0-9])|(?<=[0-9])(?=[A-Za-z])|(?=[A-Z])(?<=[a-z])");
        
        for (FavoriteDeviceEntity fav : allFavorites) {
            String favName = fav.getDeviceName().toLowerCase().replaceAll("\\s+", "");
            String favKeywords = extractKeywordsFromDeviceName(fav.getDeviceName()).toLowerCase().replaceAll("\\s+", "");
            
            boolean matchedAny = false;
            for (String keyword : keywordArray) {
                if (keyword.length() < 2) continue;
                if (favName.contains(keyword) || favKeywords.contains(keyword)) {
                    matchedAny = true;
                    break;
                }
            }
            
            if (!matchedAny) {
                for (String keyword : keywords.toLowerCase().split("\\s+")) {
                    if (keyword.length() < 2) continue;
                    if (fav.getDeviceName().toLowerCase().contains(keyword)) {
                        matchedAny = true;
                        break;
                    }
                }
            }
            
            if (matchedAny) {
                matched.add(fav);
            }
        }

        return matched;
    }

    /**
     * 从推荐回复中提取第一个设备名称
     */
    private String extractFirstDeviceNameFromReply(String reply) {
        if (reply == null || reply.isEmpty()) return null;
        
        log.info("extractFirstDeviceNameFromReply - 开始从回复中提取设备名, 回复长度: {}", reply.length());
        
        Pattern[] patterns = {
            Pattern.compile("完整设备名称[：:]\\s*([^\\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("第一推荐[：:]\\s*([^\\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(iPhone\\s+\\d+[\\S\\s]*?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(AirPods\\s+\\S[\\S\\s]*?)(?:\\n|。|，|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(华为\\s+[^\\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(小米\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(OPPO\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(vivo\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(三星\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(荣耀\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(一加\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(realme\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(魅族\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(索尼\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(Apple\\s+Watch[\\S\\s]*?)(?:\\n|。|，|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(iPad[\\S\\s]{0,30}?)(?:\\n|。|，|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(Bose\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(JBL\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(森海塞尔\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(AKG\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(佳明\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(Fitbit\\s+[^\n。]+?)(?:\\n|。|$)", Pattern.CASE_INSENSITIVE),
        };
        
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(reply);
            if (matcher.find()) {
                String deviceName = matcher.group(1).trim();
                deviceName = deviceName.replaceAll("[，。、]+$", "").trim();
                if (deviceName.length() > 2 && deviceName.length() < 50) {
                    log.info("extractFirstDeviceNameFromReply - 成功提取设备名: {}", deviceName);
                    return deviceName;
                }
            }
        }
        
        log.info("extractFirstDeviceNameFromReply - 未能从回复中提取到设备名");
        return null;
    }

    /**
     * 获取真实设备信息用于购机推荐
     */
    private String getRealDevicesInfo() {
        try {
            log.info("开始通过 DeepSeek 获取热门移动设备列表");

            String prompt = """
                    你是一个专业的移动电子设备数据库助手。请提供当前市场上各类热门移动设备的简要信息。
                    
                    请提供总共20款设备，包括：
                    - 8-10款热门手机
                    - 4-5款热门平板
                    - 3-4款热门智能手表
                    - 2-3款热门耳机
                    
                    对于每款设备，请提供：
                    - 设备名称
                    - 品牌
                    - 设备类型（手机/平板/手表/耳机）
                    - 价格（人民币）
                    - 评分（满分10分）
                    - 核心配置（根据设备类型提供关键参数）
                    
                    【重要规则】
                    1. 只返回真实的、已发布的设备信息
                    2. 不要编造任何设备
                    3. 每款设备之间用空行分隔
                    4. 使用简洁的格式
                    5. 不要使用 Markdown 符号
                    6. 如果不知道某些信息，可以省略
                    """;

            String result = llmService.generate(prompt);

            if (result != null && !result.isEmpty()) {
                log.info("成功获取热门设备列表");
                return result;
            }
        } catch (Exception e) {
            log.error("获取设备列表失败", e);
        }
        return "";
    }

    private String extractBudget(String message) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\d{3,5}(?:元|块)?");
        var matcher = pattern.matcher(message);
        return matcher.find() ? matcher.group() : null;
    }

    private String extractUseCase(String message) {
        // 通用使用场景
        String[] useCases = {
                "游戏", "拍照", "续航", "办公", "日常",
                "运动", "音乐", "学习", "商务", "娱乐",
                "健康监测", "通话", "导航", "阅读"
        };
        for (String useCase : useCases) {
            if (message.contains(useCase)) {
                return useCase;
            }
        }
        return null;
    }

    private String formatWarrantyResult(WarrantyQueryResult result) {
        if (result == null) {
            return "未查询到保修信息。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("保修查询结果：\n\n");
        sb.append("设备名称: ").append(nullToEmpty(result.getDeviceName())).append("\n");
        sb.append("序列号: ").append(nullToEmpty(result.getSnCode())).append("\n");
        sb.append("保修状态: ").append(nullToEmpty(result.getWarrantyStatus())).append("\n");
        sb.append("保修到期: ").append(nullToEmpty(result.getWarrantyEnd())).append("\n");
        sb.append("剩余天数: ").append(result.getDaysRemaining() > 0 ? result.getDaysRemaining() : 0).append(" 天\n");
        sb.append("保修范围: ").append(nullToEmpty(result.getWarrantyRange()));

        return sb.toString();
    }

    private String nullToEmpty(Object obj) {
        return obj == null ? "暂无" : obj.toString();
    }

    public static class ChatMessage {
        private String role;
        private String content;

        public ChatMessage() {
        }

        public ChatMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}