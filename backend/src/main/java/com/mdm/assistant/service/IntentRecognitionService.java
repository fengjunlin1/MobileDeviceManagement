package com.mdm.assistant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mdm.assistant.dto.IntentRecognitionResult;
import com.mdm.assistant.enums.Intent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class IntentRecognitionService {

    private static final Logger log = LoggerFactory.getLogger(IntentRecognitionService.class);

    private final LlmService llmService;
    private final ObjectMapper objectMapper;

    private static final Map<Intent, List<Pattern>> KEYWORD_PATTERNS = Map.of(
            Intent.WARRANTY_QUERY, List.of(
                    Pattern.compile("保修", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("质保", Pattern.CASE_INSENSITIVE),
                    Pattern.compile(" warranty", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("SN", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("序列号", Pattern.CASE_INSENSITIVE)
            ),
            Intent.PARAM_COMPARE, List.of(
                    Pattern.compile("对比", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("比较", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("参数", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("区别", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("和.*哪个更", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("vs", Pattern.CASE_INSENSITIVE),
                    Pattern.compile(" versus ", Pattern.CASE_INSENSITIVE)
            ),
            Intent.DEVICE_QUERY, List.of(
                    Pattern.compile("设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("手机", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("查询", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("型号", Pattern.CASE_INSENSITIVE)
            ),
            Intent.PURCHASE_ADVICE, List.of(
                    Pattern.compile("推荐", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("购机", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("性价比", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("购买", Pattern.CASE_INSENSITIVE)
            ),
            Intent.BIND_DEVICE, List.of(
                    Pattern.compile("绑定", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("关联", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("添加设备", Pattern.CASE_INSENSITIVE)
            ),
            Intent.HISTORY_QUERY, List.of(
                    Pattern.compile("历史", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("之前", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("记录", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("刚才", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("上次", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("上回", Pattern.CASE_INSENSITIVE)
            ),
            Intent.FAVORITE_OPERATION, List.of(
                    Pattern.compile("收藏", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("取消收藏", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("删除收藏", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("移除收藏", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("查看收藏", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("打开收藏", Pattern.CASE_INSENSITIVE)
            ),
            Intent.MY_DEVICE_OPERATION, List.of(
                    Pattern.compile("我的设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("我有哪些设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("我有什么设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("我现在有哪些设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("我现在有什么设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("我拥有的设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("添加设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("删除设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("移除设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("修改设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("编辑设备", Pattern.CASE_INSENSITIVE),
                    Pattern.compile("查看设备", Pattern.CASE_INSENSITIVE)
            )
    );

    public IntentRecognitionService(LlmService llmService, ObjectMapper objectMapper) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
    }

    public IntentRecognitionResult recognize(String message) {
        return recognize(message, null);
    }

    /**
     * 带对话上下文的意图识别，能更好地理解指代和追问
     */
    public IntentRecognitionResult recognize(String message, String conversationContext) {
        // 优先检查是否是上下文追问（不含具体设备名的延续性问题）
        if (isFollowUpQuestion(message)) {
            log.info("检测到上下文追问: {}", message);
            return new IntentRecognitionResult(Intent.UNKNOWN.name(), "", 0.9);
        }

        Intent intent = tryKeywordMatch(message);
        double confidence;

        if (intent != Intent.UNKNOWN) {
            confidence = 0.8;
            return new IntentRecognitionResult(intent.name(), extractInfo(message, intent), confidence);
        }

        return recognizeWithLLM(message, conversationContext);
    }

    /**
     * 判断是否是上下文追问（如"还有其他的吗"、"再推荐几个"等）
     * 这类消息应走通用对话流程，利用历史上下文回答
     */
    private boolean isFollowUpQuestion(String message) {
        String lower = message.toLowerCase().trim();
        // 追问模式：不含具体品牌/设备名，但是延续性提问
        // 注意：这里只放"延续当前话题"的模式，不包含"询问历史/回顾"的模式
        // 后者应走 tryKeywordMatch → HISTORY_QUERY 路径
        String[] followUpPatterns = {
            "还有.*吗", "还有.*不", "其他的", "还有没有", "除此之外", "另外", "别的",
            // 要求更多推荐（延续当前推荐话题）
            "再.*推荐", "再.*介绍",
            "继续.*推荐", "再.*来.*几个", "还有.*选择",
            // 追问其他品类
            "其他.*手机", "其他.*平板", "其他.*手表", "其他.*耳机",
            // 指代问题（这个、那个、第几个等）
            "刚才.*第.*个", "之前.*第.*个", "上一个", "前面.*推荐",
            "刚刚.*说", "前面.*介绍", "刚.*推荐.*第.*个",
            "刚才.*那个", "那个.*设备", "这个.*设备",
            "那个.*手机", "这个.*手机", "刚才.*那.*款",
            "那个.*怎么样", "这个.*怎么样",
            // 追问列表中的具体项
            "重复.*一遍", "再说.*一遍", "第一个.*是.*什么",
            "第二个.*是.*什么", "第三个.*是.*什么",
            "第.*款.*是.*什么", "第.*个.*是.*什么",
            // 问配置但无具体设备名（接上文）
            "配置.*怎么样", "参数.*怎么样"
        };
        for (String pattern : followUpPatterns) {
            if (lower.matches(".*" + pattern + ".*")) {
                // 确保不含具体品牌名（含品牌名说明是新查询）
                String[] brands = {"苹果", "iphone", "华为", "huawei", "小米", "xiaomi",
                        "三星", "samsung", "oppo", "vivo", "荣耀", "honor",
                        "一加", "oneplus", "魅族", "meizu", "realme", "iqoo",
                        "redmi", "redme"};
                for (String brand : brands) {
                    if (lower.contains(brand)) return false;
                }
                return true;
            }
        }
        return false;
    }

    private Intent tryKeywordMatch(String message) {
        String lowerMessage = message.toLowerCase();

        // === 保修查询优先识别：防止"查询"等通用词抢夺保修意图 ===
        if (lowerMessage.contains("保修") || lowerMessage.contains("质保")
                || lowerMessage.contains("保修期") || lowerMessage.contains("保修状态")
                || lowerMessage.contains("保修信息") || lowerMessage.contains("保修查询")
                || lowerMessage.contains("序列号") || lowerMessage.contains("sn码")
                || lowerMessage.contains("sn：") || lowerMessage.contains("sn:")) {
            return Intent.WARRANTY_QUERY;
        }

        // === 我的设备操作优先识别：防止"设备"等通用词被设备查询意图抢夺 ===
        if (lowerMessage.contains("我的设备") || lowerMessage.contains("我有哪些设备")
                || lowerMessage.contains("我有什么设备") || lowerMessage.contains("我拥有的设备")
                || lowerMessage.contains("添加设备") || lowerMessage.contains("删除设备")
                || lowerMessage.contains("移除设备") || lowerMessage.contains("修改设备")
                || lowerMessage.contains("编辑设备") || lowerMessage.contains("查看设备")) {
            return Intent.MY_DEVICE_OPERATION;
        }

        // === 收藏操作优先识别 ===
        if (lowerMessage.contains("取消收藏") || lowerMessage.contains("删除收藏")
                || lowerMessage.contains("移除收藏") || lowerMessage.contains("查看收藏")
                || lowerMessage.contains("打开收藏") || lowerMessage.contains("收藏列表")
                || lowerMessage.contains("我的收藏")) {
            return Intent.FAVORITE_OPERATION;
        }

        // 如果是"XXX的配置"、"XXX的参数"格式，应该是设备查询而非对比
        if (lowerMessage.matches(".*的配置(信息)?") || 
            lowerMessage.matches(".*的参数") ||
            lowerMessage.matches(".*怎么样")) {
            return Intent.DEVICE_QUERY;
        }
        
        // 优先检查对比意图，但需要更严格的条件
        List<Pattern> comparePatterns = KEYWORD_PATTERNS.get(Intent.PARAM_COMPARE);
        if (comparePatterns != null) {
            for (Pattern pattern : comparePatterns) {
                if (pattern.matcher(message).find()) {
                    // 确保是对比两个设备，而不是单个设备查询
                    if (lowerMessage.contains("对比") || lowerMessage.contains("比较") || 
                        lowerMessage.contains("vs") || lowerMessage.contains("和.*哪个")) {
                        return Intent.PARAM_COMPARE;
                    }
                    // "参数"、"区别"等词单独出现时，不一定是意图对比
                    if (lowerMessage.contains("参数") && !lowerMessage.contains("查询参数") && 
                        !lowerMessage.matches(".*的配置.*")) {
                        return Intent.DEVICE_QUERY;
                    }
                }
            }
        }
        
        // 按优先级顺序检查其他意图：购机建议 > 设备查询 > 保修 > 收藏操作 > 我的设备操作 > 绑定 > 历史
        // 优先检查历史查询：包含"刚才"、"之前"、"历史"、"上次"、"记录"等回顾关键词
        if (lowerMessage.contains("刚才") || lowerMessage.contains("之前") || 
            lowerMessage.contains("历史") || lowerMessage.contains("上次") ||
            lowerMessage.contains("上回") || lowerMessage.contains("记录") ||
            lowerMessage.contains("之前问了") || lowerMessage.contains("刚才问了") ||
            lowerMessage.contains("之前说") || lowerMessage.contains("刚才说")) {
            return Intent.HISTORY_QUERY;
        }
        Intent[] priorityOrder = {
            Intent.PURCHASE_ADVICE,  // 推荐/购机 - 最高优先级
            Intent.DEVICE_QUERY,     // 设备查询
            Intent.WARRANTY_QUERY,   // 保修查询
            Intent.FAVORITE_OPERATION,  // 收藏操作
            Intent.MY_DEVICE_OPERATION, // 我的设备操作
            Intent.BIND_DEVICE,      // 绑定设备
            Intent.HISTORY_QUERY     // 历史记录
        };
        
        for (Intent intent : priorityOrder) {
            List<Pattern> patterns = KEYWORD_PATTERNS.get(intent);
            if (patterns != null) {
                for (Pattern pattern : patterns) {
                    if (pattern.matcher(message).find()) {
                        return intent;
                    }
                }
            }
        }
        
        return Intent.UNKNOWN;
    }

    private String extractInfo(String message, Intent intent) {
        return switch (intent) {
            case WARRANTY_QUERY -> "保修相关信息";
            case PARAM_COMPARE -> "参数对比";
            case DEVICE_QUERY -> "设备查询";
            case PURCHASE_ADVICE -> "购机建议";
            case BIND_DEVICE -> "绑定设备";
            case HISTORY_QUERY -> "历史记录查询";
            case FAVORITE_OPERATION -> "收藏操作";
            case MY_DEVICE_OPERATION -> "我的设备操作";
            case UNKNOWN -> "";
        };
    }

    private IntentRecognitionResult recognizeWithLLM(String message) {
        return recognizeWithLLM(message, null);
    }

    private IntentRecognitionResult recognizeWithLLM(String message, String conversationContext) {
        String contextSection = (conversationContext != null && !conversationContext.isEmpty())
                ? String.format("对话历史上下文（最近的对话，帮助理解用户指代）：\n%s\n\n", conversationContext)
                : "";

        String prompt = String.format("""
                %s判断用户意图，输出JSON：{"intent": "意图类型", "extractedInfo": "提取的关键信息", "confidence": 0.0-1.0置信度}

                【重要】如果用户消息中说"刚才"、"之前"、"那个"等指代词，请结合对话历史上下文来判断意图。
                例如：用户在历史中提到过推荐设备，现在问"刚才推荐了什么"，意图应该是HISTORY_QUERY或UNKNOWN（让后续处理从历史中查找）。

                意图类型：
                - WARRANTY_QUERY: 保修查询
                - PARAM_COMPARE: 参数对比
                - DEVICE_QUERY: 设备查询
                - PURCHASE_ADVICE: 购机建议
                - BIND_DEVICE: 绑定设备
                - HISTORY_QUERY: 历史记录查询
                - FAVORITE_OPERATION: 收藏操作（收藏、取消收藏、删除收藏、查看收藏等）
                - MY_DEVICE_OPERATION: 我的设备操作（添加设备、删除设备、修改设备、查看设备等）
                - UNKNOWN: 未知意图

                用户输入：%s
                """, contextSection, message);

        try {
            String result = llmService.generate(prompt);
            return parseLLMResponse(result);
        } catch (Exception e) {
            log.error("Intent recognition error: ", e);
            return new IntentRecognitionResult(Intent.UNKNOWN.name(), "", 0.0);
        }
    }

    private IntentRecognitionResult parseLLMResponse(String content) {
        try {
            content = content.trim();
            if (content.contains("```json")) {
                content = content.substring(content.indexOf("```json") + 7);
                content = content.substring(0, content.indexOf("```"));
            } else if (content.contains("```")) {
                content = content.substring(content.indexOf("```") + 3);
                content = content.substring(0, content.indexOf("```"));
            }

            int start = content.indexOf("{");
            int end = content.lastIndexOf("}");
            if (start != -1 && end != -1) {
                content = content.substring(start, end + 1);
            }

            Map<String, Object> result = objectMapper.readValue(content, Map.class);

            String intentStr = (String) result.get("intent");
            String extractedInfo = (String) result.get("extractedInfo");
            double confidence = ((Number) result.getOrDefault("confidence", 0.5)).doubleValue();

            Intent intent;
            try {
                intent = Intent.valueOf(intentStr.toUpperCase().replace("-", "_"));
            } catch (IllegalArgumentException e) {
                intent = Intent.UNKNOWN;
            }

            return new IntentRecognitionResult(intent.name(), extractedInfo != null ? extractedInfo : "", confidence);
        } catch (Exception e) {
            log.error("Failed to parse LLM response: {}", content, e);
            return new IntentRecognitionResult(Intent.UNKNOWN.name(), "", 0.0);
        }
    }
}
