package dev.langchain4j.model.google.genai;

import static dev.langchain4j.internal.ValidationUtils.ensureNotBlank;
import static dev.langchain4j.internal.ValidationUtils.ensureNotEmpty;
import static dev.langchain4j.internal.ValidationUtils.ensureNotNull;

import com.google.genai.Client;
import com.google.genai.types.CachedContent;
import com.google.genai.types.Content;
import com.google.genai.types.CreateCachedContentConfig;
import com.google.genai.types.DeleteCachedContentConfig;
import com.google.genai.types.GetCachedContentConfig;
import com.google.genai.types.ListCachedContentsConfig;
import com.google.genai.types.UpdateCachedContentConfig;
import dev.langchain4j.data.message.ChatMessage;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for creating and managing Gemini
 * <a href="https://ai.google.dev/gemini-api/docs/caching">context caches</a> using the official
 * com.google.genai SDK.
 *
 * <p>Context caching stores large, frequently reused context (a system instruction, long documents,
 * PDFs) on Google's servers once, so subsequent requests can reference it by name instead of
 * resending it, reducing input-token cost and latency.
 *
 * <p>The name of a created cache ({@link CachedContent#name()}) can be supplied as the
 * {@code cachedContent} of a {@link GoogleGenAiChatModel} (or its streaming/batch counterparts) to
 * consume the cached context.
 */
public final class GoogleGenAiCaches {

    private final Client client;

    private GoogleGenAiCaches(Builder builder) {
        this.client = builder.client != null
                ? builder.client
                : GoogleGenAiClientFactory.createClient(
                        builder.apiKey, null, null, null, null, builder.customHeaders, builder.apiEndpoint);
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
     * @return the created cache, whose {@link CachedContent#name()} is used to consume it
     */
    public CachedContent createCache(String modelName, List<ChatMessage> messages, Duration ttl) {
        ensureNotBlank(modelName, "modelName");
        ensureNotEmpty(messages, "messages");
        return client.caches.create(modelName, toCreateConfig(messages, ttl));
    }

    /** Retrieves a cache by name (e.g. {@code "cachedContents/abc123"}). */
    public CachedContent getCache(String name) {
        ensureNotBlank(name, "name");
        return client.caches.get(name, GetCachedContentConfig.builder().build());
    }

    /** Lists all context caches for the current project. */
    public List<CachedContent> listCaches() {
        List<CachedContent> caches = new ArrayList<>();
        client.caches.list(ListCachedContentsConfig.builder().build()).forEach(caches::add);
        return caches;
    }

    /** Updates a cache's time-to-live and returns the updated cache. */
    public CachedContent updateCacheTtl(String name, Duration ttl) {
        ensureNotBlank(name, "name");
        ensureNotNull(ttl, "ttl");
        return client.caches.update(
                name, UpdateCachedContentConfig.builder().ttl(ttl).build());
    }

    /** Deletes a context cache by name. */
    public void deleteCache(String name) {
        ensureNotBlank(name, "name");
        client.caches.delete(name, DeleteCachedContentConfig.builder().build());
    }

    static CreateCachedContentConfig toCreateConfig(List<ChatMessage> messages, Duration ttl) {
        List<Content> contents = GoogleGenAiContentMapper.toContents(messages);
        Content systemInstruction = GoogleGenAiContentMapper.toSystemInstruction(messages);

        CreateCachedContentConfig.Builder config = CreateCachedContentConfig.builder();
        if (!contents.isEmpty()) {
            config.contents(contents);
        }
        if (systemInstruction != null) {
            config.systemInstruction(systemInstruction);
        }
        if (ttl != null) {
            config.ttl(ttl);
        }
        return config.build();
    }

    public static class Builder {
        private String apiKey;
        private String apiEndpoint;
        private Map<String, String> customHeaders;
        private Client client;

        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey;
            return this;
        }

        public Builder apiEndpoint(String apiEndpoint) {
            this.apiEndpoint = apiEndpoint;
            return this;
        }

        public Builder customHeaders(Map<String, String> customHeaders) {
            this.customHeaders = customHeaders;
            return this;
        }

        public Builder client(Client client) {
            this.client = client;
            return this;
        }

        public GoogleGenAiCaches build() {
            return new GoogleGenAiCaches(this);
        }
    }
}
