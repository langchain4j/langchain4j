package dev.langchain4j.model.anthropic.internal.api;

import dev.langchain4j.model.anthropic.internal.api.AnthropicMessageContent.CacheControl;

import java.util.function.Supplier;

public enum AnthropicCacheType {
    EPHEMERAL(() -> new CacheControl("ephemeral"));

    private Supplier<CacheControl> value;

    AnthropicCacheType(Supplier<CacheControl> value) {
        this.value = value;
    }

    public CacheControl cacheControl() {
        return this.value.get();
    }
}
