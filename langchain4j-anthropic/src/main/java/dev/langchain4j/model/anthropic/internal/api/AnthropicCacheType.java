package dev.langchain4j.model.anthropic.internal.api;

import java.util.function.Supplier;

public enum AnthropicCacheType {

    NO_CACHE(() -> new AnthropicCacheControl("no_cache")),
    EPHEMERAL(() -> new AnthropicCacheControl("ephemeral"));

    private final Supplier<AnthropicCacheControl> value;

    AnthropicCacheType(Supplier<AnthropicCacheControl> value) {
        this.value = value;
    }

    public AnthropicCacheControl cacheControl() {
        return this.value.get();
    }

    public AnthropicCacheControl cacheControl(String ttl) {
        return ttl == null ? cacheControl() : new AnthropicCacheControl(cacheControl().getType(), ttl);
    }
}
