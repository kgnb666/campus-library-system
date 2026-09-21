package com.library.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.dto.ai.BookInsightResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek AI 大模型智能提供者 (Stage 5 &amp; 9-D，Stage 10-F 加固)
 *
 * <p>双模平滑切换: 配置了有效密钥时真正调用 DeepSeek Chat Completions；
 * 未配置密钥、超时、限流或调用失败时降级至本地规则引擎。</p>
 *
 * <p>Stage 10-F 加固要点:</p>
 * <ol>
 *   <li>入参由 JPA 实体改为 {@link BookInsightContext} —— 原实现在事务外读取
 *       {@code book.getCategory().getName()}，未缓存书目的导读必然 500；</li>
 *   <li>HTTP 客户端由 {@code SimpleClientHttpRequestFactory}（HttpURLConnection，
 *       无连接池）改为基于 JDK HttpClient 的工厂，获得连接复用能力；</li>
 *   <li>超时与重试次数改为可配置（原为硬编码 5s 且无重试）；</li>
 *   <li>客户端懒加载改为 volatile + 双重检查，替代方法级 synchronized；</li>
 *   <li>降级分支自身加 try/catch 并给出最小可用结果，
 *       避免"兜底路径自己抛异常"导致接口 500。</li>
 * </ol>
 */
@Slf4j
@Primary
@Component("deepSeekAiProvider")
public class DeepSeekAiProvider implements AiProvider {

    @Value("${ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String modelName;

    @Value("${ai.deepseek.connect-timeout-ms:5000}")
    private int connectTimeoutMs;

    @Value("${ai.deepseek.read-timeout-ms:5000}")
    private int readTimeoutMs;

    @Value("${ai.deepseek.max-retries:1}")
    private int maxRetries;

    @Value("${ai.deepseek.retry-backoff-ms:200}")
    private long retryBackoffMs;

    private final RuleBasedMockAiProvider fallbackProvider;
    private final ObjectMapper objectMapper;

    /** 双重检查锁需要 volatile；测试可通过 setter 置空以触发重建 */
    private volatile RestClient restClient;

    @Autowired
    public DeepSeekAiProvider(
            @Qualifier("ruleBasedMockAiProvider") RuleBasedMockAiProvider fallbackProvider,
            ObjectMapper objectMapper) {
        this.fallbackProvider = fallbackProvider;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    // ------------------------------------------------------------------
    // 配置与测试辅助方法
    // ------------------------------------------------------------------

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        this.restClient = null;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
        this.restClient = null;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public void setRestClient(RestClient restClient) {
        this.restClient = restClient;
    }

    // ------------------------------------------------------------------
    // HTTP 客户端
    // ------------------------------------------------------------------

    private RestClient restClient() {
        RestClient client = this.restClient;
        if (client == null) {
            synchronized (this) {
                client = this.restClient;
                if (client == null) {
                    client = buildRestClient();
                    this.restClient = client;
                }
            }
        }
        return client;
    }

    private RestClient buildRestClient() {
        // 使用 JDK HttpClient：自带连接池，优于无池化的 HttpURLConnection
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(connectTimeoutMs, 1)))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(Math.max(readTimeoutMs, 1)));

        String trimmedBaseUrl = (baseUrl != null && !baseUrl.isBlank())
                ? baseUrl.trim() : "https://api.deepseek.com/v1";
        if (trimmedBaseUrl.endsWith("/")) {
            trimmedBaseUrl = trimmedBaseUrl.substring(0, trimmedBaseUrl.length() - 1);
        }

