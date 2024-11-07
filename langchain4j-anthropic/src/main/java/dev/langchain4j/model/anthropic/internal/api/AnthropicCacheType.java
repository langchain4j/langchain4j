package dev.langchain4j.model.anthropic.internal.api;

import java.util.function.Supplier;

public enum AnthropicCacheType {
    NO_CACHE(() -> new CacheControl("no_cache")),
    EPHEMERAL(() -> new CacheControl("ephemeral"));

    private final Supplier<CacheControl> value;

    AnthropicCacheType(Supplier<CacheControl> value) {
        this.value = value;
    }

    public CacheControl cacheControl() {
        return this.value.get();
    }
}
