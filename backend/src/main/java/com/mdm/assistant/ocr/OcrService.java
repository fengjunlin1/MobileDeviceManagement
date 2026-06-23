package com.mdm.assistant.ocr;

import com.mdm.assistant.exception.ApiServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

@Service
public class OcrService {

    private static final Logger log = LoggerFactory.getLogger(OcrService.class);

    private final HttpClient httpClient;
    private final String ocrBaseUrl;
    private final int maxRetries;
    private final Duration timeout;

    /** 上次标记为不可用的时间戳，null=当前可用 */
    private volatile Instant unavailableSince = null;
    /** 不可用后的冷却时间（秒），冷却过后重新尝试 */
    private static final long COOLDOWN_SECONDS = 30;

    private static final Pattern SN_CODE_PATTERN = Pattern.compile("[A-Za-z0-9]{8,20}");

    public OcrService(@Value("${ocr.base-url}") String ocrBaseUrl,
                     @Value("${ocr.timeout:15s}") String timeoutStr,
                     @Value("${ocr.max-retries:2}") int maxRetries) {
        this.ocrBaseUrl = ocrBaseUrl;
        this.maxRetries = maxRetries;

        Duration parsedTimeout;
        try {
            parsedTimeout = Duration.parse(timeoutStr);
        } catch (Exception e) {
            log.warn("OCR timeout 解析失败，使用默认 15s: {}", e.getMessage());
            parsedTimeout = Duration.ofSeconds(15);
        }
        this.timeout = parsedTimeout;

        // 使用更长超时（读取超时 = connectTimeout + 额外处理时间）
        Duration readTimeout = parsedTimeout.plus(Duration.ofSeconds(30));
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(parsedTimeout)
                .build();
        log.info("OCR服务初始化: baseUrl={}, timeout={}, maxRetries={}", ocrBaseUrl, parsedTimeout, maxRetries);
    }

    /**
     * 检查OCR服务是否可用。
     * 一旦标记为不可用，会在 COOLDOWN_SECONDS 秒后自动重新尝试，
     * 避免永久阻塞。
     */
    public boolean isAvailable() {
        Instant since = unavailableSince;
        if (since != null) {
            // 冷却中，检查是否已过冷却期
            if (Duration.between(since, Instant.now()).getSeconds() < COOLDOWN_SECONDS) {
                return false;
            }
            // 冷却已过，允许重新尝试，标记清空（实际尝试会在 recognizeText 中执行）
            unavailableSince = null;
        }
        return true;
    }

    /**
     * 识别图片中的 SN 码
     */
    public String recognizeSnCode(String imageBase64) {
        String rawText = recognizeText(imageBase64);
        return extractSnCode(rawText);
    }

    /**
     * 通用文字识别，返回原始文本
     */
    public String recognizeText(String imageBase64) {
        if (!isAvailable()) {
            log.warn("OCR 服务处于冷却期，跳过识别");
            throw new ApiServiceException("OCR服务暂时不可用，请在" + COOLDOWN_SECONDS + "秒后重试", 0);
        }

        int retries = 0;
        Exception lastException = null;

        while (retries < maxRetries) {
            retries++;
            try {
                String result = callOcrApi(imageBase64);
                // 成功：清除不可用标记
                unavailableSince = null;
                return result;
            } catch (Exception e) {
                lastException = e;
                log.warn("OCR 第 {} 次尝试失败: {}", retries, e.getMessage());

                if (retries >= maxRetries) {
                    unavailableSince = Instant.now();
                    log.error("OCR 已连续失败 {} 次，标记为不可用，{} 秒后重试", maxRetries, COOLDOWN_SECONDS);
                    throw new ApiServiceException("OCR识别失败：" + e.getMessage(), retries);
                }

                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new ApiServiceException("OCR识别被中断", retries);
                }
            }
        }

        throw new ApiServiceException("OCR识别失败：" + (lastException != null ? lastException.getMessage() : "未知错误"), retries);
    }

    /**
     * 调用 OCR API 进行文字识别。
     */
    private String callOcrApi(String imageBase64) throws IOException, InterruptedException {
        String requestBody = String.format("{\"image\":\"%s\",\"task\":\"ocr\"}", imageBase64);
        log.info("调用OCR API: bodySize={} bytes", requestBody.length());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ocrBaseUrl + "/ocr"))
                .header("Content-Type", "application/json")
                .timeout(timeout)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        log.info("发送OCR请求到: {}", ocrBaseUrl + "/ocr");
        long start = System.currentTimeMillis();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        long elapsed = System.currentTimeMillis() - start;

        log.info("OCR请求完成: status={}, elapsed={}ms, responseSize={}bytes",
                response.statusCode(), elapsed, response.body() != null ? response.body().length() : 0);

        if (response.statusCode() != 200) {
            throw new RuntimeException("OCR API 返回状态码: " + response.statusCode()
                    + ", 响应: " + (response.body() != null ? response.body().substring(0, Math.min(response.body().length(), 500)) : "空"));
        }

        String body = response.body();
        if (body == null || body.trim().isEmpty()) {
            throw new RuntimeException("OCR API 返回空响应");
        }

        return parseOcrResponse(body);
    }

    /**
     * 解析 OCR 响应，兼容 JSON 和纯文本格式
     */
    private String parseOcrResponse(String responseBody) {
        String trimmed = responseBody.trim();

        // 尝试解析为 JSON
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode node;

                if (trimmed.startsWith("[")) {
                    // JSON 数组：拼接所有文本
                    var arr = mapper.readTree(trimmed);
                    StringBuilder sb = new StringBuilder();
                    for (var elem : arr) {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(extractTextFromJsonNode(elem));
                    }
                    String text = sb.toString().trim();
                    if (!text.isEmpty()) return text;
                } else {
                    node = mapper.readTree(trimmed);
                    // 尝试常见字段名
                    for (String key : new String[]{"text", "result", "data", "content", "ocr_text", "recognized_text", "output"}) {
                        if (node.has(key)) {
                            String val = node.get(key).asText().trim();
                            if (!val.isEmpty()) return val;
                        }
                    }
                    // 尝试提取所有字段的值拼接
                    StringBuilder sb = new StringBuilder();
                    node.fields().forEachRemaining(entry -> {
                        if (sb.length() > 0) sb.append("\n");
                        sb.append(entry.getValue().asText());
                    });
                    String text = sb.toString().trim();
                    if (!text.isEmpty()) return text;
                }
            } catch (Exception e) {
                // JSON 解析失败，回退到纯文本
                log.debug("OCR 响应 JSON 解析失败，按纯文本处理: {}", e.getMessage());
            }
        }

        // 纯文本格式直接返回
        return trimmed;
    }

    private String extractTextFromJsonNode(com.fasterxml.jackson.databind.JsonNode node) {
        if (node.isTextual()) return node.asText();
        // 尝试常见字段
        for (String key : new String[]{"text", "content", "words", "description"}) {
            if (node.has(key)) {
                return node.get(key).asText();
            }
        }
        return node.toString();
    }

    private String extractSnCode(String ocrResult) {
        if (ocrResult == null || ocrResult.isEmpty()) return ocrResult;
        var matcher = SN_CODE_PATTERN.matcher(ocrResult);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            if (sb.length() > 0) sb.append(", ");
            sb.append(matcher.group());
        }
        return sb.length() > 0 ? sb.toString() : ocrResult;
    }
}