        return RestClient.builder()
                .requestFactory(requestFactory)
                .baseUrl(trimmedBaseUrl)
                .defaultHeader("Authorization", "Bearer " + (apiKey != null ? apiKey.trim() : ""))
                .defaultHeader("Content-Type", "application/json")
                .build();
    }

    private boolean isRealApiKeyConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.trim().toLowerCase().startsWith("mock");
    }

    private String resolvedModel() {
        return modelName != null && !modelName.isBlank() ? modelName : "deepseek-chat";
    }

    // ------------------------------------------------------------------
    // 导读生成
    // ------------------------------------------------------------------

    @Override
    public BookInsightResponse generateInsight(BookInsightContext context) {
        if (!isRealApiKeyConfigured()) {
            log.debug("未配置有效 DEEPSEEK_API_KEY，自动平滑使用本地智能生成器");
            return localFallback(context, "deepseek-chat (local-fallback)");
        }

        int attempts = Math.max(1, maxRetries + 1);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                log.info("发起 DeepSeek AI 远程调用生成图书《{}》的智能导读 (BaseUrl: {}, 尝试 {}/{})",
                        context.title(), baseUrl, attempt, attempts);
                return callDeepSeekApi(context);
            } catch (Exception e) {
                log.warn("DeepSeek API 调用失败 (尝试 {}/{}): {}", attempt, attempts, e.getMessage());
                if (attempt < attempts && retryBackoffMs > 0) {
                    sleepQuietly(retryBackoffMs);
                }
            }
        }

        log.warn("DeepSeek API 连续 {} 次调用失败，平滑降级至本地规则引擎: bookId={}", attempts, context.bookId());
        return localFallback(context, "deepseek-chat (fallback)");
    }

    /**
     * 降级路径必须自身可靠: 本地生成器若抛异常不能再向上冒泡，
     * 否则"兜底"形同虚设（原实现在降级分支内再次访问懒加载关联，必然二次抛错）。
     */
    private BookInsightResponse localFallback(BookInsightContext context, String modelNameForResponse) {
        try {
            BookInsightResponse response = fallbackProvider.generateInsight(context);
            response.setModelName(modelNameForResponse);
            return response;
        } catch (Exception e) {
            log.error("本地规则引擎兜底生成失败，返回最小可用导读: bookId={}", context.bookId(), e);
            return BookInsightResponse.builder()
                    .bookId(context.bookId())
                    .bookTitle(context.title())
                    .summary("《" + context.title() + "》的智能导读暂时无法生成，请稍后重试。")
                    .keyTopics(List.of(context.categoryNameOrDefault()))
                    .targetReader("全校师生读者")
                    .readingGuide("建议先浏览全书目录，再选择重点章节精读。")
                    .modelName(modelNameForResponse)
                    .generatedAt(OffsetDateTime.now())
                    .build();
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private BookInsightResponse callDeepSeekApi(BookInsightContext context) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", resolvedModel());

        String systemPrompt = "你是一位资深的大学图书馆学学者与专业书评专家。"
                + "请深入分析提供的图书信息，并严格输出一个合法的 JSON 对象，不要包含任何 Markdown 代码块标签（绝对不要输出 ```json 或 ``` 等代码块格式），直接输出纯 JSON 字符串。"
                + "JSON 必须包含且仅包含以下四个字段："
                + "1. \"summary\": 字符串，对本书核心主旨与学术/实践价值的高度提炼（120-200字）；"
                + "2. \"keyTopics\": 字符串数组，提炼3-5个最核心的专业技术或学科概念关键词；"
                + "3. \"targetReader\": 字符串，精准画像最适宜阅读借阅的人群；"
                + "4. \"readingGuide\": 字符串，具体的先修知识要求与推荐章节精读建议。";

        String userPrompt = String.format("书名：《%s》\n著者：%s\n分类：%s\n简介：%s",
                context.title(),
                context.authorOrDefault(),
                context.categoryNameOrDefault(),
                context.description() != null ? context.description() : "暂无简介");

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        );
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1024);

        String responseJson = restClient().post()
                .uri("/chat/completions")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode rootNode = objectMapper.readTree(responseJson);
        JsonNode choicesNode = rootNode.path("choices");
        if (choicesNode.isArray() && !choicesNode.isEmpty()) {
            String content = choicesNode.get(0).path("message").path("content").asText();
            return parseInsightContent(context, content);
        }

        throw new IllegalStateException("DeepSeek API 返回的 choices 为空");
    }

    private BookInsightResponse parseInsightContent(BookInsightContext context, String rawContent) throws Exception {
        if (rawContent == null || rawContent.isBlank()) {
            throw new IllegalStateException("DeepSeek API 返回的导读内容为空");
        }
        String cleanJson = rawContent.trim();
        if (cleanJson.startsWith("```json")) {
            cleanJson = cleanJson.substring(7);
        } else if (cleanJson.startsWith("```")) {
            cleanJson = cleanJson.substring(3);
        }
        if (cleanJson.endsWith("```")) {
            cleanJson = cleanJson.substring(0, cleanJson.length() - 3);
        }
        cleanJson = cleanJson.trim();

        JsonNode insightNode = objectMapper.readTree(cleanJson);
        String summary = insightNode.path("summary").asText(null);
        String targetReader = insightNode.path("targetReader").asText(null);
        String readingGuide = insightNode.path("readingGuide").asText(null);

        List<String> keyTopics = new ArrayList<>();
        JsonNode topicsNode = insightNode.path("keyTopics");
        if (topicsNode.isArray()) {
            for (JsonNode topic : topicsNode) {
                if (topic.isTextual() && !topic.asText().isBlank()) {
                    keyTopics.add(topic.asText().trim());
                }
            }
        }
        if (keyTopics.isEmpty()) {
            keyTopics.add("专业研读");
            keyTopics.add("通识拓展");
        }

        return BookInsightResponse.builder()
                .bookId(context.bookId())
                .bookTitle(context.title())
                .summary(summary != null && !summary.isBlank() ? summary : "《" + context.title() + "》深度导读已生成。")
                .keyTopics(keyTopics)
                .targetReader(targetReader != null && !targetReader.isBlank() ? targetReader : "全校师生读者")
                .readingGuide(readingGuide != null && !readingGuide.isBlank() ? readingGuide : "建议结合全书章节脉络进行泛读与精读。")
                .modelName("deepseek-chat")
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    @Override
    public String generateRecommendationReason(BookInsightContext context, String reasonContext) {
        if (!isRealApiKeyConfigured()) {
            return fallbackProvider.generateRecommendationReason(context, reasonContext);
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", resolvedModel());
            String systemPrompt = "你是一位图书馆资深导读馆员，请用一句话（30字以内）为读者推荐以下图书。不要包含多余废话。";
            String userPrompt = String.format("书名：《%s》，著者：%s，背景偏好：%s",
                    context.title(),
                    context.authorOrDefault(),
                    reasonContext != null ? reasonContext : "经典研读");

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            );
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 60);

            String responseJson = restClient().post()
                    .uri("/chat/completions")
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            JsonNode rootNode = objectMapper.readTree(responseJson);
            JsonNode choicesNode = rootNode.path("choices");
            if (choicesNode.isArray() && !choicesNode.isEmpty()) {
                String reason = choicesNode.get(0).path("message").path("content").asText().trim();
                if (!reason.isBlank()) {
                    return reason;
                }
            }
        } catch (Exception e) {
            log.debug("DeepSeek 远程推荐理由生成异常或超时，平滑使用规则兜底: {}", e.getMessage());
        }
        return fallbackProvider.generateRecommendationReason(context, reasonContext);
    }

    @Override
    public String getProviderName() {
        return "deepseek-chat";
    }
}
