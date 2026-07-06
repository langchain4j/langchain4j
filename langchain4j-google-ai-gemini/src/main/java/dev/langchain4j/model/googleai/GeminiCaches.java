package dev.langchain4j.model.googleai;

import static dev.langchain4j.internal.Utils.isNotNullOrEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.model.googleai.PartsAndContentsMapper.fromMessageToGContent;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.http.client.HttpClientBuilder;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;

/**
 * Service for creating and managing Gemini
 * <a href="https://ai.google.dev/gemini-api/docs/caching">context caches</a>.
 *
 * <p>Context caching stores large, frequently reused context (a system instruction, long documents,
 * PDFs) on Google's servers once, so subsequent requests can reference it by name instead of
 * resending it, reducing input-token cost and latency.
 *
 * <p>The name of a created cache ({@link GeminiCachedContent#name()}) can be supplied as the
 * {@code cachedContentName} of a {@link GoogleAiGeminiChatModel} (or its streaming counterpart) to
 * consume the cached context.
 */
public final class GeminiCaches {

    private final GeminiService geminiService;

    private GeminiCaches(Builder builder) {
        this.geminiService = new GeminiService(
                builder.httpClientBuilder,
                builder.apiKey,
                builder.baseUrl,
                builder.logRequestsAndResponses,
                builder.logRequests,
                builder.logResponses,
                builder.logger,
                builder.timeout,
                null);
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a context cache for the given model from the supplied messages.
     *
     * @param modelName the model the cache is bound to (e.g. {@code "gemini-2.5-flash"})
     * @param messages  the messages to cache; a {@code SystemMessage} becomes the cached system
     *                  instruction, the rest become cached contents
     * @param ttl       optional time-to-live for the cache (e.g. {@code Duration.ofHours(1)}); may be
     *                  {@code null}, in which case the API default is used
     * @return the created cache, whose {@link GeminiCachedContent#name()} is used to consume it
     */
    public GeminiCachedContent createCache(String modelName, List<ChatMessage> messages, Duration ttl) {
        ensureNotBlank(modelName, "modelName");
        ensureNotEmpty(messages, "messages");
        return geminiService.createCachedContent(toCreateRequest(modelName, messages, ttl));
    }

    /**
     * Retrieves a cache by name (e.g. {@code "cachedContents/abc123"}).
     */
    public GeminiCachedContent getCache(String name) {
        ensureNotBlank(name, "name");
        return geminiService.getCachedContent(name);
    }

    /**
     * Lists all context caches for the current project.
     */
    public List<GeminiCachedContent> listCaches() {
        List<GeminiCachedContent> allCaches = new ArrayList<>();
        String pageToken = null;
        do {
            GeminiCachedContentsListResponse response = geminiService.listCachedContents(null, pageToken);
            if (response.cachedContents() != null) {
                allCaches.addAll(response.cachedContents());
            }
            pageToken = response.nextPageToken();
        } while (isNotNullOrEmpty(pageToken));
        return allCaches;
    }

    /**
     * Deletes a context cache by name.
     */
    public void deleteCache(String name) {
        ensureNotBlank(name, "name");
        geminiService.deleteCachedContent(name);
    }

    static GeminiCreateCachedContentRequest toCreateRequest(
            String modelName, List<ChatMessage> messages, Duration ttl) {
        GeminiContent systemInstruction = new GeminiContent(List.of(), GeminiRole.MODEL.toString());
        List<GeminiContent> contents = fromMessageToGContent(messages, systemInstruction, false);
        return new GeminiCreateCachedContentRequest(
                modelName.startsWith("models/") ? modelName : "models/" + modelName,
                contents.isEmpty() ? null : contents,
                !systemInstruction.parts().isEmpty() ? systemInstruction : null,
                ttl != null ? toTtlString(ttl) : null);
    }

    static String toTtlString(Duration ttl) {
        if (ttl.getNano() == 0) {
            return ttl.getSeconds() + "s";
        }
        return BigDecimal.valueOf(ttl.getSeconds())
                        .add(BigDecimal.valueOf(ttl.getNano(), 9))
                        .stripTrailingZeros()
                        .toPlainString()
                + "s";
    }

    /**
     * Represents a Gemini context cache,
     * <a href="https://ai.google.dev/api/caching">documentation</a>
     *
     * @param name          the resource name of the cache (e.g., "cachedContents/abc123")
     * @param model         the fully qualified name of the model the cache is bound to (e.g., "models/gemini-2.5-flash")
     * @param displayName   an optional display name for the cache
     * @param createTime    the timestamp indicating when the cache was created, formatted as an ISO-8601 string
     * @param updateTime    the timestamp indicating when the cache was last updated, formatted as an ISO-8601 string
     * @param expireTime    the timestamp indicating when the cache expires, formatted as an ISO-8601 string
     * @param usageMetadata the token usage of the cached content
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiCachedContent(
            @JsonProperty("name") String name,
            @JsonProperty("model") String model,
            @JsonProperty("displayName") String displayName,
            @JsonProperty("createTime") String createTime,
            @JsonProperty("updateTime") String updateTime,
            @JsonProperty("expireTime") String expireTime,
            @JsonProperty("usageMetadata") UsageMetadata usageMetadata) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record UsageMetadata(
                @JsonProperty("totalTokenCount") Integer totalTokenCount) {}
    }

    record GeminiCreateCachedContentRequest(
            @JsonProperty("model") String model,
            @JsonProperty("contents") List<GeminiContent> contents,
            @JsonProperty("systemInstruction") GeminiContent systemInstruction,
            @JsonProperty("ttl") String ttl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record GeminiCachedContentsListResponse(
            @JsonProperty("cachedContents") List<GeminiCachedContent> cachedContents,
            @JsonProperty("nextPageToken") String nextPageToken) {}

    public static class Builder {
        private HttpClientBuilder httpClientBuilder;
        private String baseUrl;
        private String apiKey;
        private boolean logRequestsAndResponses;
        private boolean logRequests;
        private boolean logResponses;
        private Logger logger;
        private Duration timeout;

        public Builder httpClientBuilder(HttpClientBuilder httpClientBuilder) {
            this.httpClientBuilder = httpClientBuilder;
            return this;
        }

        public Builder baseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
            return this;
        }

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder logRequestsAndResponses(boolean logRequestsAndResponses) {
            this.logRequestsAndResponses = logRequestsAndResponses;
            return this;
        }

        public Builder logRequests(boolean logRequests) {
            this.logRequests = logRequests;
            return this;
        }

        public Builder logResponses(boolean logResponses) {
            this.logResponses = logResponses;
            return this;
        }

        public Builder logger(Logger logger) {
            this.logger = logger;
            return this;
        }

        public Builder timeout(Duration timeout) {
            this.timeout = timeout;
            return this;
        }

        public GeminiCaches build() {
            return new GeminiCaches(this);
        }
    }
}
