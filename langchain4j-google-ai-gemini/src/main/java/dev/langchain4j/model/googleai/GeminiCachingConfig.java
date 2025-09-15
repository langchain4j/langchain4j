package dev.langchain4j.model.googleai;

import java.time.Duration;
import java.util.function.Function;

public class GeminiCachingConfig {

    private boolean cacheSystemMessages;
    private String cacheKey;
    private Duration ttl;
    private Function<GeminiService, GeminiCacheManager> cacheManagerProvider;

    public boolean isCacheSystemMessages() {
        return cacheSystemMessages;
    }

    public void setCacheSystemMessages(final boolean cacheSystemMessages) {
        this.cacheSystemMessages = cacheSystemMessages;
    }

    public String getCacheKey() {
        return cacheKey;
    }

    public void setCacheKey(final String cacheKey) {
        this.cacheKey = cacheKey;
    }

    public Duration getTtl() {
        return ttl;
    }

    public void setTtl(final Duration ttl) {
        this.ttl = ttl;
    }

    public Function<GeminiService, GeminiCacheManager> getCacheManagerProvider() {
        return cacheManagerProvider;
    }

    public void setCacheManagerProvider(final Function<GeminiService, GeminiCacheManager> cacheManagerProvider) {
        this.cacheManagerProvider = cacheManagerProvider;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean cacheSystemMessages;
        private String cacheKey;
        private Duration ttl;
        private Function<GeminiService, GeminiCacheManager> cacheManagerProvider;

        public Builder cacheSystemMessages(boolean cacheSystemMessages) {
            this.cacheSystemMessages = cacheSystemMessages;
            return this;
        }

        public Builder cacheKey(String cacheKey) {
            this.cacheKey = cacheKey;
            return this;
        }

        public Builder ttl(Duration ttl) {
            this.ttl = ttl;
            return this;
        }

        public Builder cacheManagerProvider(Function<GeminiService, GeminiCacheManager> cacheManagerProvider) {
            this.cacheManagerProvider = cacheManagerProvider;
            return this;
        }

        public GeminiCachingConfig build() {
            GeminiCachingConfig config = new GeminiCachingConfig();
            config.setCacheSystemMessages(cacheSystemMessages);
            config.setCacheKey(cacheKey);
            config.setTtl(ttl);
            config.setCacheManagerProvider(cacheManagerProvider);
            return config;
        }

    }

}
