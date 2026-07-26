package dev.langchain4j.model.anthropic.internal.api;

import java.util.function.Supplier;

public enum AnthropicCacheType {

    NO_CACHE("no_cache"),
    EPHEMERAL("ephemeral");

    private final String type;

    AnthropicCacheType(String type) {
        this.type = type;
    }

    public AnthropicCacheControl cacheControl() {
        return new AnthropicCacheControl(this.type);
    }

    public AnthropicCacheControl cacheControl(java.time.Duration cacheTtl) {
        if (this == NO_CACHE || cacheTtl == null) {
            return cacheControl();
        }
        long minutes = cacheTtl.toMinutes();
        String ttlStr = (minutes >= 60 && minutes % 60 == 0) ? (minutes / 60) + "h" : minutes + "m";
        return new AnthropicCacheControl(this.type, ttlStr);
    }
}
