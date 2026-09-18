package com.library.service.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.domain.entity.Book;
import com.library.dto.ai.BookInsightResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek AI 大模型智能提供者 (Stage 5 & 9-D)
 * 支持真实 HTTP 远程调用与离线 Mock 双模平滑切换：
 * 1. 依托 Spring Boot 3 内置 RestClient (配置 5 秒严格超时保护)；
 * 2. 当配置了有效密钥 (非空且非 mock) 时，真正向 DeepSeek Chat Completions 发送结构化 Prompt 请求；
 * 3. 当外部调用超时、限流 (429) 或未配置密钥时，自动捕获并无缝平滑降级至 RuleBasedMockAiProvider 本地规则引擎；
 * 4. 彻底杜绝假代码，确保在线可演示真大模型，离线 100% 弹性高可用。
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

    private final RuleBasedMockAiProvider fallbackProvider;
    private final ObjectMapper objectMapper;
    private RestClient restClient;

    @Autowired
    public DeepSeekAiProvider(
            @Qualifier("ruleBasedMockAiProvider") RuleBasedMockAiProvider fallbackProvider,
            ObjectMapper objectMapper) {
        this.fallbackProvider = fallbackProvider;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    // 单元测试与灵活配置辅助方法
    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
        this.restClient = null; // 重置 client 以便按新 key 初始化
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

    private synchronized RestClient getRestClient() {
        if (this.restClient == null) {
            this.restClient = buildRestClient();
        }
        return this.restClient;
    }

    private RestClient buildRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(5000);
        requestFactory.setReadTimeout(5000);

        String trimmedBaseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.trim() : "https://api.deepseek.com/v1";
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

    @Override
    public BookInsightResponse generateInsight(Book book) {
        if (!isRealApiKeyConfigured()) {
            log.debug("未配置有效 DEEPSEEK_API_KEY，自动平滑使用本地智能生成器");
            BookInsightResponse response = fallbackProvider.generateInsight(book);
            response.setModelName("deepseek-chat (local-fallback)");
            return response;
        }

        try {
            log.info("发起 DeepSeek AI 远程调用生成图书《{}》的智能导读 (BaseUrl: {})", book.getTitle(), baseUrl);
            return callDeepSeekApi(book);
        } catch (Exception e) {
            log.warn("DeepSeek API 远程调用异常或超时 (5s)，平滑降级至本地规则引擎: {}", e.getMessage());
            BookInsightResponse fallback = fallbackProvider.generateInsight(book);
            fallback.setModelName("deepseek-chat (fallback)");
            return fallback;
        }
    }

    private BookInsightResponse callDeepSeekApi(Book book) throws Exception {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("model", modelName != null && !modelName.isBlank() ? modelName : "deepseek-chat");

        String systemPrompt = "你是一位资深的大学图书馆学学者与专业书评专家。"
                + "请深入分析提供的图书信息，并严格输出一个合法的 JSON 对象，不要包含任何 Markdown 代码块标签（绝对不要输出 ```json 或 ``` 等代码块格式），直接输出纯 JSON 字符串。"
                + "JSON 必须包含且仅包含以下四个字段："
                + "1. \"summary\": 字符串，对本书核心主旨与学术/实践价值的高度提炼（120-200字）；"
                + "2. \"keyTopics\": 字符串数组，提炼3-5个最核心的专业技术或学科概念关键词；"
                + "3. \"targetReader\": 字符串，精准画像最适宜阅读借阅的人群；"
                + "4. \"readingGuide\": 字符串，具体的先修知识要求与推荐章节精读建议。";

        String userPrompt = String.format("书名：《%s》\n著者：%s\n分类：%s\n简介：%s",
                book.getTitle(),
                book.getAuthor() != null ? book.getAuthor() : "未知",
                book.getCategory() != null ? book.getCategory().getName() : "综合分类",
                book.getDescription() != null ? book.getDescription() : "暂无简介");

        List<Map<String, String>> messages = List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        );
        requestBody.put("messages", messages);
        requestBody.put("temperature", 0.7);
        requestBody.put("max_tokens", 1024);

        String responseJson = getRestClient().post()
                .uri("/chat/completions")
                .body(requestBody)
                .retrieve()
                .body(String.class);

        JsonNode rootNode = objectMapper.readTree(responseJson);
        JsonNode choicesNode = rootNode.path("choices");
        if (choicesNode.isArray() && !choicesNode.isEmpty()) {
            String content = choicesNode.get(0).path("message").path("content").asText();
            return parseInsightContent(book, content);
        }

        throw new IllegalStateException("DeepSeek API 返回的 choices 为空");
    }

    private BookInsightResponse parseInsightContent(Book book, String rawContent) throws Exception {
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
                .bookId(book.getId())
                .bookTitle(book.getTitle())
                .summary(summary != null && !summary.isBlank() ? summary : "《" + book.getTitle() + "》深度导读已生成。")
                .keyTopics(keyTopics)
                .targetReader(targetReader != null && !targetReader.isBlank() ? targetReader : "全校师生读者")
                .readingGuide(readingGuide != null && !readingGuide.isBlank() ? readingGuide : "建议结合全书章节脉络进行泛读与精读。")
                .modelName("deepseek-chat")
                .generatedAt(OffsetDateTime.now())
                .build();
    }

    @Override
    public String generateRecommendationReason(Book book, String reasonContext) {
        if (!isRealApiKeyConfigured()) {
            return fallbackProvider.generateRecommendationReason(book, reasonContext);
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", modelName != null && !modelName.isBlank() ? modelName : "deepseek-chat");
            String systemPrompt = "你是一位图书馆资深导读馆员，请用一句话（30字以内）为读者推荐以下图书。不要包含多余废话。";
            String userPrompt = String.format("书名：《%s》，著者：%s，背景偏好：%s",
                    book.getTitle(),
                    book.getAuthor() != null ? book.getAuthor() : "名家",
                    reasonContext != null ? reasonContext : "经典研读");

            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", userPrompt)
            );
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7);
            requestBody.put("max_tokens", 60);

            String responseJson = getRestClient().post()
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
        return fallbackProvider.generateRecommendationReason(book, reasonContext);
    }

    @Override
    public String getProviderName() {
        return "deepseek-chat";
    }
}
