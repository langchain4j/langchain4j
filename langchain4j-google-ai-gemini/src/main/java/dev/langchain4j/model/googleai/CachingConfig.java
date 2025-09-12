package dev.langchain4j.model.googleai;

import java.time.Duration;

public class CachingConfig {

    private boolean cacheSystemMessages;
    private String cacheKey;
    private Duration ttl;

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

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private boolean cacheSystemMessages;
        private String cacheKey;
        private Duration ttl;

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

        public CachingConfig build() {
            CachingConfig config = new CachingConfig();
            config.setCacheSystemMessages(cacheSystemMessages);
            config.setCacheKey(cacheKey);
            config.setTtl(ttl);
            return config;
        }

    }

}
